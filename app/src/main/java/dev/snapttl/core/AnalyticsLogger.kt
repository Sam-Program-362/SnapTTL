package dev.snapttl.core

/**
 * Analytics boundary. The clean-room app keeps this as an interface so the core
 * never depends on a concrete telemetry SDK; production wiring can substitute a
 * Firebase implementation without touching domain/service code.
 */
interface AnalyticsLogger {
    fun logEvent(name: String, params: Map<String, Any?> = emptyMap())
    fun logError(message: String, throwable: Throwable? = null)

    object Noop : AnalyticsLogger {
        override fun logEvent(name: String, params: Map<String, Any?>) = Unit
        override fun logError(message: String, throwable: Throwable?) = Unit
    }
}
