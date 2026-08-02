package com.antiscroll.mobile.detection

import com.antiscroll.mobile.data.SettingsRepository

class InstagramReelsDetector : ShortFormDetector {
    override fun detectEventClass(
        packageName: String,
        eventClassName: String?,
    ): ShortFormDetection? {
        if (packageName != SettingsRepository.INSTAGRAM_PACKAGE) return null

        val normalizedClassName = eventClassName.normalized().orEmpty()
        return if (EVENT_CLASS_MARKERS.any { marker -> normalizedClassName.contains(marker) }) {
            detection("event-class")
        } else {
            null
        }
    }

    override fun detect(context: DetectionContext): ShortFormDetection? {
        if (context.packageName != SettingsRepository.INSTAGRAM_PACKAGE) return null

        detectEventClass(context.packageName, context.eventClassName)?.let { return it }

        var hasExplicitViewerLabel = false
        var reelsTabIsSelected = false

        for (node in context.root.walkVisible().take(MAX_INSPECTED_NODES)) {
            val resourceId = node.resourceId.normalized().orEmpty()
            if (STRONG_RESOURCE_MARKERS.any { marker -> resourceId.contains(marker) }) {
                return detection("resource-id")
            }

            val text = node.text.normalized()
            val contentDescription = node.contentDescription.normalized()
            if (!hasExplicitViewerLabel) {
                hasExplicitViewerLabel = isExplicitViewerLabel(text) ||
                    isExplicitViewerLabel(contentDescription)
            }
            if (!reelsTabIsSelected && node.selected) {
                reelsTabIsSelected = text?.let(::isReelsTabLabel) == true ||
                    contentDescription?.let(::isReelsTabLabel) == true
            }
        }

        if (hasExplicitViewerLabel) {
            return detection("viewer-label")
        }

        if (reelsTabIsSelected) {
            return detection("selected-tab")
        }

        return null
    }

    private fun isReelsTabLabel(label: String): Boolean =
        label == "reels" ||
            label == "reels tab" ||
            label == "onglet reels" ||
            label == "reels, selected" ||
            label == "reels, sélectionné"

    private fun isExplicitViewerLabel(label: String?): Boolean =
        label != null && EXPLICIT_VIEWER_LABELS.any { marker -> label.contains(marker) }

    private fun detection(rule: String) = ShortFormDetection(
        surface = BlockedSurface.INSTAGRAM_REELS,
        matchedRule = rule,
    )

    private companion object {
        const val MAX_INSPECTED_NODES = 350

        val EVENT_CLASS_MARKERS = listOf(
            ".clips.viewer.",
            "clipsvieweractivity",
            "reelsvieweractivity",
        )

        val STRONG_RESOURCE_MARKERS = listOf(
            ":id/clips_viewer",
            ":id/clips_video",
            ":id/clips_media_viewer",
            ":id/clips_viewer_view_pager",
            ":id/reels_viewer",
        )

        val EXPLICIT_VIEWER_LABELS = listOf(
            "reels viewer",
            "reels video player",
            "lecteur reels",
            "lecteur vidéo reels",
        )
    }
}
