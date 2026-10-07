package com.antiscroll.mobile.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramSessionCalendarTest {
    @Test
    fun `daily reset happens at London midnight rather than Paris midnight`() {
        val calendar = InstagramSessionCalendar { ZoneId.of("Europe/London") }
        val state = InstagramSessionLimitRules.freshState("2026-10-07").copy(
            violationsToday = 2,
            blockedUntilMillis = millis("2026-10-07T23:00:00Z"),
        )
        fun normalized(at: String): InstagramSessionState {
            val now = millis(at)
            val day = calendar.dayAt(now)
            return InstagramSessionLimitRules.normalize(
                state, day.key, now, day.nextMidnightMillis,
            )
        }

        // Paris is already on October 8, but London is still on October 7.
        assertEquals(state, normalized("2026-10-07T22:30:00Z"))
        assertEquals(
            InstagramSessionLimitRules.freshState("2026-10-08"),
            normalized("2026-10-07T23:00:00Z"),
        )
    }

    @Test
    fun `a running calendar follows timezone changes and adjusts midnight penalties`() {
        var zone = ZoneId.of("Europe/Paris")
        val calendar = InstagramSessionCalendar { zone }
        val now = millis("2026-10-07T21:30:00Z")
        val parisDay = calendar.dayAt(now)
        val state = InstagramSessionLimitRules.freshState(parisDay.key).copy(
            violationsToday = 2,
            blockedUntilMillis = parisDay.nextMidnightMillis,
        )
        assertEquals(millis("2026-10-07T22:00:00Z"), state.blockedUntilMillis)

        zone = ZoneId.of("Europe/London")
        // Also recover correctly if the old Paris deadline has already passed.
        val later = millis("2026-10-07T22:15:00Z")
        val londonDay = calendar.dayAt(later)
        val adjusted = InstagramSessionLimitRules.normalize(
            state, londonDay.key, later, londonDay.nextMidnightMillis,
        )
        assertEquals(2, adjusted.violationsToday)
        assertEquals(millis("2026-10-07T23:00:00Z"), adjusted.blockedUntilMillis)

        zone = ZoneId.of("Europe/Paris")
        val adjustedBack = InstagramSessionLimitRules.normalize(
            adjusted, parisDay.key, now, calendar.dayAt(now).nextMidnightMillis,
        )
        assertEquals(state, adjustedBack)
    }

    @Test
    fun `timezone changes preserve consumed time when the local date stays the same`() {
        var zone = ZoneId.of("Europe/Paris")
        val calendar = InstagramSessionCalendar { zone }
        val now = millis("2026-10-07T12:00:00Z")
        val state = InstagramSessionLimitRules.freshState(calendar.dayAt(now).key)
            .copy(sessionUsedMillis = 120_000L, violationsToday = 1)
        zone = ZoneId.of("Europe/London")
        val day = calendar.dayAt(now)
        assertEquals(
            state,
            InstagramSessionLimitRules.normalize(state, day.key, now, day.nextMidnightMillis),
        )
    }

    @Test
    fun `a first thirty minute penalty is never extended by a timezone change`() {
        val now = millis("2026-10-07T21:50:00Z")
        val day = InstagramSessionCalendar { ZoneId.of("Europe/London") }.dayAt(now)
        val state = InstagramSessionLimitRules.freshState(day.key).copy(
            violationsToday = 1,
            blockedUntilMillis = millis("2026-10-07T22:00:00Z"),
        )
        assertEquals(
            state,
            InstagramSessionLimitRules.normalize(state, day.key, now, day.nextMidnightMillis),
        )
    }

    @Test
    fun `next midnight follows daylight saving transitions`() {
        val calendar = InstagramSessionCalendar { ZoneId.of("Europe/London") }
        val spring = millis("2026-03-29T00:00:00Z")
        val autumn = millis("2026-10-24T23:00:00Z")
        assertEquals(23L * 3_600_000L, calendar.dayAt(spring).nextMidnightMillis - spring)
        assertEquals(25L * 3_600_000L, calendar.dayAt(autumn).nextMidnightMillis - autumn)
    }

    private fun millis(instant: String): Long = Instant.parse(instant).toEpochMilli()
}
