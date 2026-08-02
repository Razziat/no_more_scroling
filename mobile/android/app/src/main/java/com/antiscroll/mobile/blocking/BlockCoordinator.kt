package com.antiscroll.mobile.blocking

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.antiscroll.mobile.detection.ShortFormDetection

class BlockCoordinator(
    private val service: AccessibilityService,
    private val overlayController: BlockOverlayController,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingExitChecks = mutableMapOf<String, Runnable>()
    private val lastBackAtByPackage = mutableMapOf<String, Long>()

    fun block(detection: ShortFormDetection) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        overlayController.show(detection.surface)
    }

    fun startPunitiveLock(
        packageName: String,
        blockedUntilMillis: Long,
    ) {
        requestPackageExit(packageName)
        overlayController.showPunitive(packageName, blockedUntilMillis)
    }

    fun enforcePunitiveLock(
        packageName: String,
        blockedUntilMillis: Long,
        showOverlay: Boolean,
    ) {
        requestPackageExit(packageName)
        if (showOverlay) {
            overlayController.showPunitive(packageName, blockedUntilMillis)
        }
    }

    fun dispose() {
        pendingExitChecks.values.forEach(mainHandler::removeCallbacks)
        pendingExitChecks.clear()
        overlayController.dismiss()
    }

    /**
     * Leaves the blocked application with Back instead of Home. Home can reset
     * some launchers to their first page, while Back restores exactly what was
     * visible before the application was opened.
     *
     * A second guarded check is useful when the first Back only closes the
     * Shorts/Reels viewer. The package check is deliberately repeated before
     * every action so a delayed event can never press Back on the launcher or
     * in another application.
     */
    private fun requestPackageExit(packageName: String) {
        if (pendingExitChecks.containsKey(packageName)) return
        scheduleExitCheck(
            packageName = packageName,
            delayMs = 0L,
            attemptsRemaining = MAX_BACK_ATTEMPTS_PER_REQUEST,
        )
    }

    private fun scheduleExitCheck(
        packageName: String,
        delayMs: Long,
        attemptsRemaining: Int,
    ) {
        val check = Runnable {
            pendingExitChecks.remove(packageName)
            if (!isPackageActive(packageName)) return@Runnable

            val now = SystemClock.uptimeMillis()
            val lastBackAt = lastBackAtByPackage[packageName]
            val cooldownRemaining = lastBackAt?.let {
                BACK_ACTION_COOLDOWN_MS - (now - it)
            } ?: 0L
            if (cooldownRemaining > 0L) {
                scheduleExitCheck(packageName, cooldownRemaining, attemptsRemaining)
                return@Runnable
            }

            val actionPerformed = service.performGlobalAction(
                AccessibilityService.GLOBAL_ACTION_BACK,
            )
            if (actionPerformed) lastBackAtByPackage[packageName] = now

            if (attemptsRemaining > 1) {
                scheduleExitCheck(
                    packageName = packageName,
                    delayMs = EXIT_VERIFICATION_DELAY_MS,
                    attemptsRemaining = attemptsRemaining - 1,
                )
            }
        }

        pendingExitChecks[packageName] = check
        mainHandler.postDelayed(check, delayMs)
    }

    private fun isPackageActive(packageName: String): Boolean = runCatching {
        service.rootInActiveWindow?.packageName?.toString() == packageName
    }.getOrDefault(false)

    private companion object {
        const val MAX_BACK_ATTEMPTS_PER_REQUEST = 2
        const val BACK_ACTION_COOLDOWN_MS = 180L
        const val EXIT_VERIFICATION_DELAY_MS = 260L
    }
}
