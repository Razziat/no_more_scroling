package com.antiscroll.mobile.blocking

import android.accessibilityservice.AccessibilityService
import com.antiscroll.mobile.detection.ShortFormDetection

class BlockCoordinator internal constructor(private val environment: BlockEnvironment) {
    constructor(service: AccessibilityService, overlayController: BlockOverlayController) :
        this(AndroidBlockEnvironment(service, overlayController))

    private val pendingExitChecks = mutableMapOf<String, Runnable>()
    private val lastBackAtByPackage = mutableMapOf<String, Long>()
    private var pendingNoticeDismissal: Runnable? = null
    private var noticePackage: String? = null
    private var noticeBlockedUntil = 0L

    fun block(detection: ShortFormDetection) {
        cancelNoticeDismissal()
        environment.performBack()
        environment.showNormal(detection.surface)
    }

    fun startPunitiveLock(
        packageName: String,
        blockedUntilMillis: Long,
    ) {
        handlePunitiveLock(packageName, blockedUntilMillis, showOverlay = true, newPenalty = true)
    }

    fun enforcePunitiveLock(
        packageName: String,
        blockedUntilMillis: Long,
        showOverlay: Boolean,
    ) {
        handlePunitiveLock(packageName, blockedUntilMillis, showOverlay, newPenalty = false)
    }

    private fun handlePunitiveLock(
        packageName: String,
        blockedUntilMillis: Long,
        showOverlay: Boolean,
        newPenalty: Boolean,
    ) {
        // A background content event does not mean the user reopened the app.
        if (!environment.isPackageActive(packageName)) {
            pendingExitChecks.remove(packageName)?.let(environment::removeCallback)
            return
        }
        requestPackageExit(packageName)
        if (showOverlay) {
            showNotice(packageName, blockedUntilMillis, newPenalty)
        }
    }

    private fun showNotice(packageName: String, blockedUntilMillis: Long, newPenalty: Boolean) {
        // Repeated accessibility events must not prolong the notice on the launcher.
        if (pendingNoticeDismissal != null && noticePackage == packageName &&
            noticeBlockedUntil == blockedUntilMillis
        ) return
        cancelNoticeDismissal()
        environment.showPunitive(packageName, blockedUntilMillis, newPenalty)
        noticePackage = packageName
        noticeBlockedUntil = blockedUntilMillis
        pendingNoticeDismissal = Runnable {
            environment.dismissPunitive(packageName)
            pendingNoticeDismissal = null
            noticePackage = null
        }.also { environment.postDelayed(it, NOTICE_DURATION_MS) }
    }

    private fun cancelNoticeDismissal() {
        pendingNoticeDismissal?.let(environment::removeCallback)
        pendingNoticeDismissal = null
        noticePackage = null
    }

    fun dispose() {
        cancelNoticeDismissal()
        pendingExitChecks.values.forEach(environment::removeCallback)
        pendingExitChecks.clear()
        environment.dismiss()
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
            if (!environment.isPackageActive(packageName)) {
                return@Runnable
            }

            // Verify the final Back too, without issuing an extra Back action.
            // The notice has its own deadline and remains readable after exit.
            if (attemptsRemaining <= 0) return@Runnable

            val now = environment.uptimeMillis()
            val lastBackAt = lastBackAtByPackage[packageName]
            val cooldownRemaining = lastBackAt?.let {
                BACK_ACTION_COOLDOWN_MS - (now - it)
            } ?: 0L
            if (cooldownRemaining > 0L) {
                scheduleExitCheck(packageName, cooldownRemaining, attemptsRemaining)
                return@Runnable
            }

            val actionPerformed = environment.performBack()
            if (actionPerformed) lastBackAtByPackage[packageName] = now

            scheduleExitCheck(
                packageName = packageName,
                delayMs = EXIT_VERIFICATION_DELAY_MS,
                attemptsRemaining = attemptsRemaining - 1,
            )
        }

        pendingExitChecks[packageName] = check
        environment.postDelayed(check, delayMs)
    }

    private companion object {
        const val NOTICE_DURATION_MS = 3_000L
        const val MAX_BACK_ATTEMPTS_PER_REQUEST = 2
        const val BACK_ACTION_COOLDOWN_MS = 180L
        const val EXIT_VERIFICATION_DELAY_MS = 260L
    }
}
