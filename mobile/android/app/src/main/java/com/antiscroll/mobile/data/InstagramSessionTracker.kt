package com.antiscroll.mobile.data

enum class InstagramSessionObservation {
    COUNTED,
    PAUSED,
    OUTSIDE,
    UNKNOWN,
}

data class InstagramSessionTrackingDecision(
    val activeMillis: Long = 0L,
)

/**
 * Turns noisy accessibility observations into stable session decisions.
 *
 * Android can temporarily expose no active root, or a system window, while an
 * application redraws. UNKNOWN observations therefore keep a short recoverable
 * interval. Leaving Instagram pauses counting without clearing accumulated time.
 */
class InstagramSessionTracker(
    private val unknownRecoveryMillis: Long = DEFAULT_UNKNOWN_RECOVERY_MILLIS,
) {
    private var lastObservationAtMillis: Long? = null
    private var previousObservation = InstagramSessionObservation.UNKNOWN
    private var unknownSinceMillis: Long? = null
    private var recoverableUnknownMillis = 0L

    fun observe(
        nowElapsedMillis: Long,
        observation: InstagramSessionObservation,
    ): InstagramSessionTrackingDecision {
        val previousAtMillis = lastObservationAtMillis
        val deltaMillis = if (
            previousAtMillis == null || nowElapsedMillis < previousAtMillis
        ) {
            0L
        } else {
            nowElapsedMillis - previousAtMillis
        }
        lastObservationAtMillis = nowElapsedMillis

        return when (observation) {
            InstagramSessionObservation.COUNTED -> onCounted(nowElapsedMillis, deltaMillis)
            InstagramSessionObservation.PAUSED -> onPaused()
            InstagramSessionObservation.OUTSIDE -> onPaused()
            InstagramSessionObservation.UNKNOWN -> onUnknown(nowElapsedMillis, deltaMillis)
        }
    }

    /**
     * Drops the elapsed gap without clearing the persisted session. This is used
     * for screen-off/on transitions where elapsedRealtime keeps advancing even
     * though Instagram must not be counted.
     */
    fun suspend(nowElapsedMillis: Long) {
        lastObservationAtMillis = nowElapsedMillis
        previousObservation = InstagramSessionObservation.PAUSED
        clearUnknownRecovery()
    }

    fun reset(nowElapsedMillis: Long) {
        lastObservationAtMillis = nowElapsedMillis
        previousObservation = InstagramSessionObservation.UNKNOWN
        clearUnknownRecovery()
    }

    private fun onCounted(
        nowElapsedMillis: Long,
        deltaMillis: Long,
    ): InstagramSessionTrackingDecision {

        val activeMillis = when (previousObservation) {
            InstagramSessionObservation.COUNTED -> deltaMillis
            InstagramSessionObservation.UNKNOWN -> recoverUnknownInterval(
                nowElapsedMillis = nowElapsedMillis,
                finalDeltaMillis = deltaMillis,
            )

            else -> 0L
        }

        previousObservation = InstagramSessionObservation.COUNTED
        clearUnknownRecovery()
        return InstagramSessionTrackingDecision(activeMillis = activeMillis)
    }

    private fun onPaused(): InstagramSessionTrackingDecision {
        previousObservation = InstagramSessionObservation.PAUSED
        clearUnknownRecovery()
        return InstagramSessionTrackingDecision()
    }

    private fun onUnknown(
        nowElapsedMillis: Long,
        deltaMillis: Long,
    ): InstagramSessionTrackingDecision {
        when (previousObservation) {
            InstagramSessionObservation.COUNTED -> {
                unknownSinceMillis = nowElapsedMillis
                recoverableUnknownMillis = deltaMillis.coerceAtMost(unknownRecoveryMillis)
            }

            InstagramSessionObservation.UNKNOWN -> {
                val unknownSince = unknownSinceMillis
                if (unknownSince != null &&
                    nowElapsedMillis - unknownSince <= unknownRecoveryMillis &&
                    recoverableUnknownMillis > 0L
                ) {
                    recoverableUnknownMillis =
                        (recoverableUnknownMillis + deltaMillis)
                            .coerceAtMost(unknownRecoveryMillis)
                } else {
                    clearUnknownRecovery()
                }
            }

            else -> clearUnknownRecovery()
        }

        previousObservation = InstagramSessionObservation.UNKNOWN
        return InstagramSessionTrackingDecision()
    }

    private fun recoverUnknownInterval(
        nowElapsedMillis: Long,
        finalDeltaMillis: Long,
    ): Long {
        val unknownSince = unknownSinceMillis ?: return 0L
        if (nowElapsedMillis - unknownSince > unknownRecoveryMillis ||
            recoverableUnknownMillis <= 0L
        ) {
            return 0L
        }

        return (recoverableUnknownMillis + finalDeltaMillis)
            .coerceAtMost(unknownRecoveryMillis)
    }

    private fun clearUnknownRecovery() {
        unknownSinceMillis = null
        recoverableUnknownMillis = 0L
    }

    companion object {
        const val DEFAULT_UNKNOWN_RECOVERY_MILLIS = 5_000L
    }
}
