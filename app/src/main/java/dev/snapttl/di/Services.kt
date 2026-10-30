package dev.snapttl.di

import android.content.Context
import dev.snapttl.core.AnalyticsLogger
import dev.snapttl.data.expiry.ExpiryQueueDao
import dev.snapttl.data.prefs.PrefsManager
import dev.snapttl.data.repository.ExpiryRepository
import dev.snapttl.data.repository.ScreenshotRepository
import dev.snapttl.data.repository.SearchRepository
import dev.snapttl.data.search.SearchIndexDao
import dev.snapttl.state.CommonStateHolder
import dev.snapttl.vision.ScreenshotVision
import kotlinx.coroutines.flow.StateFlow

/** Application-scoped composition root for the clean-room app. */
class Services(context: Context) {
    val context: Context = context.applicationContext
    val prefs = PrefsManager(context)
    val commonState = CommonStateHolder()
    val analytics: AnalyticsLogger = AnalyticsLogger.Noop

    lateinit var expiryDao: ExpiryQueueDao
    lateinit var searchDao: SearchIndexDao
    lateinit var screenshotRepo: ScreenshotRepository
    lateinit var expiryRepo: ExpiryRepository
    lateinit var searchRepo: SearchRepository
    lateinit var vision: ScreenshotVision

    val commonStateFlow: StateFlow<dev.snapttl.state.CommonState> get() = commonState.state
}
