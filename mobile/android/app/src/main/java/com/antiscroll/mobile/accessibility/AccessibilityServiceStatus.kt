package com.antiscroll.mobile.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager

object AccessibilityServiceStatus {
    fun isEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        val expectedComponent = ComponentName(context, ShortFormBlockerService::class.java)

        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { serviceInfo ->
                val resolvedService = serviceInfo.resolveInfo.serviceInfo
                ComponentName(
                    resolvedService.packageName,
                    resolvedService.name,
                ) == expectedComponent
            }
    }
}
