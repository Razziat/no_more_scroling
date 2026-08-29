package com.antiscroll.mobile.data

import android.content.Context

class SettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var youtubeBlockingEnabled: Boolean
        get() = preferences.getBoolean(KEY_YOUTUBE_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_YOUTUBE_ENABLED, value).apply()
        }

    var instagramBlockingEnabled: Boolean
        get() = preferences.getBoolean(KEY_INSTAGRAM_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_INSTAGRAM_ENABLED, value).apply()
        }

    var punitiveModeEnabled: Boolean
        get() = preferences.getBoolean(KEY_PUNITIVE_MODE_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_PUNITIVE_MODE_ENABLED, value).apply()
        }

    var instagramSessionLimitEnabled: Boolean
        get() = preferences.getBoolean(KEY_INSTAGRAM_SESSION_LIMIT_ENABLED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_INSTAGRAM_SESSION_LIMIT_ENABLED, value).apply()
        }

    fun isBlockingEnabledFor(packageName: String): Boolean = when (packageName) {
        YOUTUBE_PACKAGE -> youtubeBlockingEnabled
        INSTAGRAM_PACKAGE -> instagramBlockingEnabled
        else -> false
    }

    fun shouldMonitorPackage(packageName: String): Boolean = when (packageName) {
        YOUTUBE_PACKAGE -> youtubeBlockingEnabled
        INSTAGRAM_PACKAGE ->
            instagramBlockingEnabled || instagramSessionLimitEnabled
        else -> false
    }

    companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"

        private const val PREFERENCES_NAME = "anti_scroll_settings"
        private const val KEY_YOUTUBE_ENABLED = "youtube_blocking_enabled"
        private const val KEY_INSTAGRAM_ENABLED = "instagram_blocking_enabled"
        private const val KEY_PUNITIVE_MODE_ENABLED = "punitive_mode_enabled"
        private const val KEY_INSTAGRAM_SESSION_LIMIT_ENABLED =
            "instagram_session_limit_enabled"
    }
}
