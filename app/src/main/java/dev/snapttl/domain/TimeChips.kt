package dev.snapttl.domain

/**
 * Deletion-duration chips, reconstructed from Mark's time-chip model.
 *
 * Durations are stored in **seconds** so the original advanced option of 30 seconds
 * is representable alongside 15/45 minutes and 3 days.
 *
 * - Free tier: 15 and 45 minutes (the only durations a free user may arm).
 * - Advanced (premium/refill): 30 s, 5 min, 30 min, 3 days.
 */
object TimeChips {

    const val SECONDS_PER_MINUTE: Long = 60L
    const val SECONDS_PER_HOUR: Long = 3_600L
    const val SECONDS_PER_DAY: Long = 86_400L

    /** Chips every install starts with (free tier). */
    val FREE: List<Long> = listOf(15 * SECONDS_PER_MINUTE, 45 * SECONDS_PER_MINUTE)

    /** Extra chips unlocked by premium or an active ad-refill. */
    val ADVANCED: List<Long> = listOf(
        30L,                       // 30 seconds
        5 * SECONDS_PER_MINUTE,    // 5 minutes
        30 * SECONDS_PER_MINUTE,   // 30 minutes
        3 * SECONDS_PER_DAY        // 3 days
    )

    /** Chips a user with [unlocked] features may arm. */
    fun allowed(unlocked: Boolean): List<Long> =
        if (unlocked) FREE + ADVANCED else FREE

    /** Human label: "30s", "45m", "3d". */
    fun label(seconds: Long): String = when {
        seconds >= SECONDS_PER_DAY && seconds % SECONDS_PER_DAY == 0L ->
            "${seconds / SECONDS_PER_DAY}d"
        seconds >= SECONDS_PER_HOUR && seconds % SECONDS_PER_HOUR == 0L ->
            "${seconds / SECONDS_PER_HOUR}h"
        seconds >= SECONDS_PER_MINUTE && seconds % SECONDS_PER_MINUTE == 0L ->
            "${seconds / SECONDS_PER_MINUTE}m"
        else -> "${seconds}s"
    }

    /** Canonical (value, unit) pair for the `timeUnit`/value columns. */
    fun unitAndValue(seconds: Long): Pair<Int, TimeUnit> = when {
        seconds >= SECONDS_PER_DAY && seconds % SECONDS_PER_DAY == 0L ->
            (seconds / SECONDS_PER_DAY).toInt() to TimeUnit.DAYS
        seconds >= SECONDS_PER_HOUR && seconds % SECONDS_PER_HOUR == 0L ->
            (seconds / SECONDS_PER_HOUR).toInt() to TimeUnit.HOURS
        seconds >= SECONDS_PER_MINUTE && seconds % SECONDS_PER_MINUTE == 0L ->
            (seconds / SECONDS_PER_MINUTE).toInt() to TimeUnit.MINUTES
        else -> seconds.toInt() to TimeUnit.SECONDS
    }

    /** Serialises a chip selection for SharedPreferences (comma-separated seconds). */
    fun encode(chips: List<Long>): String = chips.joinToString(",")

    /** Parses a persisted chip selection; invalid entries are dropped, result is sorted. */
    fun decode(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L }
            .distinct()
            .sorted()
    }
}
