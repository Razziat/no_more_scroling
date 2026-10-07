package com.antiscroll.mobile.data

import android.content.Context

data class InstagramSessionState(
    val dayKey: String,
    val sessionUsedMillis: Long,
    val violationsToday: Int,
    val blockedUntilMillis: Long,
)

data class InstagramSessionSnapshot(
    val sessionUsedMillis: Long,
    val sessionRemainingMillis: Long,
    val violationsToday: Int,
    val activeLock: PlatformLock?,
)

data class InstagramSessionUpdate(
    val snapshot: InstagramSessionSnapshot,
    val startedLock: PlatformLock?,
)

data class InstagramSessionRuleUpdate(
    val state: InstagramSessionState,
    val startedLockUntilMillis: Long?,
)

object InstagramSessionLimitRules {
    const val SESSION_LIMIT_MILLIS = 5L * 60L * 1_000L
    const val FIRST_LOCK_DURATION_MILLIS = 30L * 60L * 1_000L

    fun normalize(
        state: InstagramSessionState,
        currentDayKey: String,
        nowMillis: Long,
        nextMidnightMillis: Long,
    ): InstagramSessionState {
        if (state.dayKey != currentDayKey) {
            return freshState(currentDayKey)
        }

        // A midnight penalty follows the current phone timezone, including
        // changes while the accessibility service remains alive.
        val adjusted = if (state.violationsToday >= 2 && state.blockedUntilMillis > 0L) {
            state.copy(blockedUntilMillis = nextMidnightMillis)
        } else if (state.blockedUntilMillis > nowMillis) {
            state.copy(blockedUntilMillis = minOf(state.blockedUntilMillis, nextMidnightMillis))
        } else {
            state
        }
        return if (adjusted.blockedUntilMillis in 1L..nowMillis) {
            adjusted.copy(blockedUntilMillis = 0L)
        } else {
            adjusted
        }
    }

    fun recordActiveTime(
        state: InstagramSessionState,
        deltaMillis: Long,
        currentDayKey: String,
        nowMillis: Long,
        nextMidnightMillis: Long,
    ): InstagramSessionRuleUpdate {
        val normalized = normalize(state, currentDayKey, nowMillis, nextMidnightMillis)
        if (deltaMillis <= 0L || normalized.blockedUntilMillis > nowMillis) {
            return InstagramSessionRuleUpdate(normalized, null)
        }

        val usedMillis = (normalized.sessionUsedMillis + deltaMillis)
            .coerceAtMost(SESSION_LIMIT_MILLIS)
        if (usedMillis < SESSION_LIMIT_MILLIS) {
            return InstagramSessionRuleUpdate(
                state = normalized.copy(sessionUsedMillis = usedMillis),
                startedLockUntilMillis = null,
            )
        }

        val violationNumber = (normalized.violationsToday + 1).coerceAtMost(2)
        val blockedUntilMillis = if (violationNumber == 1) {
            (nowMillis + FIRST_LOCK_DURATION_MILLIS).coerceAtMost(nextMidnightMillis)
        } else {
            nextMidnightMillis
        }

        return InstagramSessionRuleUpdate(
            state = normalized.copy(
                sessionUsedMillis = 0L,
                violationsToday = violationNumber,
                blockedUntilMillis = blockedUntilMillis,
            ),
            startedLockUntilMillis = blockedUntilMillis,
        )
    }

    fun freshState(dayKey: String): InstagramSessionState =
        InstagramSessionState(
            dayKey = dayKey,
            sessionUsedMillis = 0L,
            violationsToday = 0,
            blockedUntilMillis = 0L,
        )
}

class InstagramSessionLimitManager(
    context: Context,
) {
    private val calendar = InstagramSessionCalendar()
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    @Synchronized
    fun snapshot(nowMillis: Long = System.currentTimeMillis()): InstagramSessionSnapshot {
        val state = normalizedState(nowMillis)
        return snapshotFrom(state, nowMillis)
    }

    @Synchronized
    fun activeLock(nowMillis: Long = System.currentTimeMillis()): PlatformLock? =
        snapshot(nowMillis).activeLock

    @Synchronized
    fun recordActiveTime(
        deltaMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): InstagramSessionUpdate {
        val day = calendar.dayAt(nowMillis)
        val ruleUpdate = InstagramSessionLimitRules.recordActiveTime(
            state = readState(day.key),
            deltaMillis = deltaMillis,
            currentDayKey = day.key,
            nowMillis = nowMillis,
            nextMidnightMillis = day.nextMidnightMillis,
        )
        writeState(ruleUpdate.state)

        val startedLock = ruleUpdate.startedLockUntilMillis?.let { blockedUntilMillis ->
            PlatformLock(
                packageName = INSTAGRAM_PACKAGE,
                blockedUntilMillis = blockedUntilMillis,
                remainingMillis = (blockedUntilMillis - nowMillis).coerceAtLeast(0L),
            )
        }
        return InstagramSessionUpdate(
            snapshot = snapshotFrom(ruleUpdate.state, nowMillis),
            startedLock = startedLock,
        )
    }

    @Synchronized
    fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun normalizedState(nowMillis: Long): InstagramSessionState {
        val day = calendar.dayAt(nowMillis)
        val stored = readState(day.key)
        val normalized = InstagramSessionLimitRules.normalize(
            state = stored,
            currentDayKey = day.key,
            nowMillis = nowMillis,
            nextMidnightMillis = day.nextMidnightMillis,
        )
        if (normalized != stored) {
            writeState(normalized)
        }
        return normalized
    }

    private fun snapshotFrom(
        state: InstagramSessionState,
        nowMillis: Long,
    ): InstagramSessionSnapshot {
        val activeLock = if (state.blockedUntilMillis > nowMillis) {
            PlatformLock(
                packageName = INSTAGRAM_PACKAGE,
                blockedUntilMillis = state.blockedUntilMillis,
                remainingMillis = state.blockedUntilMillis - nowMillis,
            )
        } else {
            null
        }

        return InstagramSessionSnapshot(
            sessionUsedMillis = state.sessionUsedMillis,
            sessionRemainingMillis =
                (InstagramSessionLimitRules.SESSION_LIMIT_MILLIS - state.sessionUsedMillis)
                    .coerceAtLeast(0L),
            violationsToday = state.violationsToday,
            activeLock = activeLock,
        )
    }

    private fun readState(currentDayKey: String): InstagramSessionState {
        val storedDayKey = preferences.getString(KEY_DAY, null)
        return if (storedDayKey == null) {
            InstagramSessionLimitRules.freshState(currentDayKey)
        } else {
            InstagramSessionState(
                dayKey = storedDayKey,
                sessionUsedMillis = preferences.getLong(KEY_SESSION_USED, 0L),
                violationsToday = preferences.getInt(KEY_VIOLATIONS, 0).coerceIn(0, 2),
                blockedUntilMillis = preferences.getLong(KEY_BLOCKED_UNTIL, 0L),
            )
        }
    }

    private fun writeState(state: InstagramSessionState) {
        preferences.edit()
            .putString(KEY_DAY, state.dayKey)
            .putLong(KEY_SESSION_USED, state.sessionUsedMillis)
            .putInt(KEY_VIOLATIONS, state.violationsToday)
            .putLong(KEY_BLOCKED_UNTIL, state.blockedUntilMillis)
            .apply()
    }

    companion object {
        const val INSTAGRAM_PACKAGE = "com.instagram.android"

        private const val PREFERENCES_NAME = "anti_scroll_instagram_sessions"
        private const val KEY_DAY = "day"
        private const val KEY_SESSION_USED = "session_used"
        private const val KEY_VIOLATIONS = "violations"
        private const val KEY_BLOCKED_UNTIL = "blocked_until"
    }
}
