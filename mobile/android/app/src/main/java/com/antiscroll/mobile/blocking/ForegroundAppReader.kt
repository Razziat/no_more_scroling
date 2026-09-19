package com.antiscroll.mobile.blocking

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.PowerManager

/** Reads only the window's package, never its text or descendants. */
class ForegroundAppReader(private val service: AccessibilityService) {
    private val powerManager = service.getSystemService(PowerManager::class.java)

    fun isPackageActive(packageName: String): Boolean = runCatching {
        if (!powerManager.isInteractive) return@runCatching false
        val root = service.rootInActiveWindow ?: return@runCatching false
        try {
            root.packageName?.toString() == packageName
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }
    }.getOrDefault(false)
}
