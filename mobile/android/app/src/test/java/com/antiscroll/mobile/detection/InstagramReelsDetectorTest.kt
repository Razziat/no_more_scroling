package com.antiscroll.mobile.detection

import com.antiscroll.mobile.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramReelsDetectorTest {
    private val detector = InstagramReelsDetector()

    @Test
    fun `detects the Reels viewer from the clips pager id`() {
        val result = detector.detect(
            context(
                UiNodeSnapshot(
                    children = listOf(
                        UiNodeSnapshot(
                            resourceId = "com.instagram.android:id/clips_viewer_view_pager",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(BlockedSurface.INSTAGRAM_REELS, result?.surface)
    }

    @Test
    fun `detects a selected Reels tab`() {
        val result = detector.detect(
            context(
                UiNodeSnapshot(
                    children = listOf(
                        UiNodeSnapshot(
                            text = "Reels",
                            selected = true,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(BlockedSurface.INSTAGRAM_REELS, result?.surface)
    }

    @Test
    fun `does not block the feed because an unselected Reels tab exists`() {
        val result = detector.detect(
            context(
                UiNodeSnapshot(
                    children = listOf(
                        UiNodeSnapshot(
                            contentDescription = "Reels",
                            selected = false,
                        ),
                        UiNodeSnapshot(contentDescription = "Like"),
                        UiNodeSnapshot(contentDescription = "Comment"),
                    ),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun `ignores a retained Reels viewer that is not visible`() {
        val result = detector.detect(
            context(
                UiNodeSnapshot(
                    children = listOf(
                        UiNodeSnapshot(
                            resourceId = "com.instagram.android:id/clips_viewer_view_pager",
                            visibleToUser = false,
                        ),
                    ),
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun `detects the dedicated clips viewer event class`() {
        val result = detector.detect(
            DetectionContext(
                packageName = SettingsRepository.INSTAGRAM_PACKAGE,
                eventClassName = "com.instagram.clips.viewer.ClipsViewerActivity",
                root = UiNodeSnapshot(),
            ),
        )

        assertEquals(BlockedSurface.INSTAGRAM_REELS, result?.surface)
    }

    private fun context(root: UiNodeSnapshot) = DetectionContext(
        packageName = SettingsRepository.INSTAGRAM_PACKAGE,
        eventClassName = null,
        root = root,
    )
}
