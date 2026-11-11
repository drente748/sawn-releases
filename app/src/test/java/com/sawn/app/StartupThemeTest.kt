package com.sawn.app

import android.app.Application
import android.os.Bundle
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercises AppCompat's real window setup with the launcher theme, without Hilt/Compose. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [26])
@LooperMode(LooperMode.Mode.PAUSED)
class StartupThemeTest {
    @Test
    @Config(qualifiers = "notnight")
    fun launcherTheme_supportsAppCompat_inLightMode() = assertActivityStarts()

    @Test
    @Config(qualifiers = "night")
    fun launcherTheme_supportsAppCompat_inDarkMode() = assertActivityStarts()

    private fun assertActivityStarts() {
        Robolectric.buildActivity(StartupThemeActivity::class.java).use { controller ->
            val activity = controller.setup().get()
            assertNotNull(activity.findViewById<FrameLayout>(android.R.id.content))
        }
    }
}

class StartupThemeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Sawn)
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
    }
}
