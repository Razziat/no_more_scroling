package com.antiscroll.mobile.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.antiscroll.mobile.blocking.BlockCoordinator
import com.antiscroll.mobile.blocking.BlockOverlayController
import com.antiscroll.mobile.data.InstagramSessionLimitManager
import com.antiscroll.mobile.data.PlatformLock
import com.antiscroll.mobile.data.PunitiveLockManager
import com.antiscroll.mobile.data.SettingsRepository
import com.antiscroll.mobile.detection.DetectionContext
import com.antiscroll.mobile.detection.InstagramSessionSurface
import com.antiscroll.mobile.detection.InstagramSessionSurfaceClassifier
import com.antiscroll.mobile.detection.ShortFormDetectionEngine
import java.util.Locale

class ShortFormBlockerService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectionEngine = ShortFormDetectionEngine()
    private val instagramSurfaceClassifier = InstagramSessionSurfaceClassifier()
    private val awaitingExitSinceByPackage = mutableMapOf<String, Long>()
    private val lastBlockedOpenTimeByPackage = mutableMapOf<String, Long>()
    private val lastPunitiveOverlayAtByPackage = mutableMapOf<String, Long>()
    private val lastScanAtByPackage = mutableMapOf<String, Long>()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var punitiveLockManager: PunitiveLockManager
    private lateinit var instagramSessionLimitManager: InstagramSessionLimitManager
    private lateinit var blockCoordinator: BlockCoordinator
    private lateinit var powerManager: PowerManager

    private var pendingScan: Runnable? = null
    private var pendingPackageName: String? = null
    private var pendingEventClassName: String? = null
    private var pendingEventType: Int? = null
    private var pendingEventTime: Long? = null
    private var pendingExplicitSurfaceOpen = false
    private var pendingRetriesRemaining = 0
    private var instagramSessionSurface = InstagramSessionSurface.COUNTED
    private var instagramWasForeground = false
    private var lastSessionTickElapsedMillis = 0L
    private var lastSurfaceRefreshElapsedMillis = 0L

    private val sessionTicker = object : Runnable {
        override fun run() {
            try {
                updateInstagramSession()
            } catch (_: Exception) {
                // A transient accessibility failure must not stop all future ticks.
            } finally {
                mainHandler.postDelayed(this, SESSION_TICK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        punitiveLockManager = PunitiveLockManager(this)
        instagramSessionLimitManager = InstagramSessionLimitManager(this)
        powerManager = getSystemService(PowerManager::class.java)
        blockCoordinator = BlockCoordinator(
            service = this,
            overlayController = BlockOverlayController(this),
        )
        lastSessionTickElapsedMillis = SystemClock.elapsedRealtime()
        mainHandler.post(sessionTicker)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        if (packageName == SettingsRepository.INSTAGRAM_PACKAGE &&
            settingsRepository.instagramSessionLimitEnabled
        ) {
            instagramSurfaceClassifier
                .classifyEventClass(event.className?.toString())
                ?.let { instagramSessionSurface = it }
        }

        val activeLock = activeLockFor(packageName)
        if (activeLock != null) {
            if (pendingPackageName == packageName) cancelPendingScan()
            enforcePunitiveLock(activeLock)
            return
        }
        lastPunitiveOverlayAtByPackage.remove(packageName)

        if (!settingsRepository.shouldMonitorPackage(packageName)) {
            if (pendingPackageName == packageName) cancelPendingScan()
            return
        }

        if (pendingPackageName != null && pendingPackageName != packageName) {
            cancelPendingScan()
        }

        val retryCount = when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> NAVIGATION_RETRY_COUNT

            else -> 0
        }
        val explicitSurfaceOpen = isExplicitSurfaceOpenEvent(event, packageName)

        if (pendingScan != null) {
            pendingEventClassName = event.className?.toString() ?: pendingEventClassName
            pendingRetriesRemaining = maxOf(pendingRetriesRemaining, retryCount)

            // Keep an explicit Shorts/Reels selection over the content-change
            // events emitted while that surface is rendering.
            if (explicitSurfaceOpen) {
                pendingExplicitSurfaceOpen = true
                pendingEventType = event.eventType
                pendingEventTime = event.eventTime
            } else if (!pendingExplicitSurfaceOpen) {
                pendingEventType = event.eventType
                pendingEventTime = event.eventTime
            }
            return
        }

        scheduleScan(
            packageName = packageName,
            eventClassName = event.className?.toString(),
            eventType = event.eventType,
            eventTime = event.eventTime,
            explicitSurfaceOpen = explicitSurfaceOpen,
            retriesRemaining = retryCount,
            delayMs = INITIAL_SCAN_DELAY_MS,
        )
    }

    private fun scheduleScan(
        packageName: String,
        eventClassName: String?,
        eventType: Int,
        eventTime: Long,
        explicitSurfaceOpen: Boolean,
        retriesRemaining: Int,
        delayMs: Long,
    ) {
        val now = SystemClock.uptimeMillis()
        val throttleDelayMs = lastScanAtByPackage[packageName]?.let { lastScanAt ->
            (MIN_SCAN_INTERVAL_MS - (now - lastScanAt)).coerceAtLeast(0L)
        } ?: 0L
        val effectiveDelayMs = maxOf(delayMs, throttleDelayMs)

        pendingPackageName = packageName
        pendingEventClassName = eventClassName
        pendingEventType = eventType
        pendingEventTime = eventTime
        pendingExplicitSurfaceOpen = explicitSurfaceOpen
        pendingRetriesRemaining = retriesRemaining
        pendingScan = Runnable {
            pendingScan = null
            val latestPackageName = pendingPackageName ?: return@Runnable
            val latestEventClassName = pendingEventClassName
            val latestEventType = pendingEventType ?: AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            val latestEventTime = pendingEventTime ?: SystemClock.uptimeMillis()
            val latestExplicitSurfaceOpen = pendingExplicitSurfaceOpen
            val latestRetriesRemaining = pendingRetriesRemaining
            pendingPackageName = null
            pendingEventClassName = null
            pendingEventType = null
            pendingEventTime = null
            pendingExplicitSurfaceOpen = false
            pendingRetriesRemaining = 0
            lastScanAtByPackage[latestPackageName] = SystemClock.uptimeMillis()

            scanCurrentWindow(
                expectedPackageName = latestPackageName,
                eventClassName = latestEventClassName,
                triggerEventType = latestEventType,
                triggerEventTime = latestEventTime,
                triggerExplicitSurfaceOpen = latestExplicitSurfaceOpen,
                retriesRemaining = latestRetriesRemaining,
            )
        }.also { scan ->
            mainHandler.postDelayed(scan, effectiveDelayMs)
        }
    }

    private fun scanCurrentWindow(
        expectedPackageName: String,
        eventClassName: String?,
        triggerEventType: Int,
        triggerEventTime: Long,
        triggerExplicitSurfaceOpen: Boolean,
        retriesRemaining: Int,
    ) {
        if (!settingsRepository.shouldMonitorPackage(expectedPackageName)) {
            awaitingExitSinceByPackage.remove(expectedPackageName)
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            scheduleRetryIfNeeded(
                packageName = expectedPackageName,
                eventClassName = eventClassName,
                eventType = triggerEventType,
                eventTime = triggerEventTime,
                explicitSurfaceOpen = triggerExplicitSurfaceOpen,
                retriesRemaining = retriesRemaining,
            )
            return
        }
        val activePackageName = root.packageName?.toString()
        if (activePackageName != null && activePackageName != expectedPackageName) {
            awaitingExitSinceByPackage.remove(expectedPackageName)
            return
        }

        val contentBlockingEnabled =
            settingsRepository.isBlockingEnabledFor(expectedPackageName)
        val sessionLimitEnabled =
            expectedPackageName == SettingsRepository.INSTAGRAM_PACKAGE &&
                settingsRepository.instagramSessionLimitEnabled

        // Activity/class markers are already strong enough on their own. Check
        // them before copying the accessibility tree, which is the expensive
        // part of detection on content-heavy screens.
        val eventClassDetection = if (contentBlockingEnabled) {
            detectionEngine.detectEventClass(
                packageName = expectedPackageName,
                eventClassName = eventClassName,
            )
        } else {
            null
        }
        val snapshot = if (sessionLimitEnabled || eventClassDetection == null) {
            UiTreeReader.capture(root)
        } else {
            null
        }

        if (sessionLimitEnabled) {
            instagramSessionSurface = instagramSurfaceClassifier.classify(
                eventClassName = eventClassName,
                root = snapshot,
            )
        }

        if (!contentBlockingEnabled) {
            awaitingExitSinceByPackage.remove(expectedPackageName)
            return
        }

        val detection = eventClassDetection ?: detectionEngine.detect(
            DetectionContext(
                packageName = expectedPackageName,
                eventClassName = eventClassName,
                root = snapshot ?: UiTreeReader.capture(root),
            ),
        )

        if (detection == null) {
            // Seeing a normal screen confirms that the previous Back action worked.
            awaitingExitSinceByPackage.remove(expectedPackageName)
            scheduleRetryIfNeeded(
                packageName = expectedPackageName,
                eventClassName = eventClassName,
                eventType = triggerEventType,
                eventTime = triggerEventTime,
                explicitSurfaceOpen = triggerExplicitSurfaceOpen,
                retriesRemaining = retriesRemaining,
            )
            return
        }

        val now = SystemClock.uptimeMillis()
        val awaitingExitSince = awaitingExitSinceByPackage[expectedPackageName]
        val isStillAwaitingExit = awaitingExitSince != null &&
            now - awaitingExitSince < EXIT_CONFIRMATION_TIMEOUT_MS
        val lastBlockedOpenTime = lastBlockedOpenTimeByPackage[expectedPackageName]
            ?: Long.MIN_VALUE
        val isNewExplicitOpen = triggerExplicitSurfaceOpen &&
            triggerEventTime > lastBlockedOpenTime &&
            (awaitingExitSince == null || triggerEventTime > awaitingExitSince)

        // The accessibility tree can keep the old Reels/Shorts screen briefly after
        // Back. Never send another Back for that stale tree: it would close the app.
        if (isStillAwaitingExit && !isNewExplicitOpen) {
            return
        }

        if (settingsRepository.punitiveModeEnabled) {
            punitiveLockManager.activate(expectedPackageName)?.let { lock ->
                awaitingExitSinceByPackage.remove(expectedPackageName)
                startPunitiveLock(lock)
            }
            return
        }

        awaitingExitSinceByPackage[expectedPackageName] = now
        if (triggerExplicitSurfaceOpen) {
            lastBlockedOpenTimeByPackage[expectedPackageName] = triggerEventTime
        }
        blockCoordinator.block(detection)
    }

    private fun startPunitiveLock(lock: PlatformLock) {
        lastPunitiveOverlayAtByPackage[lock.packageName] = SystemClock.uptimeMillis()
        blockCoordinator.startPunitiveLock(
            packageName = lock.packageName,
            blockedUntilMillis = lock.blockedUntilMillis,
        )
    }

    private fun enforcePunitiveLock(lock: PlatformLock) {
        val now = SystemClock.uptimeMillis()
        val lastOverlayAt = lastPunitiveOverlayAtByPackage[lock.packageName]
        val showOverlay = lastOverlayAt == null ||
            now - lastOverlayAt >= PUNITIVE_OVERLAY_THROTTLE_MS

        if (showOverlay) lastPunitiveOverlayAtByPackage[lock.packageName] = now

        // The coordinator uses guarded Back actions. Unlike Home, this restores
        // the exact screen (including the current launcher page) that was visible
        // before the blocked application was opened.
        blockCoordinator.enforcePunitiveLock(
            packageName = lock.packageName,
            blockedUntilMillis = lock.blockedUntilMillis,
            showOverlay = showOverlay,
        )
    }

    private fun activeLockFor(packageName: String): PlatformLock? {
        val punitiveLock = punitiveLockManager.activeLock(packageName)
        val enabledPunitiveLock = if (settingsRepository.punitiveModeEnabled) {
            punitiveLock
        } else {
            if (punitiveLock != null) punitiveLockManager.clear(packageName)
            null
        }

        val sessionLock = if (
            packageName == SettingsRepository.INSTAGRAM_PACKAGE &&
            settingsRepository.instagramSessionLimitEnabled
        ) {
            instagramSessionLimitManager.activeLock()
        } else {
            null
        }

        return listOfNotNull(enabledPunitiveLock, sessionLock)
            .maxByOrNull(PlatformLock::blockedUntilMillis)
    }

    private fun updateInstagramSession() {
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        val deltaMillis =
            (nowElapsedMillis - lastSessionTickElapsedMillis)
                .coerceIn(0L, MAX_SESSION_TICK_DELTA_MS)
        lastSessionTickElapsedMillis = nowElapsedMillis

        if (!settingsRepository.instagramSessionLimitEnabled) {
            instagramWasForeground = false
            instagramSessionSurface = InstagramSessionSurface.COUNTED
            lastSurfaceRefreshElapsedMillis = 0L
            return
        }

        if (!powerManager.isInteractive) return

        val root = rootInActiveWindow
        if (root == null) {
            // The active tree can disappear briefly while Instagram redraws.
            // Pause this tick, but keep the current session instead of resetting it.
            return
        }
        val activePackageName = runCatching {
            root.packageName?.toString()
        }.getOrNull()
        if (activePackageName != SettingsRepository.INSTAGRAM_PACKAGE) {
            if (instagramWasForeground) {
                instagramSessionLimitManager.resetCurrentSession()
            }
            instagramWasForeground = false
            instagramSessionSurface = InstagramSessionSurface.COUNTED
            lastSurfaceRefreshElapsedMillis = 0L
            return
        }

        val activeLock = activeLockFor(SettingsRepository.INSTAGRAM_PACKAGE)
        if (activeLock != null) {
            enforcePunitiveLock(activeLock)
            instagramWasForeground = false
            return
        }

        if (!instagramWasForeground) {
            instagramWasForeground = true
            instagramSessionSurface = instagramSurfaceClassifier.classify(
                eventClassName = null,
                root = UiTreeReader.capture(root),
            )
            lastSurfaceRefreshElapsedMillis = nowElapsedMillis
            return
        }

        if (nowElapsedMillis - lastSurfaceRefreshElapsedMillis >=
            SESSION_SURFACE_REFRESH_INTERVAL_MS
        ) {
            instagramSessionSurface = instagramSurfaceClassifier.classify(
                eventClassName = null,
                root = UiTreeReader.capture(root),
            )
            lastSurfaceRefreshElapsedMillis = nowElapsedMillis
        }

        if (instagramSessionSurface != InstagramSessionSurface.COUNTED) return

        val update = instagramSessionLimitManager.recordActiveTime(deltaMillis)
        update.startedLock?.let { lock ->
            instagramWasForeground = false
            startPunitiveLock(lock)
        }
    }

    private fun scheduleRetryIfNeeded(
        packageName: String,
        eventClassName: String?,
        eventType: Int,
        eventTime: Long,
        explicitSurfaceOpen: Boolean,
        retriesRemaining: Int,
    ) {
        if (retriesRemaining <= 0 || pendingScan != null) return

        scheduleScan(
            packageName = packageName,
            eventClassName = eventClassName,
            eventType = eventType,
            eventTime = eventTime,
            explicitSurfaceOpen = explicitSurfaceOpen,
            retriesRemaining = retriesRemaining - 1,
            delayMs = NAVIGATION_RETRY_DELAY_MS,
        )
    }

    private fun isExplicitSurfaceOpenEvent(
        event: AccessibilityEvent,
        packageName: String,
    ): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED
        ) {
            return false
        }

        val source = runCatching { event.source }.getOrNull() ?: return false
        if (!runCatching { source.isVisibleToUser }.getOrDefault(false)) return false

        val signal = listOfNotNull(
            runCatching { source.text?.toString() }.getOrNull(),
            runCatching { source.contentDescription?.toString() }.getOrNull(),
            runCatching { source.viewIdResourceName }.getOrNull(),
        ).joinToString(" ").lowercase(Locale.ROOT)

        return when (packageName) {
            SettingsRepository.YOUTUBE_PACKAGE ->
                signal.contains("shorts") || signal.contains("reel")

            SettingsRepository.INSTAGRAM_PACKAGE ->
                signal.contains("reels") || signal.contains("clips")

            else -> false
        }
    }

    override fun onInterrupt() {
        cancelPendingScan()
    }

    override fun onDestroy() {
        cancelPendingScan()
        mainHandler.removeCallbacks(sessionTicker)
        lastScanAtByPackage.clear()
        blockCoordinator.dispose()
        super.onDestroy()
    }

    private fun cancelPendingScan() {
        pendingScan?.let(mainHandler::removeCallbacks)
        pendingScan = null
        pendingPackageName = null
        pendingEventClassName = null
        pendingEventType = null
        pendingEventTime = null
        pendingExplicitSurfaceOpen = false
        pendingRetriesRemaining = 0
    }

    private companion object {
        const val INITIAL_SCAN_DELAY_MS = 40L
        const val MIN_SCAN_INTERVAL_MS = 200L
        const val NAVIGATION_RETRY_DELAY_MS = 120L
        const val NAVIGATION_RETRY_COUNT = 3
        const val EXIT_CONFIRMATION_TIMEOUT_MS = 1_500L
        const val PUNITIVE_OVERLAY_THROTTLE_MS = 800L
        const val SESSION_TICK_INTERVAL_MS = 1_000L
        const val SESSION_SURFACE_REFRESH_INTERVAL_MS = 2_000L
        const val MAX_SESSION_TICK_DELTA_MS = 2_000L
    }
}
