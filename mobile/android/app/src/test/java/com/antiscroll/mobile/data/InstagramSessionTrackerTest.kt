package com.antiscroll.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

        assertFalse(outside.shouldResetSession)
        assertFalse(resumed.shouldResetSession)
        assertEquals(0L, resumed.activeMillis)
    }

    @Test
    fun `stable exit resets the current session once`() {
        val tracker = tracker()

        tracker.observe(1_000L, InstagramSessionObservation.COUNTED)
        tracker.observe(2_000L, InstagramSessionObservation.OUTSIDE)
        val beforeGrace = tracker.observe(6_999L, InstagramSessionObservation.OUTSIDE)
        val atGrace = tracker.observe(7_000L, InstagramSessionObservation.OUTSIDE)
        val later = tracker.observe(8_000L, InstagramSessionObservation.OUTSIDE)

        assertFalse(beforeGrace.shouldResetSession)
        assertTrue(atGrace.shouldResetSession)
        assertFalse(later.shouldResetSession)
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
        assertFalse(resumed.shouldResetSession)
        assertEquals(1_000L, nextTick.activeMillis)
    }

    private fun tracker(
        unknownRecoveryMillis: Long = 5_000L,
    ) = InstagramSessionTracker(
        exitConfirmationMillis = 5_000L,
        unknownRecoveryMillis = unknownRecoveryMillis,
    )
}
