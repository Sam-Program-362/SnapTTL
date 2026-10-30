package dev.snapttl.domain

/** Units a user may choose for an auto-delete schedule. */
enum class TimeUnit { SECONDS, MINUTES, HOURS, DAYS, NONE }

/**
 * Deletion-duration rules. SnapTTL is free — every duration is available to
 * everyone; the only limits are sanity limits.
 *
 * - The user maintains their **own quick-delete options** (chips) that appear
 *   as one-tap buttons on the floating overlay — up to [MAX_CHIPS] of them,
 *   each between 1 second and [MAX_SECONDS] (one week).
 * - The overlay's "Custom…" button opens a free-form timer for anything else
 *   (same 1 s … 7 d range).
 * - The overlay's date/time affordance targets an explicit moment in the
 *   future, up to [MAX_TARGET_AHEAD_DAYS] ahead — the only way past a week.
 */
object DeletionDuration {

    const val SECONDS_PER_MINUTE: Long = 60L
    const val SECONDS_PER_HOUR: Long = 3_600L
    const val SECONDS_PER_DAY: Long = 86_400L

    /** Quick-timer cap: one week. */
    val MAX_SECONDS: Long = 7L * SECONDS_PER_DAY

    /** Date/time targets may be at most a year out. */
    const val MAX_TARGET_AHEAD_DAYS: Int = 365

    /** How many one-tap options the user may keep. */
    const val MAX_CHIPS: Int = 6

    /** Starter set (fully editable): 15 min, 1 h, 6 h, 1 day. */
    val DEFAULT_CHIPS: List<Long> = listOf(
        15L * SECONDS_PER_MINUTE,
        SECONDS_PER_HOUR,
        6L * SECONDS_PER_HOUR,
        SECONDS_PER_DAY
    )

    /** Quick-timer validation: 1 s … 7 d. */
    fun isValidSeconds(seconds: Long): Boolean = seconds in 1..MAX_SECONDS

    /** Date/time validation: strictly in the future, at most a year out. */
    fun isValidTargetEpochMs(targetEpochMs: Long, nowEpochMs: Long): Boolean {
        if (targetEpochMs <= nowEpochMs) return false
        val maxAheadMs = MAX_TARGET_AHEAD_DAYS * SECONDS_PER_DAY * 1_000L
        return targetEpochMs - nowEpochMs <= maxAheadMs
    }

    /** A user chip list: 1…[MAX_CHIPS] entries, each a valid duration, unique. */
    fun isValidChipList(chips: List<Long>): Boolean =
        chips.isNotEmpty() && chips.size <= MAX_CHIPS &&
            chips.all { isValidSeconds(it) } && chips.distinct().size == chips.size

    /**
     * Adds (`oldSeconds == null`) or replaces a quick option. Returns the new
     * sorted list, or null when the edit is rejected (invalid, duplicate, or
     * the list is already full).
     */
    fun upsertChip(chips: List<Long>, oldSeconds: Long?, newSeconds: Long): List<Long>? {
        if (!isValidSeconds(newSeconds)) return null
        if (newSeconds != oldSeconds && newSeconds in chips) return null
        val base = if (oldSeconds != null) chips - oldSeconds else chips
        if (oldSeconds == null && base.size >= MAX_CHIPS) return null
        return (base + newSeconds).sorted()
    }

    /**
     * Removes a quick option; returns null when the option is unknown or the
     * last remaining one would be removed.
     */
    fun removeChip(chips: List<Long>, seconds: Long): List<Long>? {
        if (chips.size <= 1) return null
        if (seconds !in chips) return null
        return (chips - seconds).sorted()
    }

    /** Serialises the chip list for SharedPreferences (comma-separated seconds). */
    fun encode(chips: List<Long>): String = chips.joinToString(",")

    /** Parses a persisted chip list; invalid entries are dropped, result sorted. */
    fun decode(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { isValidSeconds(it) }
            .distinct()
            .sorted()
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
