package com.antiscroll.mobile.data

enum class InstagramSessionObservation {
    COUNTED,
    PAUSED,
    OUTSIDE,
    UNKNOWN,
}

data class InstagramSessionTrackingDecision(
    val activeMillis: Long = 0L,
    val shouldResetSession: Boolean = false,
)

/**
 * Turns noisy accessibility observations into stable session decisions.
 *
 * Android can temporarily expose no active root, or a system window, while an
 * application redraws. UNKNOWN observations therefore keep a short recoverable
 * interval, while OUTSIDE must remain stable before a real session reset.
 */
class InstagramSessionTracker(
    private val exitConfirmationMillis: Long = DEFAULT_EXIT_CONFIRMATION_MILLIS,
    private val unknownRecoveryMillis: Long = DEFAULT_UNKNOWN_RECOVERY_MILLIS,
) {
    private var lastObservationAtMillis: Long? = null
    private var previousObservation = InstagramSessionObservation.UNKNOWN
    private var outsideSinceMillis: Long? = null
    private var outsideResetIssued = false
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
            InstagramSessionObservation.OUTSIDE -> onOutside(nowElapsedMillis)
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
        outsideSinceMillis = null
        outsideResetIssued = false
        clearUnknownRecovery()
    }

    fun reset(nowElapsedMillis: Long) {
        lastObservationAtMillis = nowElapsedMillis
        previousObservation = InstagramSessionObservation.UNKNOWN
        outsideSinceMillis = null
        outsideResetIssued = false
        clearUnknownRecovery()
    }

    private fun onCounted(
        nowElapsedMillis: Long,
        deltaMillis: Long,
    ): InstagramSessionTrackingDecision {
        outsideSinceMillis = null
        outsideResetIssued = false

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
        outsideSinceMillis = null
        outsideResetIssued = false
        clearUnknownRecovery()
        return InstagramSessionTrackingDecision()
    }

    private fun onOutside(nowElapsedMillis: Long): InstagramSessionTrackingDecision {
        clearUnknownRecovery()

        if (previousObservation != InstagramSessionObservation.OUTSIDE ||
            outsideSinceMillis == null
        ) {
            outsideSinceMillis = nowElapsedMillis
            outsideResetIssued = false
        }

        val outsideSince = outsideSinceMillis ?: nowElapsedMillis
        val shouldReset = !outsideResetIssued &&
            nowElapsedMillis - outsideSince >= exitConfirmationMillis
        if (shouldReset) outsideResetIssued = true

        previousObservation = InstagramSessionObservation.OUTSIDE
        return InstagramSessionTrackingDecision(shouldResetSession = shouldReset)
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
        const val DEFAULT_EXIT_CONFIRMATION_MILLIS = 5_000L
        const val DEFAULT_UNKNOWN_RECOVERY_MILLIS = 5_000L
    }
}
