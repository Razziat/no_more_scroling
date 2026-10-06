package com.antiscroll.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramSessionTrackerTest {
    @Test
    fun `stable counted observations keep delayed service time`() {
        val tracker = tracker()

        tracker.observe(1_000L, InstagramSessionObservation.COUNTED)
        val update = tracker.observe(21_000L, InstagramSessionObservation.COUNTED)

        assertEquals(20_000L, update.activeMillis)
    }

    @Test
    fun `short system window does not reset a session`() {
        val tracker = tracker()

        tracker.observe(1_000L, InstagramSessionObservation.COUNTED)
        tracker.observe(2_000L, InstagramSessionObservation.COUNTED)
        val outside = tracker.observe(3_000L, InstagramSessionObservation.OUTSIDE)
        val resumed = tracker.observe(5_000L, InstagramSessionObservation.COUNTED)

        assertEquals(0L, outside.activeMillis)
        assertEquals(0L, resumed.activeMillis)
    }

    @Test
    fun `usage accumulates across long breaks and triggers the first penalty`() {
        val tracker = tracker()
        val day = "2026-10-02"
        var state = InstagramSessionLimitRules.freshState(day)
        fun observe(at: Long, observation: InstagramSessionObservation) {
            val decision = tracker.observe(at, observation)
            state = InstagramSessionLimitRules.recordActiveTime(
                state, decision.activeMillis, day, at, 86_400_000L,
            ).state
        }

        observe(0L, InstagramSessionObservation.COUNTED)
        observe(120_000L, InstagramSessionObservation.COUNTED)
        observe(120_000L, InstagramSessionObservation.OUTSIDE)
        observe(125_000L, InstagramSessionObservation.OUTSIDE)
        observe(7_320_000L, InstagramSessionObservation.OUTSIDE)
        observe(7_320_000L, InstagramSessionObservation.COUNTED)
        assertEquals(120_000L, state.sessionUsedMillis)
        assertEquals(0, state.violationsToday)

        observe(7_499_000L, InstagramSessionObservation.COUNTED)
        assertEquals(299_000L, state.sessionUsedMillis)
        observe(7_500_000L, InstagramSessionObservation.COUNTED)
        assertEquals(1, state.violationsToday)
        assertEquals(9_300_000L, state.blockedUntilMillis)
    }

    @Test
    fun `brief missing root is recovered when feed returns`() {
        val tracker = tracker()

        tracker.observe(1_000L, InstagramSessionObservation.COUNTED)
        tracker.observe(2_000L, InstagramSessionObservation.COUNTED)
        tracker.observe(3_000L, InstagramSessionObservation.UNKNOWN)
        tracker.observe(4_000L, InstagramSessionObservation.UNKNOWN)
        val resumed = tracker.observe(5_000L, InstagramSessionObservation.COUNTED)

        assertEquals(3_000L, resumed.activeMillis)
    }

    @Test
    fun `long missing root is paused instead of backfilled`() {
        val tracker = tracker(unknownRecoveryMillis = 2_000L)

        tracker.observe(1_000L, InstagramSessionObservation.COUNTED)
        tracker.observe(2_000L, InstagramSessionObservation.UNKNOWN)
        tracker.observe(5_000L, InstagramSessionObservation.UNKNOWN)
        val resumed = tracker.observe(6_000L, InstagramSessionObservation.COUNTED)

        assertEquals(0L, resumed.activeMillis)
    }

    @Test
    fun `screen suspension discards elapsed sleep without resetting session`() {
        val tracker = tracker()

        tracker.observe(1_000L, InstagramSessionObservation.COUNTED)
        tracker.observe(2_000L, InstagramSessionObservation.COUNTED)
        tracker.suspend(3_000L)
        val resumed = tracker.observe(3_603_000L, InstagramSessionObservation.COUNTED)
        val nextTick = tracker.observe(3_604_000L, InstagramSessionObservation.COUNTED)

        assertEquals(0L, resumed.activeMillis)
        assertEquals(1_000L, nextTick.activeMillis)
    }

    private fun tracker(
        unknownRecoveryMillis: Long = 5_000L,
    ) = InstagramSessionTracker(
        unknownRecoveryMillis = unknownRecoveryMillis,
    )
}
