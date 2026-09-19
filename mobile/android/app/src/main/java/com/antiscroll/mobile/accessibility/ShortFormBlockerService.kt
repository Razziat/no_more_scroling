package com.antiscroll.mobile.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.antiscroll.mobile.blocking.BlockCoordinator
import com.antiscroll.mobile.blocking.BlockOverlayController
import com.antiscroll.mobile.data.InstagramSessionLimitManager
import com.antiscroll.mobile.data.InstagramSessionObservation
import com.antiscroll.mobile.data.InstagramSessionTracker
import com.antiscroll.mobile.data.InstagramSessionTrackingDecision
import com.antiscroll.mobile.data.PlatformLock
import com.antiscroll.mobile.data.PunitiveLockManager
import com.antiscroll.mobile.data.SettingsRepository
import com.antiscroll.mobile.detection.InstagramSessionSurface
import com.antiscroll.mobile.detection.InstagramSessionSurfaceClassifier
import com.antiscroll.mobile.detection.ShortFormDetectionEngine
import java.util.Locale

class ShortFormBlockerService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val detectionEngine = ShortFormDetectionEngine()
    private val instagramSurfaceClassifier = InstagramSessionSurfaceClassifier()
    private val instagramSessionTracker = InstagramSessionTracker()
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
    private var pendingScanIsPriority = false
    private var instagramSessionSurface = InstagramSessionSurface.COUNTED
    private var lastSurfaceRefreshElapsedMillis = 0L
    private var lastTickerErrorLogElapsedMillis = 0L
    private var screenStateReceiverRegistered = false
    private var inputMethodPackageName: String? = null
    private var pendingInstagramActiveMillis = 0L
    private var lastInstagramPersistElapsedMillis = 0L
    private var serviceConnected = false

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (serviceConnected) refreshSessionTicker()
    }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF &&
                intent?.action != Intent.ACTION_SCREEN_ON
            ) {
                return
            }

            val nowElapsedMillis = SystemClock.elapsedRealtime()
            flushPendingInstagramTime(nowElapsedMillis, showLockOverlay = false)
            instagramSessionTracker.suspend(nowElapsedMillis)
            lastSurfaceRefreshElapsedMillis = 0L
            refreshSessionTicker()
        }
    }

    private val sessionTicker = object : Runnable {
        override fun run() {
            try {
                updateInstagramSession()
            } catch (error: Exception) {
                val now = SystemClock.elapsedRealtime()
                if (lastTickerErrorLogElapsedMillis == 0L ||
                    now - lastTickerErrorLogElapsedMillis >= TICKER_ERROR_LOG_INTERVAL_MS
                ) {
                    lastTickerErrorLogElapsedMillis = now
                    Log.e(TAG, "Instagram session ticker failed", error)
                }
            } finally {
                if (shouldRunSessionTicker()) {
                    mainHandler.postDelayed(this, SESSION_TICK_INTERVAL_MS)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        punitiveLockManager = PunitiveLockManager(this)
        instagramSessionLimitManager = InstagramSessionLimitManager(this)
        powerManager = getSystemService(PowerManager::class.java)
        inputMethodPackageName = Settings.Secure
            .getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let(ComponentName::unflattenFromString)
            ?.packageName
        blockCoordinator = BlockCoordinator(
            service = this,
            overlayController = BlockOverlayController(this),
        )
        registerScreenStateReceiver()
        settingsRepository.registerListener(settingsListener)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        instagramSessionTracker.reset(nowElapsedMillis)
        lastInstagramPersistElapsedMillis = nowElapsedMillis
        serviceConnected = true
        refreshSessionTicker()
    }

    private fun shouldRunSessionTicker(): Boolean =
        serviceConnected && settingsRepository.instagramSessionLimitEnabled &&
            powerManager.isInteractive

    private fun refreshSessionTicker() {
        mainHandler.removeCallbacks(sessionTicker)
        if (shouldRunSessionTicker()) {
            mainHandler.post(sessionTicker)
        } else {
            val now = SystemClock.elapsedRealtime()
            instagramSessionTracker.suspend(now)
            if (!settingsRepository.instagramSessionLimitEnabled) {
                pendingInstagramActiveMillis = 0L
                instagramSessionSurface = InstagramSessionSurface.COUNTED
                lastSurfaceRefreshElapsedMillis = 0L
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

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
        val priorityScan = explicitSurfaceOpen || isPriorityScanEvent(event.eventType)

        if (pendingScan != null) {
            if (priorityScan && !pendingScanIsPriority) {
                cancelPendingScan()
                scheduleScan(
                    packageName = packageName,
                    eventClassName = event.className?.toString(),
                    eventType = event.eventType,
                    eventTime = event.eventTime,
                    explicitSurfaceOpen = explicitSurfaceOpen,
                    retriesRemaining = retryCount,
                    delayMs = PRIORITY_SCAN_DELAY_MS,
                    priorityScan = true,
                )
                return
            }
            if (pendingScanIsPriority && !priorityScan) {
                // A content-change storm must not replace the activity/click that
                // requested the priority scan. The root is read when the scan runs.
                return
            }

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
            delayMs = if (priorityScan) PRIORITY_SCAN_DELAY_MS else CONTENT_SCAN_DELAY_MS,
            priorityScan = priorityScan,
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
        priorityScan: Boolean,
    ) {
        val now = SystemClock.uptimeMillis()
        val minimumIntervalMs = if (priorityScan) {
            PRIORITY_SCAN_INTERVAL_MS
        } else {
            CONTENT_SCAN_INTERVAL_MS
        }
        val throttleDelayMs = lastScanAtByPackage[packageName]?.let { lastScanAt ->
            (minimumIntervalMs - (now - lastScanAt)).coerceAtLeast(0L)
        } ?: 0L
        val effectiveDelayMs = maxOf(delayMs, throttleDelayMs)

        pendingPackageName = packageName
        pendingEventClassName = eventClassName
        pendingEventType = eventType
        pendingEventTime = eventTime
        pendingExplicitSurfaceOpen = explicitSurfaceOpen
        pendingRetriesRemaining = retriesRemaining
        pendingScanIsPriority = priorityScan
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
            pendingScanIsPriority = false
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
        if (activePackageName != expectedPackageName) {
            awaitingExitSinceByPackage.remove(expectedPackageName)
            return
        }

        val contentBlockingEnabled =
            settingsRepository.isBlockingEnabledFor(expectedPackageName)
        val sessionLimitEnabled =
            expectedPackageName == SettingsRepository.INSTAGRAM_PACKAGE &&
                settingsRepository.instagramSessionLimitEnabled

        // The event may describe an activity that has already been left. A
        // deferred scan must confirm the surface from the current window.
        val snapshot = UiTreeReader.capture(root)

        if (sessionLimitEnabled) {
            instagramSessionSurface = instagramSurfaceClassifier.classify(
                eventClassName = snapshot.className,
                root = snapshot,
            )
            lastSurfaceRefreshElapsedMillis = SystemClock.elapsedRealtime()
        }

        if (!contentBlockingEnabled) {
            awaitingExitSinceByPackage.remove(expectedPackageName)
            return
        }

        val detection = detectionEngine.detectCurrentWindow(expectedPackageName, snapshot)

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

        if (!settingsRepository.instagramSessionLimitEnabled) {
            pendingInstagramActiveMillis = 0L
            instagramSessionTracker.reset(nowElapsedMillis)
            instagramSessionSurface = InstagramSessionSurface.COUNTED
            lastSurfaceRefreshElapsedMillis = 0L
            return
        }

        if (!powerManager.isInteractive) {
            flushPendingInstagramTime(nowElapsedMillis, showLockOverlay = false)
            instagramSessionTracker.suspend(nowElapsedMillis)
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            observeUnknownInstagramWindow(nowElapsedMillis)
            return
        }
        val activePackageName = runCatching {
            root.packageName?.toString()
        }.getOrNull()
        if (activePackageName == null) {
            observeUnknownInstagramWindow(nowElapsedMillis)
            return
        }
        if (activePackageName != SettingsRepository.INSTAGRAM_PACKAGE) {
            if (isTransientSystemWindow(activePackageName)) {
                observeUnknownInstagramWindow(nowElapsedMillis)
                lastSurfaceRefreshElapsedMillis = 0L
                return
            }

            flushPendingInstagramTime(nowElapsedMillis)
            applyInstagramSessionDecision(
                decision = instagramSessionTracker.observe(
                    nowElapsedMillis,
                    InstagramSessionObservation.OUTSIDE,
                ),
                nowElapsedMillis = nowElapsedMillis,
            )
            lastSurfaceRefreshElapsedMillis = 0L
            return
        }

        val activeLock = activeLockFor(SettingsRepository.INSTAGRAM_PACKAGE)
        if (activeLock != null) {
            pendingInstagramActiveMillis = 0L
            enforcePunitiveLock(activeLock)
            instagramSessionTracker.reset(nowElapsedMillis)
            return
        }

        if (lastSurfaceRefreshElapsedMillis == 0L ||
            nowElapsedMillis - lastSurfaceRefreshElapsedMillis >=
            SESSION_SURFACE_REFRESH_INTERVAL_MS
        ) {
            instagramSessionSurface = instagramSurfaceClassifier.classify(
                eventClassName = null,
                root = UiTreeReader.capture(root),
            )
            lastSurfaceRefreshElapsedMillis = nowElapsedMillis
        }

        val observation = if (
            instagramSessionSurface == InstagramSessionSurface.COUNTED
        ) {
            InstagramSessionObservation.COUNTED
        } else {
            InstagramSessionObservation.PAUSED
        }
        if (observation == InstagramSessionObservation.PAUSED) {
            flushPendingInstagramTime(nowElapsedMillis)
        }
        applyInstagramSessionDecision(
            decision = instagramSessionTracker.observe(nowElapsedMillis, observation),
            nowElapsedMillis = nowElapsedMillis,
        )
    }

    private fun observeUnknownInstagramWindow(nowElapsedMillis: Long) {
        flushPendingInstagramTime(nowElapsedMillis)
        applyInstagramSessionDecision(
            decision = instagramSessionTracker.observe(
                nowElapsedMillis,
                InstagramSessionObservation.UNKNOWN,
            ),
            nowElapsedMillis = nowElapsedMillis,
        )
    }

    private fun isTransientSystemWindow(packageName: String): Boolean =
        packageName == inputMethodPackageName || packageName in TRANSIENT_WINDOW_PACKAGES

    private fun applyInstagramSessionDecision(
        decision: InstagramSessionTrackingDecision,
        nowElapsedMillis: Long,
    ) {
        if (decision.shouldResetSession) {
            pendingInstagramActiveMillis = 0L
            instagramSessionLimitManager.resetCurrentSession()
            instagramSessionSurface = InstagramSessionSurface.COUNTED
            lastInstagramPersistElapsedMillis = nowElapsedMillis
        }

        if (decision.activeMillis <= 0L) return

        pendingInstagramActiveMillis += decision.activeMillis
        if (nowElapsedMillis - lastInstagramPersistElapsedMillis >=
            SESSION_PERSIST_INTERVAL_MS
        ) {
            flushPendingInstagramTime(nowElapsedMillis)
        }
    }

    private fun flushPendingInstagramTime(
        nowElapsedMillis: Long,
        showLockOverlay: Boolean = true,
    ) {
        val activeMillis = pendingInstagramActiveMillis
        if (activeMillis <= 0L) {
            lastInstagramPersistElapsedMillis = nowElapsedMillis
            return
        }

        val update = instagramSessionLimitManager.recordActiveTime(activeMillis)
        pendingInstagramActiveMillis = 0L
        lastInstagramPersistElapsedMillis = nowElapsedMillis
        update.startedLock?.let { lock ->
            instagramSessionTracker.reset(nowElapsedMillis)
            instagramSessionSurface = InstagramSessionSurface.COUNTED
            lastSurfaceRefreshElapsedMillis = 0L
            if (showLockOverlay) startPunitiveLock(lock)
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenStateReceiver, filter)
            }
        }.onSuccess {
            screenStateReceiverRegistered = true
        }.onFailure { error ->
            Log.e(TAG, "Unable to observe screen state", error)
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
            priorityScan = true,
        )
    }

    private fun isPriorityScanEvent(eventType: Int): Boolean =
        eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SELECTED

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
        return try {
            if (!runCatching { source.isVisibleToUser }.getOrDefault(false)) {
                false
            } else {
                val signal = listOfNotNull(
                    runCatching { source.text?.toString() }.getOrNull(),
                    runCatching { source.contentDescription?.toString() }.getOrNull(),
                    runCatching { source.viewIdResourceName }.getOrNull(),
                ).joinToString(" ").lowercase(Locale.ROOT)

                when (packageName) {
                    SettingsRepository.YOUTUBE_PACKAGE ->
                        signal.contains("shorts") || signal.contains("reel")

                    SettingsRepository.INSTAGRAM_PACKAGE ->
                        signal.contains("reels") || signal.contains("clips")

                    else -> false
                }
            }
        } finally {
            recycleEventSource(source)
        }
    }

    @Suppress("DEPRECATION")
    private fun recycleEventSource(source: android.view.accessibility.AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runCatching { source.recycle() }
        }
    }

    override fun onInterrupt() {
        cancelPendingScan()
    }

    override fun onDestroy() {
        serviceConnected = false
        settingsRepository.unregisterListener(settingsListener)
        cancelPendingScan()
        mainHandler.removeCallbacks(sessionTicker)
        runCatching {
            flushPendingInstagramTime(
                nowElapsedMillis = SystemClock.elapsedRealtime(),
                showLockOverlay = false,
            )
        }
        if (screenStateReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenStateReceiverRegistered = false
        }
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
        pendingScanIsPriority = false
    }

    private companion object {
        const val PRIORITY_SCAN_DELAY_MS = 40L
        const val CONTENT_SCAN_DELAY_MS = 250L
        const val PRIORITY_SCAN_INTERVAL_MS = 250L
        const val CONTENT_SCAN_INTERVAL_MS = 750L
        const val NAVIGATION_RETRY_DELAY_MS = 120L
        const val NAVIGATION_RETRY_COUNT = 3
        const val EXIT_CONFIRMATION_TIMEOUT_MS = 1_500L
        const val PUNITIVE_OVERLAY_THROTTLE_MS = 800L
        const val SESSION_TICK_INTERVAL_MS = 1_000L
        const val SESSION_SURFACE_REFRESH_INTERVAL_MS = 3_000L
        const val SESSION_PERSIST_INTERVAL_MS = 5_000L
        const val TICKER_ERROR_LOG_INTERVAL_MS = 30_000L
        const val TAG = "ShortFormBlocker"
        val TRANSIENT_WINDOW_PACKAGES = setOf(
            "android",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.android.systemui",
            "com.google.android.permissioncontroller",
        )
    }
}
