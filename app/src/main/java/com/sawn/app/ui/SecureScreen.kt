package com.sawn.app.ui

import android.app.Activity
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import com.sawn.app.BuildConfig

/**
 * Marks the current screen as security-sensitive while it's composed:
 * - FLAG_SECURE (release builds only, so debug can still be screenshotted) blocks
 *   screenshots / screen recording of the PIN.
 * - filterTouchesWhenObscured + setHideOverlayWindows defeat tapjacking overlays that could
 *   trick the user into entering or revealing the PIN.
 *
 * Modals compose in their own window whose context is a [ContextThemeWrapper] chain, not the
 * Activity — `view.context as? Activity` fails silently there. We therefore walk the context
 * chain to find the hosting Activity AND secure the view's own window when it already is one
 * (dialog decor views expose their window directly).
 */
@Composable
fun SecureScreen() {
    val view = LocalView.current
    DisposableEffect(view) {
        val applySecure = !BuildConfig.DEBUG

        // The window this view actually renders into (works for dialogs too).
        fun ownWindow(): Window? = runCatching { view.context.getSystemService(Window::class.java) }
            .getOrNull()

        // Activity underneath, following ContextWrapper/ContextThemeWrapper chains.
        fun hostingActivity(): Activity? {
            var ctx = view.context
            var guard = 0
            while (ctx != null && guard++ < 10) {
                if (ctx is Activity) return ctx
                ctx = (ctx as? ContextThemeWrapper)?.baseContext
                    ?: (ctx as? android.content.ContextWrapper)?.baseContext
            }
            return null
        }

        val windows = buildList {
            ownWindow()?.let(::add)
            hostingActivity()?.window?.let(::add)
        }.distinct()

        for (window in windows) {
            if (applySecure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            runCatching {
                // May throw on windows we don't own (e.g. an activity behind a dialog).
                window.decorView.filterTouchesWhenObscured = true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { window.setHideOverlayWindows(true) }
            }
        }

        onDispose {
            for (window in windows) {
                if (applySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                runCatching { window.decorView.filterTouchesWhenObscured = false }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching { window.setHideOverlayWindows(false) }
                }
            }
        }
    }
}
