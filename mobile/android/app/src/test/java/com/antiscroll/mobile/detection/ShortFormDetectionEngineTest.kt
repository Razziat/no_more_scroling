package com.antiscroll.mobile.detection

import com.antiscroll.mobile.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortFormDetectionEngineTest {
    private val engine = ShortFormDetectionEngine()

    @Test
    fun `a deferred scan of the home screen cannot reuse a previous viewer event`() {
        val packageName = SettingsRepository.INSTAGRAM_PACKAGE
        // The old event is valid, but the window has changed before the scan.
        assertEquals(
            BlockedSurface.INSTAGRAM_REELS,
            engine.detectEventClass(packageName, "com.instagram.clips.viewer.ClipsViewerActivity")?.surface,
        )
        val home = UiNodeSnapshot(
            className = "android.widget.FrameLayout",
            children = listOf(UiNodeSnapshot(text = "Reels", selected = false)),
        )
        assertNull(engine.detectCurrentWindow(packageName, home))
    }

    @Test
    fun `current window markers still identify a viewer`() {
        val viewer = UiNodeSnapshot(children = listOf(
            UiNodeSnapshot(resourceId = "com.google.android.youtube:id/reel_watch_player"),
        ))
        assertEquals(
            BlockedSurface.YOUTUBE_SHORTS,
            engine.detectCurrentWindow(SettingsRepository.YOUTUBE_PACKAGE, viewer)?.surface,
        )
    }

    @Test
    fun `a hidden root cannot trigger a block from its class name`() {
        assertNull(engine.detectCurrentWindow(
            SettingsRepository.INSTAGRAM_PACKAGE,
            UiNodeSnapshot(
                className = "com.instagram.clips.viewer.ClipsViewerActivity",
                visibleToUser = false,
            ),
        ))
    }

    @Test
    fun `a visible current viewer class remains supported`() {
        assertEquals(
            BlockedSurface.INSTAGRAM_REELS,
            engine.detectCurrentWindow(
                SettingsRepository.INSTAGRAM_PACKAGE,
                UiNodeSnapshot(className = "com.instagram.clips.viewer.ClipsViewerActivity"),
            )?.surface,
        )
    }

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
