package dev.snapttl.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import dev.snapttl.SnapTtlApp
import dev.snapttl.ui.theme.SnapTtlTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as SnapTtlApp
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
