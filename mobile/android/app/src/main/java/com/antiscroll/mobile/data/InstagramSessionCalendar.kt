package com.antiscroll.mobile.data

import java.time.Instant
import java.time.ZoneId

internal data class InstagramSessionDay(
    val key: String,
    val nextMidnightMillis: Long,
)

internal class InstagramSessionCalendar(
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun dayAt(nowMillis: Long): InstagramSessionDay {
        val zone = zoneProvider()
        val date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return InstagramSessionDay(
            key = date.toString(),
            nextMidnightMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}
