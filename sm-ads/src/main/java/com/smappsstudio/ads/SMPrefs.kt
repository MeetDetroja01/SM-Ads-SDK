package com.smappsstudio.ads

import android.content.Context
import android.content.SharedPreferences

object SMPrefs {
    private const val PREF_NAME = "sm_ads_preferences"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun putBoolean(context: Context, key: String, value: Boolean) {
        getPrefs(context).edit().putBoolean(key, value).apply()
    }

    @JvmStatic
    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getPrefs(context).getBoolean(key, defaultValue)
    }

    @JvmStatic
    fun putString(context: Context, key: String, value: String) {
        getPrefs(context).edit().putString(key, value).apply()
    }

    @JvmStatic
    fun getString(context: Context, key: String, defaultValue: String? = null): String? {
        return getPrefs(context).getString(key, defaultValue)
    }

    @JvmStatic
    fun putInt(context: Context, key: String, value: Int) {
        getPrefs(context).edit().putInt(key, value).apply()
    }

    @JvmStatic
    fun getInt(context: Context, key: String, defaultValue: Int = 0): Int {
        return getPrefs(context).getInt(key, defaultValue)
    }

    @JvmStatic
    fun putLong(context: Context, key: String, value: Long) {
        getPrefs(context).edit().putLong(key, value).apply()
    }

    @JvmStatic
    fun getLong(context: Context, key: String, defaultValue: Long = 0L): Long {
        return getPrefs(context).getLong(key, defaultValue)
    }

    @JvmStatic
    fun remove(context: Context, key: String) {
        getPrefs(context).edit().remove(key).apply()
    }

    @JvmStatic
    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
