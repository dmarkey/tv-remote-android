package com.dmarkey.tvremote

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Tv(val name: String, val host: String)

/** Paired TVs and the last one used. The pairing itself lives in the Keystore identity. */
class TvStore(context: Context) {
    private val prefs = context.getSharedPreferences("tvs", Context.MODE_PRIVATE)

    fun load(): List<Tv> {
        val json = JSONArray(prefs.getString("list", "[]"))
        return List(json.length()) { json.getJSONObject(it).let { o -> Tv(o.getString("name"), o.getString("host")) } }
    }

    fun save(tvs: List<Tv>) {
        val json = JSONArray()
        tvs.forEach { json.put(JSONObject().put("name", it.name).put("host", it.host)) }
        prefs.edit().putString("list", json.toString()).apply()
    }

    var lastHost: String?
        get() = prefs.getString("last", null)
        set(value) = prefs.edit().putString("last", value).apply()
}
