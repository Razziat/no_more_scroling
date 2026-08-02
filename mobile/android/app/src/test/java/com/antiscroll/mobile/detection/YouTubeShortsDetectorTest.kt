package com.antiscroll.mobile.detection

import com.antiscroll.mobile.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeShortsDetectorTest {
    private val detector = YouTubeShortsDetector()

    @Test
    fun `detects the Shorts viewer from a strong resource id`() {
        val result = detector.detect(
            context(
                UiNodeSnapshot(
                    children = listOf(
                        UiNodeSnapshot(
                            resourceId = "com.google.android.youtube:id/reel_watch_fragment_root",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(BlockedSurface.YOUTUBE_SHORTS, result?.surface)
    }

    @Test
    fun `detects a selected Shorts tab`() {
        val result = detector.detect(
            context(
                UiNodeSnapshot(
                    children = listOf(
                        UiNodeSnapshot(
                            contentDescription = "Shorts",
                            selected = true,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(BlockedSurface.YOUTUBE_SHORTS, result?.surface)
    }

    @Test
    fun `does not block a regular video just because the Shorts tab exists`() {
        val result = detector.detect(
            context(
                UiNodeSnapshot(
                    children = listOf(
                        UiNodeSnapshot(
                            contentDescription = "Shorts",
                            selected = false,
                        ),
                        UiNodeSnapshot(contentDescription = "Like this video"),
                        UiNodeSnapshot(contentDescription = "Share"),
                    ),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun `ignores a retained Shorts viewer that is not visible`() {
        val result = detector.detect(
            context(
                UiNodeSnapshot(
                    children = listOf(
                        UiNodeSnapshot(
                            resourceId = "com.google.android.youtube:id/reel_watch_fragment_root",
                            visibleToUser = false,
                        ),
                    ),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun `ignores another package`() {
        val result = detector.detect(
            DetectionContext(
                packageName = SettingsRepository.INSTAGRAM_PACKAGE,
                eventClassName = "ShortsActivity",
                root = UiNodeSnapshot(),
            ),
        )

        assertNull(result)
    }

    private fun context(root: UiNodeSnapshot) = DetectionContext(
        packageName = SettingsRepository.YOUTUBE_PACKAGE,
        eventClassName = null,
        root = root,
    )
}
