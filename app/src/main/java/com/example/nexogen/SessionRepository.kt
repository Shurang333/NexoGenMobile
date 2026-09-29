package com.example.nexogen

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SessionRecord(
    val peerId: String,
    val server: String,
    val fps: Int,
    val quality: Int,
    val lastConnectedAt: Long,
    val status: String // "ACTIVE", "IDLE", "FAILED"
)

class SessionRepository(context: Context) {

    private val prefs = context.getSharedPreferences("nexogen_sessions", Context.MODE_PRIVATE)

    fun getAll(): List<SessionRecord> {
        val raw = prefs.getString("sessions", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SessionRecord(
                    peerId = o.getString("peerId"),
                    server = o.getString("server"),
                    fps = o.optInt("fps", 15),
                    quality = o.optInt("quality", 50),
                    lastConnectedAt = o.optLong("lastConnectedAt", 0L),
                    status = o.optString("status", "IDLE")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun upsert(record: SessionRecord) {
        val list = getAll().toMutableList()
        val existing = list.indexOfFirst { it.peerId == record.peerId && it.server == record.server }
        if (existing >= 0) list[existing] = record else list.add(0, record)
        save(list)
    }

    fun updateStatus(peerId: String, server: String, status: String) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.peerId == peerId && it.server == server }
        if (idx >= 0) {
            val r = list[idx]
            list[idx] = r.copy(status = status, lastConnectedAt = System.currentTimeMillis())
            save(list)
        }
    }

    fun clear() {
        prefs.edit().remove("sessions").apply()
    }

    private fun save(list: List<SessionRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            val o = JSONObject()
            o.put("peerId", r.peerId)
            o.put("server", r.server)
            o.put("fps", r.fps)
            o.put("quality", r.quality)
            o.put("lastConnectedAt", r.lastConnectedAt)
            o.put("status", r.status)
            arr.put(o)
        }
        prefs.edit().putString("sessions", arr.toString()).apply()
    }
}