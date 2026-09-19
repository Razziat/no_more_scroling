package com.antiscroll.mobile.blocking

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.antiscroll.mobile.R
import com.antiscroll.mobile.data.SettingsRepository
import com.antiscroll.mobile.detection.BlockedSurface

class BlockOverlayController(context: AccessibilityService) {
    private val appContext = context.applicationContext
    // AccessibilityService's WindowManager carries its accessibility overlay
    // token. The application WindowManager does not have that token.
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val foreground = ForegroundAppReader(context)

    private var currentView: View? = null
    private var hideRunnable: Runnable? = null
    private var currentPunitivePackage: String? = null

    fun show(surface: BlockedSurface) {
        dismiss()

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp, 15.dp, 24.dp, 15.dp)
            elevation = 12.dp.toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 22.dp.toFloat()
                setColor(Color.rgb(23, 29, 26))
            }
            contentDescription = appContext.getString(R.string.block_overlay_title)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        }

        container.addView(
            TextView(appContext).apply {
                text = appContext.getString(R.string.block_overlay_title)
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
        container.addView(
            TextView(appContext).apply {
                text = appContext.getString(
                    when (surface) {
                        BlockedSurface.YOUTUBE_SHORTS -> R.string.youtube_blocked_overlay
                        BlockedSurface.INSTAGRAM_REELS -> R.string.instagram_blocked_overlay
                    },
                )
                setTextColor(Color.rgb(192, 201, 194))
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 4.dp, 0, 0)
            },
        )

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 52.dp
        }

        val wasAdded = runCatching {
            windowManager.addView(container, layoutParams)
            true
        }.onFailure { error ->
            Log.e(TAG, "Unable to show blocking overlay", error)
        }.getOrDefault(false)

        if (!wasAdded) return

        currentView = container
        hideRunnable = Runnable(::dismiss).also { runnable ->
            handler.postDelayed(runnable, DISPLAY_DURATION_MS)
        }
    }

    /** A static notice; its fixed lifetime is managed by BlockCoordinator. */
    fun showPunitive(packageName: String, blockedUntilMillis: Long, newPenalty: Boolean) {
        val remaining = blockedUntilMillis - System.currentTimeMillis()
        // A replacement owns a new deadline; always remove the previous notice
        // even if the foreground changes before this second check.
        dismiss()
        if (!foreground.isPackageActive(packageName) || remaining <= 0L) return

        val platform = when (packageName) {
            SettingsRepository.YOUTUBE_PACKAGE -> "YouTube"
            SettingsRepository.INSTAGRAM_PACKAGE -> "Instagram"
            else -> appContext.getString(R.string.app_name)
        }
        val message = appContext.getString(
            if (newPenalty) R.string.punitive_notice_started else R.string.punitive_notice_remaining,
            platform,
            formatRemaining(remaining),
        )
        val notice = TextView(appContext).apply {
            text = message
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            maxWidth = (appContext.resources.displayMetrics.widthPixels - 32.dp).coerceAtLeast(1)
            setPadding(20.dp, 14.dp, 20.dp, 14.dp)
            elevation = 8.dp.toFloat()
            background = GradientDrawable().apply {
                cornerRadius = 18.dp.toFloat()
                setColor(Color.rgb(23, 29, 26))
            }
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 52.dp
        }
        runCatching { windowManager.addView(notice, layoutParams) }
            .onSuccess {
                currentView = notice
                currentPunitivePackage = packageName
            }
            .onFailure { error -> Log.e(TAG, "Unable to show punitive notice", error) }
    }

    fun dismissPunitive(packageName: String) {
        if (currentPunitivePackage == packageName) dismiss()
    }

    fun dismiss() {
        hideRunnable?.let(handler::removeCallbacks)
        hideRunnable = null

        currentView?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { error -> Log.e(TAG, "Unable to remove blocking overlay", error) }
        }
        currentView = null
        currentPunitivePackage = null
    }

    private val Int.dp: Int
        get() = (this * appContext.resources.displayMetrics.density).toInt()

    private fun formatRemaining(remainingMillis: Long): String {
        if (remainingMillis < 60_000L) {
            return appContext.getString(R.string.notice_duration_less_than_minute)
        }
        val minutes = (remainingMillis + 59_999L) / 60_000L
        return if (minutes >= 60L) {
            appContext.getString(R.string.notice_duration_hours_minutes, minutes / 60L, minutes % 60L)
        } else {
            appContext.getString(R.string.notice_duration_minutes, minutes)
        }
    }

    private companion object {
        const val TAG = "BlockOverlay"
        const val DISPLAY_DURATION_MS = 2_400L
    }
}
