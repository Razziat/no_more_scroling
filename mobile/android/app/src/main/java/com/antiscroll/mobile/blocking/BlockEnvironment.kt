package com.antiscroll.mobile.blocking

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.antiscroll.mobile.detection.BlockedSurface

/** Android effects kept separate so delayed exit sequences can be tested. */
internal interface BlockEnvironment {
    fun isPackageActive(packageName: String): Boolean
    fun performBack(): Boolean
    fun showNormal(surface: BlockedSurface)
    fun showPunitive(packageName: String, blockedUntilMillis: Long, newPenalty: Boolean)
    fun dismissPunitive(packageName: String)
    fun dismiss()
    fun uptimeMillis(): Long
    fun postDelayed(callback: Runnable, delayMillis: Long)
    fun removeCallback(callback: Runnable)
}

internal class AndroidBlockEnvironment(
    private val service: AccessibilityService,
    private val overlay: BlockOverlayController,
) : BlockEnvironment {
    private val handler = Handler(Looper.getMainLooper())
    private val foreground = ForegroundAppReader(service)

    override fun isPackageActive(packageName: String) = foreground.isPackageActive(packageName)
    override fun performBack() = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    override fun showNormal(surface: BlockedSurface) = overlay.show(surface)
    override fun showPunitive(packageName: String, blockedUntilMillis: Long, newPenalty: Boolean) =
        overlay.showPunitive(packageName, blockedUntilMillis, newPenalty)
    override fun dismissPunitive(packageName: String) = overlay.dismissPunitive(packageName)
    override fun dismiss() = overlay.dismiss()
    override fun uptimeMillis() = SystemClock.uptimeMillis()
    override fun postDelayed(callback: Runnable, delayMillis: Long) {
        handler.postDelayed(callback, delayMillis)
    }
    override fun removeCallback(callback: Runnable) {
        handler.removeCallbacks(callback)
    }
}
