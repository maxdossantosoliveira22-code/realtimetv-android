package br.realtime.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** type: live | movie | series | episode */
data class Entry(
    val t: String,
    val id: String,
    val name: String,
    val icon: String = "",
    val rating: String = "",
    val ext: String = "",
    val sid: String = "",
    val pos: Long = 0,
    val dur: Long = 0,
    val num: String = ""
) {
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("t", t); o.put("id", id); o.put("name", name); o.put("icon", icon)
        o.put("rating", rating); o.put("ext", ext); o.put("sid", sid)
        o.put("pos", pos); o.put("dur", dur); o.put("num", num)
        return o
    }

    fun key(): String = "$t:$id"

    companion object {
        fun fromJson(o: JSONObject): Entry = Entry(
            o.optString("t"), o.optString("id"), o.optString("name"), o.optString("icon"),
            o.optString("rating"), o.optString("ext"), o.optString("sid"),
            o.optLong("pos"), o.optLong("dur"), o.optString("num")
        )
    }
}

object Store {
    private val sp by lazy { App.ctx.getSharedPreferences("rt", Context.MODE_PRIVATE) }

    var user: String
        get() = sp.getString("user", "") ?: ""
        set(v) { sp.edit().putString("user", v).apply() }
    var pass: String
        get() = sp.getString("pass", "") ?: ""
        set(v) { sp.edit().putString("pass", v).apply() }
    /** auto | hls | ts */
    var liveFormat: String
        get() = sp.getString("liveFormat", "auto") ?: "auto"
        set(v) { sp.edit().putString("liveFormat", v).apply() }
    var adultFilter: Boolean
        get() = sp.getBoolean("adult", true)
        set(v) { sp.edit().putBoolean("adult", v).apply() }

    private fun load(name: String): MutableList<Entry> {
        val s = sp.getString(name, "[]") ?: "[]"
        val a = try { JSONArray(s) } catch (e: Exception) { JSONArray() }
        val out = ArrayList<Entry>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(Entry.fromJson(o))
        }
        return out
    }

    private fun save(name: String, list: List<Entry>) {
        val a = JSONArray()
        for (e in list) a.put(e.toJson())
        sp.edit().putString(name, a.toString()).apply()
    }

    fun favs(): List<Entry> = load("fav")
    fun isFav(e: Entry): Boolean = load("fav").any { it.key() == e.key() }
    fun toggleFav(e: Entry): Boolean {
        val l = load("fav")
        val i = l.indexOfFirst { it.key() == e.key() }
        val now: Boolean
        if (i >= 0) { l.removeAt(i); now = false } else { l.add(0, e.copy(pos = 0, dur = 0)); now = true }
        save("fav", l)
        return now
    }

    fun recent(): List<Entry> = load("recent")
    fun recentFind(t: String, id: String): Entry? = load("recent").firstOrNull { it.t == t && it.id == id }
    fun recentFindSeries(sid: String): Entry? = load("recent").firstOrNull { it.t == "episode" && it.sid == sid }
    fun recentUpsert(e: Entry) {
        val l = load("recent")
        l.removeAll { it.key() == e.key() }
        l.add(0, e)
        while (l.size > 30) l.removeAt(l.size - 1)
        save("recent", l)
    }
    fun recentRemove(t: String, id: String) {
        val l = load("recent")
        l.removeAll { it.t == t && it.id == id }
        save("recent", l)
    }

    fun highlights(sec: String): List<Entry> = load("hl_$sec")
    fun setHighlights(sec: String, l: List<Entry>) = save("hl_$sec", l)

    fun clearUserData() {
        sp.edit().remove("fav").remove("recent").remove("hl_vod").remove("hl_series").apply()
    }
}
