package com.antiscroll.mobile.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class InstagramSessionSurfaceClassifierTest {
    private val classifier = InstagramSessionSurfaceClassifier()

    @Test
    fun `direct inbox activity pauses the timer`() {
        val result = classifier.classify(
            eventClassName = "com.instagram.direct.inbox.DirectInboxActivity",
            root = null,
        )

        assertEquals(InstagramSessionSurface.PAUSED_MESSAGES, result)
    }

    @Test
    fun `a visible profile resource pauses the timer`() {
        val result = classifier.classify(
            eventClassName = null,
            root = UiNodeSnapshot(
                children = listOf(
                    UiNodeSnapshot(resourceId = "com.instagram.android:id/profile_header"),
                ),
            ),
        )

        assertEquals(InstagramSessionSurface.PAUSED_PROFILE, result)
    }

    @Test
    fun `a selected messages label pauses the timer`() {
        val result = classifier.classify(
            eventClassName = null,
            root = UiNodeSnapshot(
                children = listOf(
                    UiNodeSnapshot(
                        contentDescription = "Messages",
                        selected = true,
                    ),
                ),
            ),
        )

        assertEquals(InstagramSessionSurface.PAUSED_MESSAGES, result)
    }

    @Test
    fun `a direct thread recognized from input and send labels pauses the timer`() {
        val result = classifier.classify(
            eventClassName = "android.widget.FrameLayout",
            root = UiNodeSnapshot(
                children = listOf(
                    UiNodeSnapshot(text = "Message…"),
                    UiNodeSnapshot(contentDescription = "Send"),
                ),
            ),
        )

        assertEquals(InstagramSessionSurface.PAUSED_MESSAGES, result)
    }

    @Test
    fun `another user profile is recognized from its counters`() {
        val result = classifier.classify(
            eventClassName = "android.widget.FrameLayout",
            root = UiNodeSnapshot(
                children = listOf(
                    UiNodeSnapshot(text = "82 posts"),
                    UiNodeSnapshot(text = "1,204 followers"),
                    UiNodeSnapshot(text = "318 following"),
                ),
            ),
        )

        assertEquals(InstagramSessionSurface.PAUSED_PROFILE, result)
    }

    @Test
    fun `hidden profile markers are ignored`() {
        val result = classifier.classify(
            eventClassName = null,
            root = UiNodeSnapshot(
                children = listOf(
                    UiNodeSnapshot(
                        resourceId = "com.instagram.android:id/profile_header",
                        visibleToUser = false,
                    ),
                ),
            ),
        )

        assertEquals(InstagramSessionSurface.COUNTED, result)
    }

    @Test
    fun `an unknown Instagram screen is counted conservatively`() {
        val result = classifier.classify(
            eventClassName = "android.widget.FrameLayout",
            root = UiNodeSnapshot(
                children = listOf(
                    UiNodeSnapshot(
                        resourceId = "com.instagram.android:id/feed_recycler_view",
                        scrollable = true,
                    ),
                ),
            ),
        )

        assertEquals(InstagramSessionSurface.COUNTED, result)
    }

    @Test
    fun `home feed buttons do not look like the messages screen`() {
        val result = classifier.classify(
            eventClassName = "android.widget.FrameLayout",
            root = UiNodeSnapshot(
                children = listOf(
                    UiNodeSnapshot(
                        contentDescription = "Home",
                        selected = true,
                    ),
                    UiNodeSnapshot(contentDescription = "Messages"),
                    UiNodeSnapshot(contentDescription = "Send post"),
                    UiNodeSnapshot(
                        resourceId = "com.instagram.android:id/profile_tab_layout",
                    ),
                ),
            ),
        )

        assertEquals(InstagramSessionSurface.COUNTED, result)
    }

    @Test
    fun `following feed suggestions do not look like a profile`() {
        val result = classifier.classify(
            eventClassName = "android.widget.FrameLayout",
            root = UiNodeSnapshot(
                children = listOf(
                    UiNodeSnapshot(text = "Following"),
                    UiNodeSnapshot(text = "Suggested account · 204 followers"),
                    UiNodeSnapshot(
                        resourceId = "com.instagram.android:id/feed_recycler_view",
                    ),
                ),
            ),
        )

        assertEquals(InstagramSessionSurface.COUNTED, result)
    }
}
