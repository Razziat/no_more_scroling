package com.antiscroll.mobile.data

import android.content.Context

data class PlatformLock(
    val packageName: String,
    val blockedUntilMillis: Long,
    val remainingMillis: Long,
)

object PunitiveLockRules {
    const val PUNISHMENT_DURATION_MS = 30L * 60L * 1_000L

    fun blockedUntil(
        currentBlockedUntilMillis: Long,
        nowMillis: Long,
        durationMillis: Long = PUNISHMENT_DURATION_MS,
    ): Long = if (currentBlockedUntilMillis > nowMillis) {
        currentBlockedUntilMillis
    } else {
        nowMillis + durationMillis
    }

    fun remainingMillis(blockedUntilMillis: Long, nowMillis: Long): Long =
        (blockedUntilMillis - nowMillis).coerceAtLeast(0L)
}

class PunitiveLockManager(
    context: Context,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun activate(packageName: String): PlatformLock? {
        val key = keyFor(packageName) ?: return null
        val now = currentTimeMillis()
        val currentBlockedUntil = preferences.getLong(key, 0L)
        val blockedUntil = PunitiveLockRules.blockedUntil(
            currentBlockedUntilMillis = currentBlockedUntil,
            nowMillis = now,
        )

        if (blockedUntil != currentBlockedUntil) {
            preferences.edit().putLong(key, blockedUntil).apply()
        }

        return PlatformLock(
            packageName = packageName,
            blockedUntilMillis = blockedUntil,
            remainingMillis = PunitiveLockRules.remainingMillis(blockedUntil, now),
        )
    }

    fun activeLock(packageName: String): PlatformLock? {
        val key = keyFor(packageName) ?: return null
        val now = currentTimeMillis()
        val blockedUntil = preferences.getLong(key, 0L)
        val remaining = PunitiveLockRules.remainingMillis(blockedUntil, now)

        if (remaining <= 0L) {
            if (blockedUntil != 0L) preferences.edit().remove(key).apply()
            return null
        }

        return PlatformLock(
            packageName = packageName,
            blockedUntilMillis = blockedUntil,
            remainingMillis = remaining,
        )
    }

    fun clear(packageName: String) {
        keyFor(packageName)?.let { key -> preferences.edit().remove(key).apply() }
    }

    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun keyFor(packageName: String): String? = when (packageName) {
        SettingsRepository.YOUTUBE_PACKAGE -> KEY_YOUTUBE_BLOCKED_UNTIL
        SettingsRepository.INSTAGRAM_PACKAGE -> KEY_INSTAGRAM_BLOCKED_UNTIL
        else -> null
    }

    private companion object {
        const val PREFERENCES_NAME = "anti_scroll_punitive_locks"
        const val KEY_YOUTUBE_BLOCKED_UNTIL = "youtube_blocked_until"
        const val KEY_INSTAGRAM_BLOCKED_UNTIL = "instagram_blocked_until"
    }
}
