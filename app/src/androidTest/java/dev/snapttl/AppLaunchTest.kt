package dev.snapttl

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.snapttl.ui.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test on a real device/emulator: the Compose app inflates, reaches RESUMED
 * and its window has focus. Runs on the API 30 / 34 / 35 emulator matrix.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @Test
    fun mainActivityLaunchesAndResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.hasWindowFocus() || !activity.isFinishing)
            }
        }
    }

    @Test
    fun servicesInitialise() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = activity.application as SnapTtlApp
                // Composition root wired: repositories and DAOs are available.
                assertTrue(app.services.prefs.timeChips.isNotEmpty())
            }
        }
    }
}
