package com.antiscroll.mobile.detection

import java.util.Locale

data class UiNodeSnapshot(
    val resourceId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    val selected: Boolean = false,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val visibleToUser: Boolean = true,
    val children: List<UiNodeSnapshot> = emptyList(),
)

data class DetectionContext(
    val packageName: String,
    val eventClassName: String?,
    val root: UiNodeSnapshot,
)

enum class BlockedSurface {
    YOUTUBE_SHORTS,
    INSTAGRAM_REELS,
}

data class ShortFormDetection(
    val surface: BlockedSurface,
    val matchedRule: String,
)

interface ShortFormDetector {
    fun detectEventClass(packageName: String, eventClassName: String?): ShortFormDetection? = null

    fun detect(context: DetectionContext): ShortFormDetection?
}

internal fun UiNodeSnapshot.walkVisible(): Sequence<UiNodeSnapshot> = sequence {
    if (!visibleToUser) return@sequence
    yield(this@walkVisible)
    children.forEach { child -> yieldAll(child.walkVisible()) }
}

internal fun String?.normalized(): String? = this
    ?.trim()
    ?.lowercase(Locale.ROOT)
    ?.takeIf { it.isNotEmpty() }
