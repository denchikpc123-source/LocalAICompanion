package com.localai.companion.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_prefs", Context.MODE_PRIVATE)

    var serverIp: String
        get() = prefs.getString("server_ip", "192.168.0.102") ?: "192.168.0.102"
        set(value) = prefs.edit().putString("server_ip", value).apply()

    var serverPort: String
        get() = prefs.getString("server_port", "1234") ?: "1234"
        set(value) = prefs.edit().putString("server_port", value).apply()

    var selectedModel: String
        get() = prefs.getString("model", "") ?: ""
        set(value) = prefs.edit().putString("model", value).apply()

    var useTts: Boolean
        get() = prefs.getBoolean("use_tts", true)
        set(value) = prefs.edit().putBoolean("use_tts", value).apply()

    var useOverlay: Boolean
        get() = prefs.getBoolean("use_overlay", false)
        set(value) = prefs.edit().putBoolean("use_overlay", value).apply()

    var ttsRate: Float
        get() = prefs.getFloat("tts_rate", 1.0f)
        set(value) = prefs.edit().putFloat("tts_rate", value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat("tts_pitch", 1.0f)
        set(value) = prefs.edit().putFloat("tts_pitch", value).apply()

    fun getBaseUrl(): String = "http://$serverIp:$serverPort/v1"
}
