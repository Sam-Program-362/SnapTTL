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
import dev.snapttl.domain.EntitlementState
import dev.snapttl.language.LocaleHelper
import dev.snapttl.service.NotificationChannels
import dev.snapttl.state.CommonState
import dev.snapttl.vision.MlKitScreenshotVision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SnapTtlApp : Application() {

    lateinit var services: Services
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        val entitlement = entitlement(container)
        container.commonState.update {
            CommonState(
                serviceEnabled = container.prefs.serviceEnabled,
                premiumPurchased = container.prefs.havePremiumPurchased,
                adsRemoved = container.prefs.haveAdsRemoved,
                refillDaysLeft = entitlement.refillDaysLeft(System.currentTimeMillis()),
                timeChips = container.prefs.timeChips,
                noDialogAutoDelete = container.prefs.noDialogAutoDeleteEnabled
            )
        }
        return container
    }

    private fun entitlement(container: Services) = EntitlementState(
        premiumPurchased = container.prefs.havePremiumPurchased,
        adsRemoved = container.prefs.haveAdsRemoved,
        refillExpiryEpochMs = container.prefs.refillExpiryEpochMs
    )

    /**
     * Runs the expiry-queue reconcile (crash/boot safety net) once per app open.
     * Application.onCreate stays side-effect-light so background WorkManager
     * traffic never races app startup (or unit tests).
     */
    fun reconcileQueue() {
        appScope.launch { services.expiryRepo.rescheduleAll() }
    }
}
