package com.example.nexogen

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("nexogen", Context.MODE_PRIVATE)

    var server: String
        get() = prefs.getString("server", "") ?: ""
        set(value) = prefs.edit().putString("server", value).apply()

    var myId: String
        get() = prefs.getString("myId", "") ?: ""
        set(value) = prefs.edit().putString("myId", value).apply()

    var peerId: String
        get() = prefs.getString("peerId", "") ?: ""
        set(value) = prefs.edit().putString("peerId", value).apply()

    var signalingServer: String
        get() = prefs.getString("signalingServer", "wss://signal.nexogen.local") ?: ""
        set(value) = prefs.edit().putString("signalingServer", value).apply()

    var stunServer: String
        get() = prefs.getString("stunServer", "stun:stun.l.google.com:19302") ?: ""
        set(value) = prefs.edit().putString("stunServer", value).apply()

    var fps: Int
        get() = prefs.getInt("fps", 15)
        set(value) = prefs.edit().putInt("fps", value).apply()

    var quality: Int
        get() = prefs.getInt("quality", 50)
        set(value) = prefs.edit().putInt("quality", value).apply()

    var audioEnabled: Boolean
        get() = prefs.getBoolean("audio", false)
        set(value) = prefs.edit().putBoolean("audio", value).apply()

    var controlEnabled: Boolean
        get() = prefs.getBoolean("control", true)
        set(value) = prefs.edit().putBoolean("control", value).apply()

    /** Режим "локальная сеть" — скрывает поле адреса сервера */
    var lanMode: Boolean
        get() = prefs.getBoolean("lanMode", false)
        set(value) = prefs.edit().putBoolean("lanMode", value).apply()
}