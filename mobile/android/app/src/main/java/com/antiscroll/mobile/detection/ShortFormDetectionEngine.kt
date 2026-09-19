package com.antiscroll.mobile.detection

class ShortFormDetectionEngine(
    private val detectors: List<ShortFormDetector> = listOf(
        YouTubeShortsDetector(),
        InstagramReelsDetector(),
    ),
) {
    /** Deferred scans must only use evidence from the window captured now. */
    fun detectCurrentWindow(packageName: String, root: UiNodeSnapshot): ShortFormDetection? =
        if (root.visibleToUser) detect(DetectionContext(packageName, root.className, root))
        else null

    fun detectEventClass(
        packageName: String,
        eventClassName: String?,
    ): ShortFormDetection? = detectors.firstNotNullOfOrNull { detector ->
        detector.detectEventClass(packageName, eventClassName)
    }

    fun detect(context: DetectionContext): ShortFormDetection? =
        detectors.firstNotNullOfOrNull { detector -> detector.detect(context) }
}
