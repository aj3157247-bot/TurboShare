package com.fastsend.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)
    fun add(item: TransferItem) {
        val old = JSONArray(prefs.getString("items", "[]"))
        val obj = JSONObject().put("name", item.name).put("size", item.size).put("direction", item.direction)
            .put("timestamp", item.timestamp).put("success", item.success).put("speed", item.speedBytesPerSecond)
        val out = JSONArray().put(obj)
        for (i in 0 until minOf(old.length(), 99)) out.put(old.getJSONObject(i))
        prefs.edit().putString("items", out.toString()).apply()
    }
    fun all(): List<TransferItem> {
        val a = JSONArray(prefs.getString("items", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            TransferItem(o.getString("name"), o.getLong("size"), o.getString("direction"), o.getLong("timestamp"), o.getBoolean("success"), o.optLong("speed", 0L))
        }
    }
    fun clear() = prefs.edit().remove("items").apply()
}
