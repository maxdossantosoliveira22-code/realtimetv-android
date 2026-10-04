package com.realtimetv.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class Category(val id: String, val name: String)
data class Channel(val id: Int, val num: Int, val name: String, val logo: String, val epgId: String, val catId: String, val radio: Boolean)
data class Movie(val id: Int, val name: String, val poster: String, val rating: String, val catId: String, val ext: String, val added: Long)
data class Series(val id: Int, val name: String, val cover: String, val plot: String, val genre: String, val year: String, val rating: String, val catId: String, val backdrop: String, val added: Long)
data class Episode(val id: Int, val season: Int, val num: Int, val title: String, val ext: String, val image: String, val plot: String, val durationSecs: Int)
data class SeriesDetail(val seasons: List<Int>, val episodes: Map<Int, List<Episode>>, val cast: String, val director: String, val backdrop: String)
data class MovieDetail(val plot: String, val genre: String, val year: String, val cast: String, val director: String, val duration: String, val backdrop: String)
data class EpgItem(val title: String, val desc: String, val start: Long, val stop: Long)
data class Account(val username: String, val status: String, val expDate: Long?, val maxConn: Int)

class AuthException(msg: String) : Exception(msg)

object Api {
    val base: String = BuildConfig.SERVER_URL.trimEnd('/')
    var user: String = ""
    var pass: String = ""

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(action: String?, vararg extra: Pair<String, String>): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder(base).append("/player_api.php?username=").append(enc(user)).append("&password=").append(enc(pass))
        if (action != null) sb.append("&action=").append(action)
        for ((k, v) in extra) sb.append('&').append(k).append('=').append(enc(v))
        val req = Request.Builder().url(sb.toString()).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("Servidor respondeu " + r.code)
            r.body?.string() ?: ""
        }
    }

    private fun JSONObject.s(k: String): String {
        val v = opt(k)
        return if (v == null || v == JSONObject.NULL) "" else v.toString()
    }

    private fun JSONObject.firstOf(k: String): String {
        val a = optJSONArray(k)
        return if (a != null && a.length() > 0) a.optString(0, "") else ""
    }

    suspend fun login(u: String, p: String): Account {
        user = u; pass = p
        val o = try { JSONObject(get(null)) } catch (e: org.json.JSONException) { throw IOException("Resposta inválida do servidor") }
        val ui = o.optJSONObject("user_info") ?: throw AuthException("Usuário ou senha inválidos")
        if (ui.optInt("auth", 0) != 1) throw AuthException("Usuário ou senha inválidos")
        val msg = ui.s("message")
        if (msg.isNotBlank() && !msg.contains("Realtime", true)) throw AuthException("Servidor não reconhecido. Este app funciona somente com o servidor Realtime TV.")
        val status = ui.s("status")
        if (status.equals("Disabled", true)) throw AuthException("Esta conta está desativada")
        if (status.equals("Expired", true)) throw AuthException("Esta conta está vencida")
        return Account(ui.s("username").ifEmpty { u }, status, ui.s("exp_date").toLongOrNull(), ui.s("max_connections").toIntOrNull() ?: 1)
    }

    private suspend fun cats(action: String): List<Category> {
        val a = JSONArray(get(action))
        return List(a.length()) { i -> val o = a.getJSONObject(i); Category(o.s("category_id"), o.s("category_name")) }
    }

    suspend fun liveCategories() = cats("get_live_categories")
    suspend fun vodCategories() = cats("get_vod_categories")
    suspend fun seriesCategories() = cats("get_series_categories")

    suspend fun liveChannels(): List<Channel> {
        val a = JSONArray(get("get_live_streams"))
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Channel(o.optInt("stream_id"), i + 1, o.s("name"), o.s("stream_icon"), o.s("epg_channel_id"), o.s("category_id"), false)
        }
    }

    suspend fun vodStreams(cat: String?): List<Movie> {
        val txt = if (cat == null) get("get_vod_streams") else get("get_vod_streams", "category_id" to cat)
        val a = JSONArray(txt)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Movie(o.optInt("stream_id"), o.s("name"), o.s("stream_icon"), o.s("rating"), o.s("category_id"), o.s("container_extension").ifEmpty { "mp4" }, o.s("added").toLongOrNull() ?: 0L)
        }
    }

    suspend fun seriesList(cat: String?): List<Series> {
        val txt = if (cat == null) get("get_series") else get("get_series", "category_id" to cat)
        val a = JSONArray(txt)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Series(o.optInt("series_id"), o.s("name"), o.s("cover"), o.s("plot"), o.s("genre"), o.s("releaseDate"), o.s("rating"), o.s("category_id"), o.firstOf("backdrop_path"), o.s("last_modified").toLongOrNull() ?: 0L)
        }
    }

    suspend fun movieInfo(id: Int): MovieDetail {
        val o = try { JSONObject(get("get_vod_info", "vod_id" to id.toString())) } catch (e: Exception) { return MovieDetail("", "", "", "", "", "", "") }
        val i = o.optJSONObject("info") ?: return MovieDetail("", "", "", "", "", "", "")
        return MovieDetail(i.s("plot").ifEmpty { i.s("description") }, i.s("genre"), i.s("releasedate"), i.s("cast"), i.s("director"), i.s("duration"), i.firstOf("backdrop_path"))
    }

    suspend fun seriesInfo(id: Int): SeriesDetail {
        val o = JSONObject(get("get_series_info", "series_id" to id.toString()))
        val info = o.optJSONObject("info")
        val eps = o.optJSONObject("episodes")
        val map = LinkedHashMap<Int, List<Episode>>()
        if (eps != null) {
            val keys = ArrayList<String>()
            val iter = eps.keys()
            while (iter.hasNext()) keys.add(iter.next())
            for (k in keys.sortedBy { it.toIntOrNull() ?: 0 }) {
                val arr = eps.optJSONArray(k) ?: continue
                val list = ArrayList<Episode>()
                for (j in 0 until arr.length()) {
                    val e = arr.getJSONObject(j)
                    val ei = e.optJSONObject("info")
                    list.add(Episode(e.s("id").toIntOrNull() ?: 0, e.optInt("season", k.toIntOrNull() ?: 1), e.optInt("episode_num", j + 1), e.s("title"),
                        e.s("container_extension").ifEmpty { "mp4" }, ei?.s("movie_image") ?: "", ei?.s("plot") ?: "", ei?.optInt("duration_secs", 0) ?: 0))
                }
                map[k.toIntOrNull() ?: 1] = list
            }
        }
        return SeriesDetail(map.keys.toList(), map, info?.s("cast") ?: "", info?.s("director") ?: "", info?.firstOf("backdrop_path") ?: "")
    }

    private fun b64(s: String): String = try { String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8) } catch (e: Exception) { s }

    suspend fun shortEpg(streamId: Int, limit: Int): List<EpgItem> {
        val o = JSONObject(get("get_short_epg", "stream_id" to streamId.toString(), "limit" to limit.toString()))
        val a = o.optJSONArray("epg_listings") ?: return emptyList()
        return List(a.length()) { i ->
            val e = a.getJSONObject(i)
            EpgItem(b64(e.s("title")), b64(e.s("description")), e.s("start_timestamp").toLongOrNull() ?: 0L, e.s("stop_timestamp").toLongOrNull() ?: 0L)
        }
    }

    fun liveUrl(id: Int) = "$base/live/$user/$pass/$id.m3u8"
    fun movieUrl(id: Int, ext: String) = "$base/movie/$user/$pass/$id.$ext"
    fun episodeUrl(id: Int, ext: String) = "$base/series/$user/$pass/$id.$ext"
    val host: String get() = base.removePrefix("http://").removePrefix("https://")
}
