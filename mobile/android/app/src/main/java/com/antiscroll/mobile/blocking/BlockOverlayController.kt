package com.antiscroll.mobile.blocking

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.antiscroll.mobile.R
import com.antiscroll.mobile.data.SettingsRepository
import com.antiscroll.mobile.detection.BlockedSurface
import java.util.Locale

class BlockOverlayController(context: Context) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var currentView: View? = null
    private var hideRunnable: Runnable? = null
    private var countdownRunnable: Runnable? = null

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
        }.getOrDefault(false)

        if (!wasAdded) return

        currentView = container
        hideRunnable = Runnable(::dismiss).also { runnable ->
            handler.postDelayed(runnable, DISPLAY_DURATION_MS)
        }
    }

    fun showPunitive(packageName: String, blockedUntilMillis: Long) {
        dismiss()

        val container = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumWidth = 280.dp
            setPadding(28.dp, 24.dp, 28.dp, 24.dp)
            elevation = 16.dp.toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28.dp.toFloat()
                setColor(Color.rgb(23, 29, 26))
                setStroke(1.dp, Color.rgb(65, 82, 72))
            }
            contentDescription = appContext.getString(R.string.punitive_overlay_title)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        }

        container.addView(
            TextView(appContext).apply {
                text = appContext.getString(R.string.punitive_overlay_title)
                setTextColor(Color.WHITE)
                textSize = 22f
                gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            },
        )
        container.addView(
            TextView(appContext).apply {
                text = appContext.getString(
                    when (packageName) {
                        SettingsRepository.YOUTUBE_PACKAGE -> R.string.punitive_youtube_blocked
                        SettingsRepository.INSTAGRAM_PACKAGE -> R.string.punitive_instagram_blocked
                        else -> R.string.app_name
                    },
                )
                setTextColor(Color.rgb(192, 201, 194))
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 8.dp, 0, 0)
            },
        )
        container.addView(
            TextView(appContext).apply {
                text = appContext.getString(R.string.punitive_time_remaining)
                setTextColor(Color.rgb(121, 214, 166))
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 18.dp, 0, 0)
            },
        )
        val countdownText = TextView(appContext).apply {
            setTextColor(Color.WHITE)
            textSize = 38f
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 2.dp, 0, 0)
        }
        container.addView(countdownText)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        val wasAdded = runCatching {
            windowManager.addView(container, layoutParams)
            true
        }.getOrDefault(false)

        if (!wasAdded) return

        currentView = container
        countdownRunnable = object : Runnable {
            override fun run() {
                if (currentView !== container) return
                val remaining = (blockedUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                countdownText.text = formatRemaining(remaining)
                if (remaining > 0L) {
                    handler.postDelayed(this, COUNTDOWN_TICK_MS)
                } else {
                    dismiss()
                }
            }
        }.also { runnable -> handler.post(runnable) }
        hideRunnable = Runnable(::dismiss).also { runnable ->
            handler.postDelayed(runnable, PUNITIVE_DISPLAY_DURATION_MS)
        }
    }

    fun dismiss() {
        hideRunnable?.let(handler::removeCallbacks)
        hideRunnable = null
        countdownRunnable?.let(handler::removeCallbacks)
        countdownRunnable = null

        currentView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        currentView = null
    }

    private val Int.dp: Int
        get() = (this * appContext.resources.displayMetrics.density).toInt()

    private fun formatRemaining(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis + 999L) / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    private companion object {
        const val DISPLAY_DURATION_MS = 2_400L
        const val PUNITIVE_DISPLAY_DURATION_MS = 5_000L
        const val COUNTDOWN_TICK_MS = 1_000L
    }
}
