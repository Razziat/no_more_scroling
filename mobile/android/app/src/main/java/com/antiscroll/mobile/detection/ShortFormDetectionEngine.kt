package com.antiscroll.mobile.detection

class ShortFormDetectionEngine(
    private val detectors: List<ShortFormDetector> = listOf(
        YouTubeShortsDetector(),
        InstagramReelsDetector(),
    ),
) {
    fun detectEventClass(
        packageName: String,
        eventClassName: String?,
    ): ShortFormDetection? = detectors.firstNotNullOfOrNull { detector ->
        detector.detectEventClass(packageName, eventClassName)
    }

    fun detect(context: DetectionContext): ShortFormDetection? =
        detectors.firstNotNullOfOrNull { detector -> detector.detect(context) }
}
