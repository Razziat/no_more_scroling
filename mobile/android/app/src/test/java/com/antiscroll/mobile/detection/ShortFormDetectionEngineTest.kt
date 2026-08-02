package com.antiscroll.mobile.detection

import com.antiscroll.mobile.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortFormDetectionEngineTest {
    private val engine = ShortFormDetectionEngine()

    @Test
    fun `detects YouTube event class without a UI tree`() {
        val result = engine.detectEventClass(
            packageName = SettingsRepository.YOUTUBE_PACKAGE,
            eventClassName = "com.google.android.youtube.shorts.ShortswatchActivity",
        )

        assertEquals(BlockedSurface.YOUTUBE_SHORTS, result?.surface)
        assertEquals("event-class", result?.matchedRule)
    }

    @Test
    fun `detects Instagram event class without a UI tree`() {
        val result = engine.detectEventClass(
            packageName = SettingsRepository.INSTAGRAM_PACKAGE,
            eventClassName = "com.instagram.clips.viewer.ClipsViewerActivity",
        )

        assertEquals(BlockedSurface.INSTAGRAM_REELS, result?.surface)
        assertEquals("event-class", result?.matchedRule)
    }

    @Test
    fun `does not treat a regular activity as short form content`() {
        val result = engine.detectEventClass(
            packageName = SettingsRepository.YOUTUBE_PACKAGE,
            eventClassName = "com.google.android.youtube.HomeActivity",
        )

        assertNull(result)
    }
}
