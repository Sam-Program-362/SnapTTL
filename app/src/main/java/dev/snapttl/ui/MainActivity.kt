package dev.snapttl.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.snapttl.SnapTtlApp
import dev.snapttl.language.LocaleHelper
import dev.snapttl.ui.theme.SnapTtlTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SnapTtlApp
        // Crash resilience: bring the monitor back if it was enabled but is not
        // running (the app is in the foreground here, so this is always allowed).
        if (app.services.prefs.serviceEnabled && !dev.snapttl.service.CaptureMonitorService.isRunning) {
            dev.snapttl.service.CaptureMonitorService.start(this)
        }
        // Reconcile the expiry queue on app open (boot covers reboots; this covers
        // process death without a reboot).
        app.reconcileQueue()
        setContent {
            SnapTtlTheme {
                CompositionLocalProvider(
                    LocalSnapTtlFactory provides ViewModelFactory(app.services)
                ) {
                    SnapTtlRoot(services = app.services)
                }
            }
        }
    }
}
