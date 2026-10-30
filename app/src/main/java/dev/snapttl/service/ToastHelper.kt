package dev.snapttl.service

import android.content.Context
import android.widget.Toast

/** Small helper so services/workers can show toasts without a Compose context. */
object ToastHelper {
    fun show(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
