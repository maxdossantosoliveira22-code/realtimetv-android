package com.realtimetv.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Profile(val id: String, val name: String, val color: Long, val kids: Boolean)
data class Progress(
    val key: String, val type: String, val id: Int, val title: String, val image: String, val ext: String,
    val pos: Long, val dur: Long, val ts: Long, val seriesId: Int = 0, val season: Int = 0, val ep: Int = 0,
)

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("rtv", Context.MODE_PRIVATE)

    var user: String
        get() = sp.getString("user", "") ?: ""
        set(v) { sp.edit().putString("user", v).apply() }
    var pass: String
        get() = sp.getString("pass", "") ?: ""
        set(v) { sp.edit().putString("pass", v).apply() }
    var pin: String
        get() = sp.getString("pin", "") ?: ""
        set(v) { sp.edit().putString("pin", v).apply() }
    var lockInit: Boolean
        get() = sp.getBoolean("lock_init", false)
        set(v) { sp.edit().putBoolean("lock_init", v).apply() }
    var lastProfile: String
        get() = sp.getString("last_profile", "") ?: ""
        set(v) { sp.edit().putString("last_profile", v).apply() }

    fun clearLogin() { sp.edit().remove("user").remove("pass").apply() }

    // ---------- perfis ----------
    fun profiles(): List<Profile> {
        val a = try { JSONArray(sp.getString("profiles", "[]")) } catch (e: Exception) { JSONArray() }
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Profile(o.getString("id"), o.getString("name"), o.optLong("color", 0xFF00E5FF), o.optBoolean("kids", false))
        }
    }

    fun saveProfiles(list: List<Profile>) {
        val a = JSONArray()
        for (p in list) a.put(JSONObject().put("id", p.id).put("name", p.name).put("color", p.color).put("kids", p.kids))
        sp.edit().putString("profiles", a.toString()).apply()
    }

    // ---------- favoritos ----------
    fun favs(pid: String): List<String> {
        val a = try { JSONArray(sp.getString("fav_$pid", "[]")) } catch (e: Exception) { JSONArray() }
        return List(a.length()) { a.getString(it) }
    }

    fun saveFavs(pid: String, l: List<String>) {
        val a = JSONArray()
        for (s in l) a.put(s)
        sp.edit().putString("fav_$pid", a.toString()).apply()
    }

    // ---------- continuar assistindo ----------
    fun progress(pid: String): List<Progress> {
        val a = try { JSONArray(sp.getString("prog_$pid", "[]")) } catch (e: Exception) { JSONArray() }
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Progress(o.getString("key"), o.getString("type"), o.getInt("id"), o.optString("title"), o.optString("image"), o.optString("ext", "mp4"),
                o.optLong("pos"), o.optLong("dur"), o.optLong("ts"), o.optInt("seriesId"), o.optInt("season"), o.optInt("ep"))
        }
    }

    private fun writeProgress(pid: String, l: List<Progress>) {
        val a = JSONArray()
        for (p in l) a.put(JSONObject().put("key", p.key).put("type", p.type).put("id", p.id).put("title", p.title).put("image", p.image).put("ext", p.ext)
            .put("pos", p.pos).put("dur", p.dur).put("ts", p.ts).put("seriesId", p.seriesId).put("season", p.season).put("ep", p.ep))
        sp.edit().putString("prog_$pid", a.toString()).apply()
    }

    fun saveProgress(pid: String, p: Progress) {
        val l = (progress(pid).filter { it.key != p.key } + p).sortedByDescending { it.ts }.take(80)
        writeProgress(pid, l)
    }

    fun removeProgress(pid: String, key: String) { writeProgress(pid, progress(pid).filter { it.key != key }) }
    fun clearProgress(pid: String) { writeProgress(pid, emptyList()) }

    // ---------- controle dos pais ----------
    private var lockCache: Set<String>? = null

    fun lockedSet(): Set<String> {
        val c = lockCache
        if (c != null) return c
        val s: Set<String> = HashSet(sp.getStringSet("lock", emptySet()) ?: emptySet())
        lockCache = s
        return s
    }

    fun setLocked(key: String, on: Boolean) {
        val s = HashSet(lockedSet())
        if (on) s.add(key) else s.remove(key)
        sp.edit().putStringSet("lock", s).apply()
        lockCache = s
    }

    fun setLockedAll(keys: Set<String>) {
        sp.edit().putStringSet("lock", HashSet(keys)).apply()
        lockCache = HashSet(keys)
    }
}
