package com.realtimetv.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.text.Normalizer

enum class Tab(val label: String) { HOME("Início"), SEARCH("Buscar"), LIVE("TV"), GUIDE("Guia"), MOVIES("Filmes"), SERIES("Séries"), RADIO("Rádio"), SETTINGS("Ajustes") }

data class VodItem(
    val key: String, val type: String, val id: Int, val title: String, val url: String, val image: String, val ext: String,
    val seriesId: Int = 0, val season: Int = 0, val ep: Int = 0,
)

sealed class PlayReq {
    class Live(val list: List<Channel>, val index: Int, val audio: Boolean = false) : PlayReq()
    class Vod(val items: List<VodItem>, val index: Int) : PlayReq()
}

sealed class Overlay {
    class MovieDet(val movie: Movie) : Overlay()
    class SeriesDet(val series: Series) : Overlay()
    class Player(val req: PlayReq) : Overlay()
}

fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

class AppVM(app: Application) : AndroidViewModel(app) {
    val store = Store(app)

    var booting by mutableStateOf(true)
    var busy by mutableStateOf(false)
    var loginError by mutableStateOf("")
    var account by mutableStateOf<Account?>(null)
    var profile by mutableStateOf<Profile?>(null)
    var profiles by mutableStateOf<List<Profile>>(emptyList())

    var tab by mutableStateOf(Tab.HOME)
    val overlays = mutableStateListOf<Overlay>()

    // dados
    var liveCats by mutableStateOf<List<Category>>(emptyList())
    var vodCats by mutableStateOf<List<Category>>(emptyList())
    var seriesCats by mutableStateOf<List<Category>>(emptyList())
    var live by mutableStateOf<List<Channel>>(emptyList())
    var radios by mutableStateOf<List<Channel>>(emptyList())
    var dataLoading by mutableStateOf(false)
    var dataError by mutableStateOf("")
    var recentMovies by mutableStateOf<List<Movie>>(emptyList())
    var recentSeries by mutableStateOf<List<Series>>(emptyList())
    var allMovies by mutableStateOf<List<Movie>>(emptyList())
    var allSeries by mutableStateOf<List<Series>>(emptyList())

    val favs = mutableStateListOf<String>()
    var progressList by mutableStateOf<List<Progress>>(emptyList())
    val unlocked = mutableStateListOf<String>()
    var pinPrompt by mutableStateOf<(() -> Unit)?>(null)

    private val movieCache = HashMap<String, List<Movie>>()
    private val seriesCache = HashMap<String, List<Series>>()
    private val seriesInfoCache = HashMap<Int, SeriesDetail>()
    private val movieInfoCache = HashMap<Int, MovieDetail>()
    private val epgCache = HashMap<Int, Pair<Long, List<EpgItem>>>()

    // seleção que sobrevive à troca de abas
    var liveCat by mutableStateOf("all")
    var guideCat by mutableStateOf("all")
    var movieCat by mutableStateOf("all")
    var seriesCat by mutableStateOf("all")

    init {
        val u = store.user
        val p = store.pass
        if (u.isNotEmpty() && p.isNotEmpty()) login(u, p, true) else booting = false
    }

    fun login(u: String, p: String, silent: Boolean = false) {
        viewModelScope.launch {
            busy = true
            loginError = ""
            try {
                val acc = Api.login(u.trim(), p.trim())
                store.user = u.trim(); store.pass = p.trim()
                account = acc
                ensureProfiles(acc)
                loadCore()
            } catch (e: AuthException) {
                account = null
                store.pass = ""
                loginError = e.message ?: "Falha no login"
            } catch (e: Exception) {
                account = null
                loginError = "Não foi possível conectar ao servidor. Verifique sua internet."
            }
            busy = false
            booting = false
        }
    }

    fun logout() {
        store.clearLogin()
        account = null; profile = null
        live = emptyList(); radios = emptyList(); liveCats = emptyList(); vodCats = emptyList(); seriesCats = emptyList()
        movieCache.clear(); seriesCache.clear(); recentMovies = emptyList(); recentSeries = emptyList(); allMovies = emptyList(); allSeries = emptyList()
        overlays.clear(); tab = Tab.HOME
    }

    private fun ensureProfiles(acc: Account) {
        var list = store.profiles()
        if (list.isEmpty()) {
            list = listOf(Profile("p1", acc.username.replaceFirstChar { it.uppercase() }, 0xFF00E5FF, false))
            store.saveProfiles(list)
        }
        profiles = list
        if (list.size == 1) selectProfile(list[0])
    }

    fun selectProfile(p: Profile) {
        profile = p
        store.lastProfile = p.id
        favs.clear(); favs.addAll(store.favs(p.id))
        progressList = store.progress(p.id)
        unlocked.clear()
        tab = Tab.HOME
    }

    fun switchProfile() { profile = null; overlays.clear() }

    fun addProfile(name: String, kids: Boolean) {
        val colors = listOf(0xFF00E5FF, 0xFF8B5CFF, 0xFFFF3DCB, 0xFF2BFF9A, 0xFFFFB020, 0xFF4D7CFF)
        val id = "p" + System.currentTimeMillis()
        val list = profiles + Profile(id, name.trim().ifEmpty { "Perfil" }, colors[profiles.size % colors.size], kids)
        store.saveProfiles(list); profiles = list
    }

    fun deleteProfile(p: Profile) {
        if (profiles.size <= 1) return
        val list = profiles.filter { it.id != p.id }
        store.saveProfiles(list); profiles = list
    }

    // ---------- carregamento ----------
    private fun isRadioCat(name: String): Boolean = norm(name).contains("radio")

    private fun isAdultCat(name: String): Boolean {
        val n = norm(name)
        return n.contains("adult") || n.contains("xxx") || n.contains("+18") || n.contains("18+") || n.contains("erotic") || n.contains("porn") || n.contains("hentai")
    }

    suspend fun loadCore() {
        dataLoading = true
        dataError = ""
        try {
            val a = viewModelScope.async { Api.liveCategories() }
            val b = viewModelScope.async { Api.liveChannels() }
            val c = viewModelScope.async { try { Api.vodCategories() } catch (e: Exception) { emptyList() } }
            val d = viewModelScope.async { try { Api.seriesCategories() } catch (e: Exception) { emptyList() } }
            val cats = a.await()
            val chans = b.await()
            vodCats = c.await()
            seriesCats = d.await()
            val radioIds = cats.filter { isRadioCat(it.name) }.map { it.id }.toSet()
            liveCats = cats
            val liveOnly = chans.filter { it.catId !in radioIds }
            val radioOnly = chans.filter { it.catId in radioIds }
            live = liveOnly.mapIndexed { i, ch -> ch.copy(num = i + 1) }
            radios = radioOnly.mapIndexed { i, ch -> ch.copy(num = i + 1, radio = true) }
            if (!store.lockInit) {
                val keys = HashSet<String>()
                for (x in cats) if (isAdultCat(x.name)) keys.add("live:" + x.id)
                for (x in vodCats) if (isAdultCat(x.name)) keys.add("movie:" + x.id)
                for (x in seriesCats) if (isAdultCat(x.name)) keys.add("series:" + x.id)
                store.setLockedAll(store.lockedSet() + keys)
                store.lockInit = true
            }
        } catch (e: Exception) {
            dataError = "Não foi possível carregar a lista. Toque para tentar novamente."
        }
        dataLoading = false
    }

    fun reload() { viewModelScope.launch { loadCore() } }

    fun loadHomeExtras() {
        if (recentMovies.isNotEmpty() || recentSeries.isNotEmpty()) return
        viewModelScope.launch {
            try { recentMovies = Api.vodStreams(null).sortedByDescending { it.added }.take(30) } catch (e: Exception) { }
            try { recentSeries = Api.seriesList(null).sortedByDescending { it.added }.take(30) } catch (e: Exception) { }
        }
    }

    suspend fun movies(cat: String): List<Movie> {
        movieCache[cat]?.let { return it }
        val l = Api.vodStreams(if (cat == "all") null else cat)
        val r = if (cat == "all") l.sortedByDescending { it.added } else l
        movieCache[cat] = r
        return r
    }

    suspend fun seriesIn(cat: String): List<Series> {
        seriesCache[cat]?.let { return it }
        val l = Api.seriesList(if (cat == "all") null else cat)
        val r = if (cat == "all") l.sortedByDescending { it.added } else l
        seriesCache[cat] = r
        return r
    }

    suspend fun ensureAllVod() {
        if (allMovies.isEmpty()) allMovies = try { Api.vodStreams(null) } catch (e: Exception) { emptyList() }
        if (allSeries.isEmpty()) allSeries = try { Api.seriesList(null) } catch (e: Exception) { emptyList() }
    }

    suspend fun movieDetail(id: Int): MovieDetail {
        movieInfoCache[id]?.let { return it }
        val d = Api.movieInfo(id)
        movieInfoCache[id] = d
        return d
    }

    suspend fun seriesDetail(id: Int): SeriesDetail {
        seriesInfoCache[id]?.let { return it }
        val d = Api.seriesInfo(id)
        seriesInfoCache[id] = d
        return d
    }

    suspend fun epgFor(id: Int, limit: Int): List<EpgItem> {
        val now = System.currentTimeMillis() / 1000
        val c = epgCache[id]
        if (c != null && now - c.first < 300 && c.second.isNotEmpty()) return c.second
        val l = try { Api.shortEpg(id, limit) } catch (e: Exception) { emptyList() }
        epgCache[id] = Pair(now, l)
        return l
    }

    // ---------- favoritos / progresso ----------
    fun isFav(key: String) = favs.contains(key)

    fun toggleFav(key: String) {
        val p = profile ?: return
        if (favs.contains(key)) favs.remove(key) else favs.add(key)
        store.saveFavs(p.id, favs.toList())
    }

    fun progressFor(key: String): Progress? = progressList.firstOrNull { it.key == key }

    fun saveProgress(p: Progress) {
        val pr = profile ?: return
        store.saveProgress(pr.id, p)
        progressList = store.progress(pr.id)
    }

    fun removeProgress(key: String) {
        val pr = profile ?: return
        store.removeProgress(pr.id, key)
        progressList = store.progress(pr.id)
    }

    fun clearHistory() {
        val pr = profile ?: return
        store.clearProgress(pr.id)
        progressList = emptyList()
    }

    // ---------- controle dos pais ----------
    val kids: Boolean get() = profile?.kids == true

    fun isLockedCat(type: String, id: String): Boolean = store.lockedSet().contains("$type:$id")

    /** categoria oculta no momento (bloqueada e ainda não liberada com PIN; perfil infantil nunca libera) */
    fun hiddenCat(type: String, id: String): Boolean = isLockedCat(type, id) && (kids || !unlocked.contains("$type:$id"))

    fun visibleCats(type: String, cats: List<Category>): List<Category> = if (kids) cats.filter { !isLockedCat(type, it.id) } else cats

    fun requestUnlock(type: String, id: String, then: () -> Unit) {
        if (!isLockedCat(type, id) || unlocked.contains("$type:$id")) { then(); return }
        if (kids) return
        pinPrompt = { unlocked.add("$type:$id"); then() }
    }

    fun requestPin(then: () -> Unit) { pinPrompt = then }

    // ---------- navegação ----------
    fun push(o: Overlay) { overlays.add(o) }
    fun closeTop() { if (overlays.isNotEmpty()) overlays.removeAt(overlays.size - 1) }

    fun playLive(list: List<Channel>, index: Int, audio: Boolean = false) { overlays.add(Overlay.Player(PlayReq.Live(list, index, audio))) }

    fun playMovie(m: Movie) {
        val vi = VodItem("movie:${m.id}", "movie", m.id, m.name, Api.movieUrl(m.id, m.ext), m.poster, m.ext)
        overlays.add(Overlay.Player(PlayReq.Vod(listOf(vi), 0)))
    }

    fun playEpisodes(s: Series, eps: List<Episode>, index: Int) {
        val items = eps.map { e ->
            VodItem("ep:${e.id}", "episode", e.id, "${s.name} · T${e.season}E${e.num} ${e.title}".trim(), Api.episodeUrl(e.id, e.ext), e.image.ifEmpty { s.cover }, e.ext, s.id, e.season, e.num)
        }
        overlays.add(Overlay.Player(PlayReq.Vod(items, index)))
    }

    fun resume(p: Progress) {
        when (p.type) {
            "movie" -> overlays.add(Overlay.Player(PlayReq.Vod(listOf(VodItem(p.key, "movie", p.id, p.title, Api.movieUrl(p.id, p.ext), p.image, p.ext)), 0)))
            "episode" -> overlays.add(Overlay.Player(PlayReq.Vod(listOf(VodItem(p.key, "episode", p.id, p.title, Api.episodeUrl(p.id, p.ext), p.image, p.ext, p.seriesId, p.season, p.ep)), 0)))
        }
    }
}
