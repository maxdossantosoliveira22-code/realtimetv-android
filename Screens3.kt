package com.realtimetv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
private fun Backdrop(url: String) {
    Box(Modifier.fillMaxSize().background(Neon.bgBrush))
    if (url.isNotEmpty()) AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.30f, modifier = Modifier.fillMaxSize())
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x9904060F), Color(0xF204060F)))))
}

@Composable
private fun MetaLine(vararg parts: String) {
    val txt = parts.filter { it.isNotBlank() }.joinToString("  •  ")
    if (txt.isNotEmpty()) T(txt, 13.sp, color = Neon.muted)
}

// ======================= DETALHE DO FILME =======================
@Composable
fun MovieDetailScreen(vm: AppVM, movie: Movie) {
    var det by remember { mutableStateOf<MovieDetail?>(null) }
    LaunchedEffect(movie.id) { det = try { vm.movieDetail(movie.id) } catch (e: Exception) { null } }
    val prog = vm.progressFor("movie:${movie.id}")
    val fav = vm.isFav("movie:${movie.id}")
    Box(Modifier.fillMaxSize()) {
        Backdrop(det?.backdrop?.ifEmpty { movie.poster } ?: movie.poster)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Close, size = 42.dp) { vm.closeTop() }
                    Spacer(Modifier.width(12.dp))
                    Wordmark(16.sp)
                }
                Spacer(Modifier.height(18.dp))
                val info: @Composable () -> Unit = {
                    Column {
                        T(movie.name, if (wide) 32.sp else 24.sp, FontWeight.Black)
                        Spacer(Modifier.height(6.dp))
                        val d = det
                        MetaLine(if (movie.rating.isNotBlank() && movie.rating != "0") "★ ${movie.rating}" else "", d?.year ?: "", d?.genre ?: "", d?.duration ?: "")
                        Spacer(Modifier.height(14.dp))
                        Row {
                            if (prog != null) NeonButton("Continuar " + fmtTime(prog.pos), Icons.Rounded.PlayArrow) { vm.playMovie(movie) }
                            else NeonButton("Assistir", Icons.Rounded.PlayArrow) { vm.playMovie(movie) }
                            Spacer(Modifier.width(10.dp))
                            NeonButton(if (fav) "Favorito" else "Favoritar", if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, primary = false) { vm.toggleFav("movie:${movie.id}") }
                        }
                        Spacer(Modifier.height(16.dp))
                        val plot = d?.plot ?: ""
                        if (plot.isNotBlank()) T(plot, 14.sp, color = Color(0xFFCBD7F0))
                        if (!d?.cast.isNullOrBlank()) { Spacer(Modifier.height(12.dp)); T("Elenco: " + d?.cast, 12.sp, color = Neon.muted) }
                        if (!d?.director.isNullOrBlank()) { Spacer(Modifier.height(4.dp)); T("Direção: " + d?.director, 12.sp, color = Neon.muted) }
                    }
                }
                if (wide) {
                    Row {
                        PosterCard(movie.name, movie.poster, Modifier.width(190.dp).aspectRatio(0.68f)) { vm.playMovie(movie) }
                        Spacer(Modifier.width(24.dp))
                        Box(Modifier.weight(1f)) { info() }
                    }
                } else {
                    PosterCard(movie.name, movie.poster, Modifier.width(150.dp).aspectRatio(0.68f)) { vm.playMovie(movie) }
                    Spacer(Modifier.height(16.dp))
                    info()
                }
            }
        }
    }
}

// ======================= DETALHE DA SÉRIE =======================
@Composable
fun SeriesDetailScreen(vm: AppVM, series: Series) {
    var det by remember { mutableStateOf<SeriesDetail?>(null) }
    var err by remember { mutableStateOf("") }
    var season by remember { mutableStateOf(-1) }
    LaunchedEffect(series.id) {
        try {
            val d = vm.seriesDetail(series.id)
            det = d
            if (season < 0) season = d.seasons.firstOrNull() ?: 1
        } catch (e: Exception) { err = "Não foi possível carregar a série." }
    }
    val d = det
    val fav = vm.isFav("series:${series.id}")
    val flat: List<Episode> = if (d != null) d.seasons.flatMap { sn -> d.episodes[sn] ?: emptyList() } else emptyList()
    val eps: List<Episode> = if (d != null) (d.episodes[season] ?: emptyList()) else emptyList()
    Box(Modifier.fillMaxSize()) {
        Backdrop(d?.backdrop?.ifEmpty { series.backdrop }?.ifEmpty { series.cover } ?: series.cover)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBtn(Icons.Rounded.Close, size = 42.dp) { vm.closeTop() }
                    Spacer(Modifier.width(12.dp))
                    Wordmark(16.sp)
                }
                Spacer(Modifier.height(16.dp))
                T(series.name, 28.sp, FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                MetaLine(if (series.rating.isNotBlank() && series.rating != "0") "★ ${series.rating}" else "", series.year, series.genre, if (d != null) "${d.seasons.size} temporada(s)" else "")
                Spacer(Modifier.height(12.dp))
                Row {
                    val next = flat.firstOrNull { e -> vm.progressFor("ep:${e.id}") != null } ?: flat.firstOrNull()
                    if (next != null) NeonButton(if (vm.progressFor("ep:${next.id}") != null) "Continuar T${next.season}E${next.num}" else "Assistir T${next.season}E${next.num}", Icons.Rounded.PlayArrow) {
                        vm.playEpisodes(series, flat, flat.indexOfFirst { it.id == next.id })
                    }
                    Spacer(Modifier.width(10.dp))
                    NeonButton(if (fav) "Favorita" else "Favoritar", if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, primary = false) { vm.toggleFav("series:${series.id}") }
                }
                if (series.plot.isNotBlank()) { Spacer(Modifier.height(14.dp)); T(series.plot, 14.sp, color = Color(0xFFCBD7F0)) }
                Spacer(Modifier.height(12.dp))
                if (d != null && d.seasons.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(d.seasons) { sn -> Chip("Temporada $sn", season == sn) { season = sn } }
                    }
                }
                if (err.isNotEmpty()) T(err, 14.sp, color = Neon.bad)
                if (d == null && err.isEmpty()) T("Carregando episódios…", 14.sp, color = Neon.muted)
            }
            items(eps, key = { it.id }) { e ->
                val p = vm.progressFor("ep:${e.id}")
                val shape = RoundedCornerShape(14.dp)
                Focusable(modifier = Modifier.fillMaxWidth(), shape = shape, onClick = { vm.playEpisodes(series, flat, flat.indexOfFirst { it.id == e.id }) }) { _ ->
                    Row(Modifier.fillMaxWidth().glass(shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0x22FFFFFF))) {
                            val img = e.image.ifEmpty { series.cover }
                            if (img.isNotEmpty()) AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                            if (p != null && p.dur > 0) Bar(p.pos.toFloat() / p.dur, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(6.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T("E${e.num} · " + e.title.ifEmpty { "Episódio ${e.num}" }, 15.sp, FontWeight.Bold, maxLines = 1)
                            if (e.durationSecs > 0) T("${e.durationSecs / 60} min", 12.sp, color = Neon.muted)
                            if (e.plot.isNotBlank()) T(e.plot, 12.sp, color = Neon.muted, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

// ======================= BUSCA GLOBAL =======================
@Composable
fun SearchScreen(vm: AppVM) {
    var q by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { loading = true; vm.ensureAllVod(); loading = false }
    val liveIdx = remember(vm.live) { vm.live.map { Pair(norm(it.name), it) } }
    val radioIdx = remember(vm.radios) { vm.radios.map { Pair(norm(it.name), it) } }
    val movieIdx = remember(vm.allMovies) { vm.allMovies.map { Pair(norm(it.name), it) } }
    val seriesIdx = remember(vm.allSeries) { vm.allSeries.map { Pair(norm(it.name), it) } }
    val nq = norm(q.trim())
    val chans = if (nq.length >= 2) liveIdx.filter { it.first.contains(nq) && !vm.hiddenCat("live", it.second.catId) }.map { it.second }.take(30) else emptyList()
    val rads = if (nq.length >= 2) radioIdx.filter { it.first.contains(nq) }.map { it.second }.take(30) else emptyList()
    val movies = if (nq.length >= 2) movieIdx.filter { it.first.contains(nq) && !vm.hiddenCat("movie", it.second.catId) }.map { it.second }.take(40) else emptyList()
    val series = if (nq.length >= 2) seriesIdx.filter { it.first.contains(nq) && !vm.hiddenCat("series", it.second.catId) }.map { it.second }.take(40) else emptyList()
    val cardH = 190.dp
    Column(Modifier.fillMaxSize()) {
        SectionTitle("Buscar")
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            placeholder = { T("Canais, filmes, séries e rádios", 14.sp, color = Neon.muted) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Neon.cyan) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = fieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { }),
        )
        if (loading) T("Carregando catálogo…", 12.sp, color = Neon.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        if (nq.length < 2) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T("Digite ao menos 2 letras.", 15.sp, color = Neon.muted) }
        } else if (chans.isEmpty() && rads.isEmpty() && movies.isEmpty() && series.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { T("Nada encontrado.", 15.sp, color = Neon.muted) }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (chans.isNotEmpty()) item {
                    SectionTitle("Canais")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(chans) { c -> ChannelCard(c, Modifier.size(120.dp)) { vm.playLive(chans, chans.indexOf(c)) } }
                    }
                }
                if (rads.isNotEmpty()) item {
                    SectionTitle("Rádios")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(rads) { c -> ChannelCard(c, Modifier.size(120.dp)) { vm.playLive(rads, rads.indexOf(c), true) } }
                    }
                }
                if (movies.isNotEmpty()) item {
                    SectionTitle("Filmes")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(movies) { m -> PosterCard(m.name, m.poster, Modifier.size(cardH * 0.68f, cardH)) { vm.push(Overlay.MovieDet(m)) } }
                    }
                }
                if (series.isNotEmpty()) item {
                    SectionTitle("Séries")
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(series) { s -> PosterCard(s.name, s.cover, Modifier.size(cardH * 0.68f, cardH)) { vm.push(Overlay.SeriesDet(s)) } }
                    }
                }
            }
        }
    }
}

// ======================= AJUSTES =======================
@Composable
private fun SettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { T(label, 14.sp, color = Neon.muted) }
        T(value, 14.sp, FontWeight.SemiBold)
    }
}

@Composable
fun SettingsScreen(vm: AppVM) {
    val acc = vm.account
    var lv by remember { mutableStateOf(0) }
    val locked = remember(lv, vm.profile) { vm.store.lockedSet() }
    var showLocks by remember { mutableStateOf(false) }
    val exp = acc?.expDate
    val expTxt = if (exp == null || exp <= 0L) "Sem vencimento" else java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("pt", "BR")).format(java.util.Date(exp * 1000))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { SectionTitle("Ajustes") }
        item {
            Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                T("Conta", 17.sp, FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SettingRow("Usuário", acc?.username ?: "")
                SettingRow("Situação", if (acc?.status.equals("Active", true)) "Ativa" else (acc?.status ?: ""))
                SettingRow("Vencimento", expTxt)
                SettingRow("Telas simultâneas", "${acc?.maxConn ?: 1}")
                SettingRow("Perfil atual", vm.profile?.name ?: "")
                SettingRow("Servidor", Api.host)
                Spacer(Modifier.height(10.dp))
                Row {
                    NeonButton("Trocar perfil", primary = false) { vm.switchProfile() }
                    Spacer(Modifier.width(8.dp))
                    NeonButton("Recarregar listas", primary = false) { vm.reload() }
                }
                Spacer(Modifier.height(8.dp))
                NeonButton("Sair da conta", primary = false) { vm.logout() }
            }
        }
        if (!vm.kids) {
            item {
                Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    T("Controle dos pais", 17.sp, FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    T("Categorias bloqueadas pedem PIN e ficam ocultas nos perfis infantis.", 12.sp, color = Neon.muted)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        if (vm.store.pin.isEmpty()) NeonButton("Criar PIN") { vm.requestPin { lv++ } }
                        else NeonButton("Alterar PIN", primary = false) { vm.requestPin { vm.store.pin = ""; vm.requestPin { lv++ } } }
                        Spacer(Modifier.width(8.dp))
                        NeonButton(if (showLocks) "Ocultar categorias" else "Escolher categorias", primary = false) { showLocks = !showLocks }
                    }
                }
            }
            if (showLocks) {
                val rows = ArrayList<Triple<String, String, String>>()
                for (c in vm.liveCats) rows.add(Triple("live", c.id, "TV · " + c.name))
                for (c in vm.vodCats) rows.add(Triple("movie", c.id, "Filmes · " + c.name))
                for (c in vm.seriesCats) rows.add(Triple("series", c.id, "Séries · " + c.name))
                items(rows) { r ->
                    val key = r.first + ":" + r.second
                    val on = locked.contains(key)
                    Focusable(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), onClick = {
                        vm.requestPin { vm.store.setLocked(key, !on); lv++ }
                    }) { _ ->
                        Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { T(r.third, 14.sp, maxLines = 1) }
                            T(if (on) "🔒 Bloqueada" else "Livre", 13.sp, FontWeight.Bold, if (on) Neon.magenta else Neon.ok)
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                T("Histórico", 17.sp, FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                NeonButton("Limpar \"continuar assistindo\"", primary = false) { vm.clearHistory() }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Wordmark(18.sp)
                Spacer(Modifier.height(4.dp))
                T("Versão " + BuildConfig.VERSION_NAME, 12.sp, color = Neon.muted)
            }
        }
    }
}
