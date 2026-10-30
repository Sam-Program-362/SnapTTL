package dev.snapttl.domain

/** Units a user may choose for an auto-delete schedule. */
enum class TimeUnit { SECONDS, MINUTES, HOURS, DAYS, NONE }

/**
 * Deletion-duration rules. SnapTTL is free — every duration is available to
 * everyone; the only limits are sanity limits.
 *
 * - The quick timer (floating overlay, Settings, bulk actions) accepts any
 *   duration from 1 second up to [MAX_SECONDS] (one week).
 * - Longer retention is possible only through the overlay's date/time
 *   affordance, up to [MAX_TARGET_AHEAD_DAYS] ahead.
 */
object DeletionDuration {

    const val SECONDS_PER_MINUTE: Long = 60L
    const val SECONDS_PER_HOUR: Long = 3_600L
    const val SECONDS_PER_DAY: Long = 86_400L

    /** Quick-timer cap: one week. */
    val MAX_SECONDS: Long = 7L * SECONDS_PER_DAY

    /** Date/time targets may be at most a year out. */
    const val MAX_TARGET_AHEAD_DAYS: Int = 365

    /** Used by silent auto-delete until the user picks something else. */
    const val DEFAULT_SECONDS: Long = 15L * SECONDS_PER_MINUTE

    /** Quick-timer validation: 1 s … 7 d. */
    fun isValidSeconds(seconds: Long): Boolean = seconds in 1..MAX_SECONDS

    /** Date/time validation: strictly in the future, at most a year out. */
    fun isValidTargetEpochMs(targetEpochMs: Long, nowEpochMs: Long): Boolean {
        if (targetEpochMs <= nowEpochMs) return false
        val maxAheadMs = MAX_TARGET_AHEAD_DAYS * SECONDS_PER_DAY * 1_000L
        return targetEpochMs - nowEpochMs <= maxAheadMs
    }

    /** Human label for round values: "30s", "45m", "3d". */
    fun label(seconds: Long): String = when {
        seconds >= SECONDS_PER_DAY && seconds % SECONDS_PER_DAY == 0L ->
            "${seconds / SECONDS_PER_DAY}d"
        seconds >= SECONDS_PER_HOUR && seconds % SECONDS_PER_HOUR == 0L ->
            "${seconds / SECONDS_PER_HOUR}h"
        seconds >= SECONDS_PER_MINUTE && seconds % SECONDS_PER_MINUTE == 0L ->
            "${seconds / SECONDS_PER_MINUTE}m"
        else -> "${seconds}s"
    }

    /** Human label for arbitrary values: "1h 30m", "2d 4h", "45s". */
    fun detailedLabel(seconds: Long): String {
        var rest = seconds.coerceAtLeast(0L)
        val days = rest / SECONDS_PER_DAY
        rest -= days * SECONDS_PER_DAY
        val hours = rest / SECONDS_PER_HOUR
        rest -= hours * SECONDS_PER_HOUR
        val minutes = rest / SECONDS_PER_MINUTE
        rest -= minutes * SECONDS_PER_MINUTE
        val secs = rest
        return buildList {
            if (days > 0) add("${days}d")
            if (hours > 0) add("${hours}h")
            if (minutes > 0) add("${minutes}m")
            if (secs > 0 || isEmpty()) add("${secs}s")
        }.joinToString(" ")
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
}
