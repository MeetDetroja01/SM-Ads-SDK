package com.smappsstudio.ads

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

data class AdPlacement(
    val id: String = "",
    val isEnable: Boolean = false,
    val enableUaCheck: Boolean = false,
    val reloadIntervalSeconds: Int = 30
)

object SMAdConfig {
    private var placements: Map<String, AdPlacement> = emptyMap()

    /**
     * Load configuration from the assets folder.
     * Uses `ad_config_debug.json` if in debug mode, otherwise `ad_config.json`.
     */
    fun initialize(context: Context, isDebug: Boolean, fileName: String? = null) {
        val fileToLoad = fileName ?: if (isDebug) "ad_config_debug.json" else "ad_config.json"
        try {
            val jsonString = context.assets.open(fileToLoad).bufferedReader().use { it.readText() }
            val mapType = object : TypeToken<Map<String, AdPlacement>>() {}.type
            placements = Gson().fromJson(jsonString, mapType)
        } catch (e: IOException) {
            e.printStackTrace()
            placements = emptyMap()
        }
    }

    /**
     * Directly initialize with a JSON string (e.g. from Firebase Remote Config).
     */
    fun initializeWithJson(jsonString: String) {
        try {
            val mapType = object : TypeToken<Map<String, AdPlacement>>() {}.type
            placements = Gson().fromJson(jsonString, mapType)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Get placement by its key name (e.g., "inter_splash")
     */
    fun getPlacement(key: String): AdPlacement {
        return placements[key] ?: AdPlacement()
    }
}
