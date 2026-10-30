package dev.snapttl.domain

/** Units a user may choose for an auto-delete schedule. */
enum class TimeUnit { SECONDS, MINUTES, HOURS, DAYS, NONE }

/**
 * Freemium / rewarded-refill / one-time-premium entitlement state machine.
 *
 * Free tier: deletion durations between 15 and 45 minutes only.
 * Rewarded ad refill: adds 20 days, hard capped at 40 days, unlocks durations
 * outside 15-45 minutes and the extra time chips.
 * Premium purchase: unlocks everything permanently (including silent auto-delete).
 */
class EntitlementState(
    var premiumPurchased: Boolean = false,
    var adsRemoved: Boolean = false,
    var refillExpiryEpochMs: Long = 0L
) {
    val DAY_MS: Long = 24L * 60L * 60L * 1000L
    val REFILL_MAX_DAYS: Int = 40
    val REFILL_GRANT_DAYS: Int = 20
    val FREE_MIN_MINUTES: Int = 15
    val FREE_MAX_MINUTES: Int = 45

    fun refillDaysLeft(nowMs: Long): Int {
        if (refillExpiryEpochMs <= nowMs) return 0
        val days = (refillExpiryEpochMs - nowMs) / DAY_MS
        return days.coerceIn(0L, REFILL_MAX_DAYS.toLong()).toInt()
    }

    fun hasUnlockedFeatures(nowMs: Long): Boolean =
        premiumPurchased || refillDaysLeft(nowMs) > 0

    /** Applies a rewarded-ad refill and returns the resulting days left. */
    fun applyRewardedAdRefill(nowMs: Long): Int {
        val base = maxOf(nowMs, refillExpiryEpochMs)
        refillExpiryEpochMs = minOf(nowMs + REFILL_MAX_DAYS * DAY_MS, base + REFILL_GRANT_DAYS * DAY_MS)
        return refillDaysLeft(nowMs)
    }

    fun isValidDeletionSchedule(value: Int, unit: TimeUnit, nowMs: Long): Boolean {
        if (value <= 0 || unit == TimeUnit.NONE) return false
        if (hasUnlockedFeatures(nowMs)) return true
        return unit == TimeUnit.MINUTES && value in FREE_MIN_MINUTES..FREE_MAX_MINUTES
    }

    /**
     * Chip-based validation used by the UI and the capture service. A free user may
     * arm any duration between 15 and 45 minutes; premium/refill unlocks everything.
     */
    fun isValidDeletionScheduleSeconds(seconds: Long, nowMs: Long): Boolean {
        if (seconds <= 0L) return false
        if (hasUnlockedFeatures(nowMs)) return true
        val min = FREE_MIN_MINUTES * 60L
        val max = FREE_MAX_MINUTES * 60L
        return seconds in min..max
    }

    companion object {
        /** Default time chips available to free users (in minutes). */
        val FREE_TIME_CHIPS: List<Int> = listOf(15, 45)
    }
}
