package dev.snapttl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementStateTest {

    private val now = 1_700_000_000_000L

    @Test
    fun free_tier_allows_only_15_to_45_minutes() {
        val state = EntitlementState()
        assertTrue(state.isValidDeletionSchedule(15, TimeUnit.MINUTES, now))
        assertTrue(state.isValidDeletionSchedule(45, TimeUnit.MINUTES, now))
        assertFalse(state.isValidDeletionSchedule(14, TimeUnit.MINUTES, now))
        assertFalse(state.isValidDeletionSchedule(46, TimeUnit.MINUTES, now))
    }

    @Test
    fun free_tier_rejects_other_units() {
        val state = EntitlementState()
        assertFalse(state.isValidDeletionSchedule(1, TimeUnit.HOURS, now))
        assertFalse(state.isValidDeletionSchedule(1, TimeUnit.DAYS, now))
        assertFalse(state.isValidDeletionSchedule(15, TimeUnit.NONE, now))
        assertFalse(state.isValidDeletionSchedule(0, TimeUnit.MINUTES, now))
    }

    @Test
    fun rewarded_ad_refill_grants_20_days_capped_at_40_days() {
        val state = EntitlementState()
        assertEquals(20, state.applyRewardedAdRefill(now))
        assertEquals(40, state.applyRewardedAdRefill(now))
        assertEquals(40, state.applyRewardedAdRefill(now))
    }

    @Test
    fun refill_unlocks_out_of_range_durations() {
        val state = EntitlementState()
        assertFalse(state.isValidDeletionSchedule(10, TimeUnit.MINUTES, now))
        state.applyRewardedAdRefill(now)
        assertTrue(state.isValidDeletionSchedule(10, TimeUnit.MINUTES, now))
        assertTrue(state.isValidDeletionSchedule(2, TimeUnit.HOURS, now))
    }

    @Test
    fun premium_purchase_unlocks_everything_permanently() {
        val state = EntitlementState(premiumPurchased = true)
        assertTrue(state.isValidDeletionSchedule(1, TimeUnit.DAYS, now))
        assertTrue(state.hasUnlockedFeatures(now + 100L * 365 * 24 * 3600_000L))
    }

    @Test
    fun refill_expires_and_drops_unlock() {
        val state = EntitlementState()
        state.applyRewardedAdRefill(now)
        assertTrue(state.hasUnlockedFeatures(now))
        assertFalse(state.hasUnlockedFeatures(now + 41L * 24 * 3600_000L))
    }
}
