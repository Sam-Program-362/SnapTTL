package dev.snapttl

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import dev.snapttl.data.expiry.ExpiryDatabase
import dev.snapttl.data.repository.ExpiryRepository
import dev.snapttl.data.repository.ScreenshotRepository
import dev.snapttl.data.repository.SearchRepository
import dev.snapttl.data.search.SearchIndexDatabase
import dev.snapttl.di.Services
import dev.snapttl.language.LocaleHelper
import dev.snapttl.service.NotificationChannels
import dev.snapttl.state.CommonState
import dev.snapttl.vision.MlKitScreenshotVision

class SnapTtlApp : Application() {

    lateinit var services: Services
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        services = buildServices()
        LocaleHelper.apply(this, services.prefs.languageCode)
    }

    private fun buildServices(): Services {
        val container = Services(this)
        val expiryDb = Room.databaseBuilder(
            this,
            ExpiryDatabase::class.java,
            "snapttl_expiry.db"
        ).build()
        val searchDb = Room.databaseBuilder(
            this,
            SearchIndexDatabase::class.java,
            "snapttl_search.db"
        ).build()

        container.expiryDao = expiryDb.expiryQueueDao()
        container.searchDao = searchDb.searchIndexDao()
        container.screenshotRepo = ScreenshotRepository(this, container.prefs)
        container.expiryRepo = ExpiryRepository(container.expiryDao, WorkManager.getInstance(this))
        container.vision = MlKitScreenshotVision(this)
        container.searchRepo = SearchRepository(container.searchDao, container.vision, container.screenshotRepo)

        container.commonState.update {
            CommonState(
                serviceEnabled = container.prefs.serviceEnabled,
                premiumPurchased = container.prefs.havePremiumPurchased,
                adsRemoved = container.prefs.haveAdsRemoved,
                refillDaysLeft = EntitlementDays.daysLeft(container.prefs.refillExpiryEpochMs),
                timeChips = container.prefs.timeChips,
                noDialogAutoDelete = container.prefs.noDialogAutoDeleteEnabled
            )
        }
        return container
    }
}

/** Shared refill computation so the Application and services agree on the cap. */
private object EntitlementDays {
    private val DAY_MS = 24L * 60L * 60L * 1000L
    fun daysLeft(expiryMs: Long): Int {
        if (expiryMs <= 0L) return 0
        return ((expiryMs - System.currentTimeMillis()) / DAY_MS).coerceIn(0L, 40L).toInt()
    }
}
