package com.antiscroll.mobile.detection

import com.antiscroll.mobile.data.SettingsRepository

class YouTubeShortsDetector : ShortFormDetector {
    override fun detectEventClass(
        packageName: String,
        eventClassName: String?,
    ): ShortFormDetection? {
        if (packageName != SettingsRepository.YOUTUBE_PACKAGE) return null

        val normalizedClassName = eventClassName.normalized().orEmpty()
        return if (EVENT_CLASS_MARKERS.any { marker -> normalizedClassName.contains(marker) }) {
            detection("event-class")
        } else {
            null
        }
    }

    override fun detect(context: DetectionContext): ShortFormDetection? {
        if (context.packageName != SettingsRepository.YOUTUBE_PACKAGE) return null

        detectEventClass(context.packageName, context.eventClassName)?.let { return it }

        var hasExplicitPlayerLabel = false
        var shortsTabIsSelected = false

        for (node in context.root.walkVisible().take(MAX_INSPECTED_NODES)) {
            val resourceId = node.resourceId.normalized().orEmpty()
            if (STRONG_RESOURCE_MARKERS.any { marker -> resourceId.contains(marker) }) {
                return detection("resource-id")
            }

            val text = node.text.normalized()
            val contentDescription = node.contentDescription.normalized()
            if (!hasExplicitPlayerLabel) {
                hasExplicitPlayerLabel = isExplicitPlayerLabel(text) ||
                    isExplicitPlayerLabel(contentDescription)
            }
            if (!shortsTabIsSelected && node.selected) {
                shortsTabIsSelected = text?.let(::isShortsTabLabel) == true ||
                    contentDescription?.let(::isShortsTabLabel) == true
            }
        }

        if (hasExplicitPlayerLabel) {
            return detection("player-label")
        }

        if (shortsTabIsSelected) {
            return detection("selected-tab")
        }

        return null
    }

    private fun isShortsTabLabel(label: String): Boolean =
        label == "shorts" ||
            label == "shorts tab" ||
            label == "onglet shorts" ||
            label == "shorts, selected" ||
            label == "shorts, sélectionné"

    private fun isExplicitPlayerLabel(label: String?): Boolean =
        label != null && EXPLICIT_PLAYER_LABELS.any { marker -> label.contains(marker) }

    private fun detection(rule: String) = ShortFormDetection(
        surface = BlockedSurface.YOUTUBE_SHORTS,
        matchedRule = rule,
    )

    private companion object {
        const val MAX_INSPECTED_NODES = 350

        val EVENT_CLASS_MARKERS = listOf(
            ".reel.",
            "shortsactivity",
            "shortswatch",
        )

        val STRONG_RESOURCE_MARKERS = listOf(
            ":id/reel_watch_fragment",
            ":id/reel_watch_player",
            ":id/reel_recycler",
            ":id/shorts_player",
            ":id/shorts_watch",
            ":id/shorts_video",
        )

        val EXPLICIT_PLAYER_LABELS = listOf(
            "shorts player",
            "shorts video player",
            "lecteur shorts",
            "lecteur vidéo shorts",
        )
    }
}
