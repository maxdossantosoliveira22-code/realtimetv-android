package br.realtime.tv

import android.net.Uri
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

class Cat(val id: String, val name: String)
class Raw(val e: Entry, val added: Long, val cat: String)
class Info(
    val plot: String, val genre: String, val year: String, val rating: String,
    val duration: String, val backdrop: String, val poster: String, val ext: String, val cast: String
)
class Ep(val id: String, val num: Int, val title: String, val ext: String, val duration: String, val plot: String, val image: String)
class SeriesInfo(val info: Info, val seasons: Map<Int, List<Ep>>)
class EpgItem(val title: String, val desc: String, val start: Long, val stop: Long)

object Api {
    var userInfo: JSONObject? = null

    private val dir: File by lazy { File(App.ctx.cacheDir, "api").apply { mkdirs() } }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun base(): String =
        Config.SERVER + "/player_api.php?username=" + enc(Store.user) + "&password=" + enc(Store.pass)

    fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        val sb = StringBuilder()
        for (b in d) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    /** abre a URL seguindo redirecionamentos (inclusive http -> https) */
    fun open(url: String, timeout: Int): HttpURLConnection? {
        var u = url
        for (i in 0 until 6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = timeout
            c.readTimeout = timeout
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Realtime/1.0 (Android)")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) return null
                u = URL(URL(u), loc).toString()
                continue
            }
            if (code in 200..299) return c
            c.disconnect()
            return null
        }
        return null
    }

    private fun httpText(url: String, timeout: Int): String? {
        return try {
            val c = open(url, timeout) ?: return null
            val s = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            s
        } catch (e: Exception) {
            null
        }
    }

    /** baixa para um arquivo no cache (sem carregar tudo na memória). Usa o arquivo antigo se a rede falhar. */
    fun fetchFile(url: String, ttlSec: Int, timeout: Int = 30000, force: Boolean = false): File? {
        val f = File(dir, md5(url) + ".json")
        val fresh = f.exists() && f.length() > 2 && System.currentTimeMillis() - f.lastModified() < ttlSec * 1000L
        if (!force && fresh) return f
        val tmp = File(dir, md5(url) + ".tmp")
        try {
            val c = open(url, timeout)
            if (c == null) return if (f.exists()) f else null
            c.inputStream.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
            c.disconnect()
            if (tmp.length() < 2) {
                tmp.delete()
                return if (f.exists()) f else null
            }
            f.delete()
            tmp.renameTo(f)
            return f
        } catch (e: Exception) {
            tmp.delete()
            return if (f.exists()) f else null
        }
    }

    fun clearCache() {
        dir.listFiles()?.forEach { it.delete() }
        Img.clearDisk()
    }

    fun isAdult(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        val words = listOf("xxx", "adult", "+18", "18+", "porn", "erotic", "hentai", "playboy", "sexy", "sexo")
        return words.any { n.contains(it) }
    }

    // ---------- login ----------
    /** devolve null se deu certo, ou a mensagem de erro */
    fun login(): String? {
        val s = httpText(base(), 15000) ?: return "Sem resposta do servidor. Verifique a internet."
        val o = try { JSONObject(s) } catch (e: Exception) { return "O servidor devolveu uma resposta inválida." }
        val ui = o.optJSONObject("user_info") ?: return "Usuário ou senha incorretos."
        if (ui.optString("auth") != "1") return "Usuário ou senha incorretos."
        val st = ui.optString("status")
        if (st.isNotEmpty() && !st.equals("Active", true)) return "Assinatura sem acesso (status: $st)."
        userInfo = ui
        return null
    }

    // ---------- listas ----------
    fun categories(sec: String): List<Cat>? {
        val act = when (sec) {
            "live" -> "get_live_categories"
            "vod" -> "get_vod_categories"
            else -> "get_series_categories"
        }
        val f = fetchFile(base() + "&action=" + act, 21600) ?: return null
        val a = try { JSONArray(f.readText()) } catch (e: Exception) { f.delete(); return null }
        val out = ArrayList<Cat>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val name = o.optString("category_name")
            if (Store.adultFilter && isAdult(name)) continue
            out.add(Cat(o.optString("category_id"), name))
        }
        return out
    }

    fun adultIds(sec: String): Set<String> {
        val out = HashSet<String>()
        if (!Store.adultFilter) return out
        val act = when (sec) {
            "live" -> "get_live_categories"
            "vod" -> "get_vod_categories"
            else -> "get_series_categories"
        }
        val f = fetchFile(base() + "&action=" + act, 21600) ?: return out
        try {
            val a = JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (isAdult(o.optString("category_name"))) out.add(o.optString("category_id"))
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun streams(sec: String, cat: String?, ttl: Int = 1800): List<Raw>? {
        val act = when (sec) {
            "live" -> "get_live_streams"
            "vod" -> "get_vod_streams"
            else -> "get_series"
        }
        var u = base() + "&action=" + act
        if (!cat.isNullOrEmpty()) u += "&category_id=" + cat
        val f = fetchFile(u, ttl, if (cat.isNullOrEmpty()) 40000 else 30000) ?: return null
        return parse(f, sec)
    }

    /** lista completa de uma seção; se o servidor não entregar tudo de uma vez, junta categoria por categoria */
    fun streamsAll(sec: String, ttl: Int = 43200): List<Raw>? {
        val full = try { streams(sec, null, ttl) } catch (e: Throwable) { null }
        if (full != null && full.isNotEmpty()) return full
        val cats = categories(sec) ?: return full
        val out = java.util.Collections.synchronizedList(ArrayList<Raw>())
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        for (c in cats) {
            pool.execute {
                try { streams(sec, c.id, ttl)?.let { out.addAll(it) } } catch (e: Throwable) { }
            }
        }
        pool.shutdown()
        try { pool.awaitTermination(180, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) { }
        return if (out.isEmpty()) full else ArrayList(out)
    }

    private fun str(r: JsonReader): String = when (r.peek()) {
        JsonToken.STRING, JsonToken.NUMBER -> r.nextString()
        JsonToken.BOOLEAN -> r.nextBoolean().toString()
        JsonToken.NULL -> { r.nextNull(); "" }
        else -> { r.skipValue(); "" }
    }

    fun ratingText(s: String): String {
        val d = s.toDoubleOrNull() ?: return ""
        if (d <= 0.0) return ""
        return String.format(Locale.US, "%.1f", d)
    }

    private fun parse(f: File, sec: String): List<Raw>? {
        val out = ArrayList<Raw>()
        val type = when (sec) { "live" -> "live"; "vod" -> "movie"; else -> "series" }
        try {
            JsonReader(InputStreamReader(f.inputStream().buffered(), "UTF-8")).use { r ->
                if (r.peek() != JsonToken.BEGIN_ARRAY) {
                    f.delete()
                    return null
                }
                r.beginArray()
                while (r.hasNext()) {
                    if (r.peek() != JsonToken.BEGIN_OBJECT) { r.skipValue(); continue }
                    var name = ""; var id = ""; var icon = ""; var rating = ""
                    var ext = ""; var cat = ""; var num = ""; var added = 0L
                    r.beginObject()
                    while (r.hasNext()) {
                        when (r.nextName()) {
                            "name" -> { name = str(r) }
                            "stream_id" -> { if (sec != "series") id = str(r) else r.skipValue() }
                            "series_id" -> { if (sec == "series") id = str(r) else r.skipValue() }
                            "stream_icon" -> { if (sec != "series") icon = str(r) else r.skipValue() }
                            "cover" -> { if (sec == "series") icon = str(r) else r.skipValue() }
                            "rating" -> { rating = str(r) }
                            "container_extension" -> { ext = str(r) }
                            "category_id" -> { cat = str(r) }
                            "num" -> { num = str(r) }
                            "added", "last_modified" -> {
                                val v = str(r).toLongOrNull() ?: 0L
                                if (v > added) added = v
                            }
                            else -> r.skipValue()
                        }
                    }
                    r.endObject()
                    if (name.isNotBlank() && id.isNotEmpty()) {
                        val e = Entry(type, id, name.trim(), icon, ratingText(rating), if (ext.isEmpty()) "mp4" else ext, "", 0, 0, num)
                        out.add(Raw(e, added, cat))
                    }
                }
                r.endArray()
            }
        } catch (e: Exception) {
            if (out.isEmpty()) return null
        }
        return out
    }

    private fun isNewName(n: String): Boolean {
        val t = n.lowercase(Locale.ROOT)
        val keys = listOf("lançamento", "lancamento", "novidade", "release", "new", "2026", "2025")
        return keys.any { t.contains(it) }
    }

    /** novidades para a tela inicial */
    fun highlights(sec: String): List<Entry>? {
        val cats = categories(sec) ?: return null
        var pick = ""
        for (c in cats) {
            if (isNewName(c.name)) { pick = c.id; break }
        }
        if (pick.isEmpty()) {
            val first = cats.firstOrNull() ?: return null
            pick = first.id
        }
        val raw = streams(sec, pick) ?: return null
        return raw.sortedByDescending { it.added }.take(20).map { it.e }
    }

    // ---------- detalhes ----------
    private fun backdropOf(info: JSONObject): String {
        val b = info.opt("backdrop_path")
        if (b is JSONArray && b.length() > 0) return b.optString(0)
        if (b is String) return b
        return ""
    }

    private fun infoFrom(o: JSONObject?, ext: String): Info {
        val i = o ?: JSONObject()
        var year = i.optString("releasedate")
        if (year.isEmpty()) year = i.optString("releaseDate")
        if (year.isEmpty()) year = i.optString("year")
        if (year.length >= 4) year = year.substring(0, 4)
        var dur = i.optString("duration")
        if (dur.isEmpty()) dur = i.optString("episode_run_time")
        var poster = i.optString("movie_image")
        if (poster.isEmpty()) poster = i.optString("cover")
        return Info(
            i.optString("plot").ifEmpty { i.optString("description") },
            i.optString("genre"), year, ratingText(i.optString("rating")), dur,
            backdropOf(i), poster, ext, i.optString("cast")
        )
    }

    fun vodInfo(id: String): Info? {
        val f = fetchFile(base() + "&action=get_vod_info&vod_id=" + id, 86400, 20000) ?: return null
        val o = try { JSONObject(f.readText()) } catch (e: Exception) { f.delete(); return null }
        val md = o.optJSONObject("movie_data")
        return infoFrom(o.optJSONObject("info"), md?.optString("container_extension") ?: "")
    }

    fun seriesInfo(id: String): SeriesInfo? {
        val f = fetchFile(base() + "&action=get_series_info&series_id=" + id, 3600, 30000) ?: return null
        val o = try { JSONObject(f.readText()) } catch (e: Exception) { f.delete(); return null }
        val info = infoFrom(o.optJSONObject("info"), "")
        val seasons = java.util.TreeMap<Int, List<Ep>>()
        val eps = o.opt("episodes")
        fun addList(season: Int, arr: JSONArray) {
            val list = ArrayList<Ep>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val ei = e.optJSONObject("info")
                var num = e.optInt("episode_num", i + 1)
                if (num <= 0) num = i + 1
                list.add(
                    Ep(
                        e.optString("id"), num, e.optString("title"),
                        e.optString("container_extension").ifEmpty { "mp4" },
                        ei?.optString("duration") ?: "", ei?.optString("plot") ?: "",
                        ei?.optString("movie_image") ?: ""
                    )
                )
            }
            list.sortBy { it.num }
            if (list.isNotEmpty()) seasons[season] = list
        }
        if (eps is JSONObject) {
            val keys = eps.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = eps.optJSONArray(k) ?: continue
                addList(k.toIntOrNull() ?: 1, arr)
            }
        } else if (eps is JSONArray) {
            for (i in 0 until eps.length()) {
                val arr = eps.optJSONArray(i) ?: continue
                addList(i + 1, arr)
            }
        }
        return SeriesInfo(info, seasons)
    }

    fun epg(streamId: String): List<EpgItem>? {
        val f = fetchFile(base() + "&action=get_short_epg&stream_id=" + streamId + "&limit=4", 120, 8000) ?: return null
        val o = try { JSONObject(f.readText()) } catch (e: Exception) { return null }
        val a = o.optJSONArray("epg_listings") ?: return emptyList()
        val out = ArrayList<EpgItem>()
        for (i in 0 until a.length()) {
            val x = a.optJSONObject(i) ?: continue
            out.add(
                EpgItem(
                    b64(x.optString("title")), b64(x.optString("description")),
                    x.optString("start_timestamp").toLongOrNull() ?: 0L,
                    x.optString("stop_timestamp").toLongOrNull() ?: 0L
                )
            )
        }
        return out
    }

    private fun b64(s: String): String {
        if (s.isEmpty()) return ""
        return try {
            val r = String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
            if (r.isEmpty()) s else r
        } catch (e: Exception) {
            s
        }
    }

    // ---------- endereços de vídeo ----------
    private fun seg(s: String): String = Uri.encode(s)

    private fun liveUrl(id: String, fmt: String): String =
        Config.SERVER + "/live/" + seg(Store.user) + "/" + seg(Store.pass) + "/" + id + "." + fmt

    private fun vodUrl(kind: String, id: String, ext: String): String =
        Config.SERVER + "/" + kind + "/" + seg(Store.user) + "/" + seg(Store.pass) + "/" + id + "." + ext

    /** formatos a tentar, em ordem (o player cai para o próximo se falhar) */
    fun candidates(e: Entry): List<String> {
        if (e.t == "live") {
            return when (Store.liveFormat) {
                "hls" -> listOf(liveUrl(e.id, "m3u8"))
                "ts" -> listOf(liveUrl(e.id, "ts"))
                else -> listOf(liveUrl(e.id, "m3u8"), liveUrl(e.id, "ts"))
            }
        }
        val kind = if (e.t == "movie") "movie" else "series"
        val exts = LinkedHashSet<String>()
        exts.add(if (e.ext.isEmpty()) "mp4" else e.ext)
        exts.add("mp4")
        exts.add("mkv")
        return exts.map { vodUrl(kind, e.id, it) }
    }
}
