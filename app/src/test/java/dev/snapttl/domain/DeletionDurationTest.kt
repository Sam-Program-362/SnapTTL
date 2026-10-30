package dev.snapttl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deletion-duration rules: the quick timer accepts 1 s … 7 d for everyone, and
 * date/time targets must be strictly in the future within a year.
 */
class DeletionDurationTest {

    @Test
    fun quick_timer_accepts_one_second_to_seven_days() {
        assertTrue(DeletionDuration.isValidSeconds(1))
        assertTrue(DeletionDuration.isValidSeconds(30))
        assertTrue(DeletionDuration.isValidSeconds(900))
        assertTrue(DeletionDuration.isValidSeconds(7 * 86_400))
    }

    @Test
    fun quick_timer_rejects_zero_negative_and_beyond_a_week() {
        assertFalse(DeletionDuration.isValidSeconds(0))
        assertFalse(DeletionDuration.isValidSeconds(-60))
        assertFalse(DeletionDuration.isValidSeconds(7 * 86_400 + 1))
    }

    @Test
    fun datetime_target_must_be_future_and_within_a_year() {
        val now = 1_800_000_000_000L
        assertTrue(DeletionDuration.isValidTargetEpochMs(now + 1, now))
        assertTrue(DeletionDuration.isValidTargetEpochMs(now + 365L * 86_400_000, now))  // exactly a year
        assertFalse(DeletionDuration.isValidTargetEpochMs(now, now))                       // not the past
        assertFalse(DeletionDuration.isValidTargetEpochMs(now - 1, now))                   // not the past
        assertFalse(DeletionDuration.isValidTargetEpochMs(now + 366L * 86_400_000, now))   // over a year
        assertFalse(DeletionDuration.isValidTargetEpochMs(now + 400L * 86_400_000, now))   // over a year
    }

    @Test
    fun labels_use_the_largest_round_unit() {
        assertEquals("30s", DeletionDuration.label(30))
        assertEquals("15m", DeletionDuration.label(900))
        assertEquals("45m", DeletionDuration.label(2700))
        assertEquals("2h", DeletionDuration.label(7_200))
        assertEquals("3d", DeletionDuration.label(259_200))
        assertEquals("7d", DeletionDuration.label(DeletionDuration.MAX_SECONDS))
    }

    @Test
    fun detailed_labels_spell_out_every_part() {
        assertEquals("45s", DeletionDuration.detailedLabel(45))
        assertEquals("1h 30m", DeletionDuration.detailedLabel(5_400))
        assertEquals("2d 4h 30m", DeletionDuration.detailedLabel(2 * 86_400 + 4 * 3_600 + 1_800))
        assertEquals("0s", DeletionDuration.detailedLabel(0))
    }

    @Test
    fun unit_and_value_picks_the_largest_exact_unit() {
        assertEquals(15 to TimeUnit.MINUTES, DeletionDuration.unitAndValue(900))
        assertEquals(30 to TimeUnit.SECONDS, DeletionDuration.unitAndValue(30))
        assertEquals(2 to TimeUnit.HOURS, DeletionDuration.unitAndValue(7_200))
        assertEquals(3 to TimeUnit.DAYS, DeletionDuration.unitAndValue(259_200))
        assertEquals(90 to TimeUnit.SECONDS, DeletionDuration.unitAndValue(90)) // 1.5 min stays exact
        assertEquals(7 to TimeUnit.DAYS, DeletionDuration.unitAndValue(DeletionDuration.MAX_SECONDS))
    }

    @Test
    fun starter_options_are_four_valid_unique_chips() {
        assertEquals(listOf(900L, 3_600L, 21_600L, 86_400L), DeletionDuration.DEFAULT_CHIPS)
        assertTrue(DeletionDuration.isValidChipList(DeletionDuration.DEFAULT_CHIPS))
    }

    @Test
    fun slider_ladder_is_sorted_unique_and_within_the_quick_range() {
        val ladder = DeletionDuration.LADDER
        assertEquals(ladder.sorted(), ladder)                            // ascending
        assertEquals(ladder.distinct().size, ladder.size)                // no duplicates
        assertTrue(ladder.all { DeletionDuration.isValidSeconds(it) })  // 1 s … 7 d
        assertEquals(5L, ladder.first())                                 // 5 s
        assertEquals(DeletionDuration.MAX_SECONDS, ladder.last())       // 7 d
        assertEquals(29, ladder.size)
    }

    @Test
    fun nearest_ladder_index_snaps_exactly_and_by_closeness() {
        val ladder = DeletionDuration.LADDER
        // Exact values map to themselves.
        ladder.forEachIndexed { i, value ->
            assertEquals(i, DeletionDuration.nearestLadderIndex(value))
        }
        // Out-of-range values clamp to the ends.
        assertEquals(0, DeletionDuration.nearestLadderIndex(Long.MIN_VALUE))
        assertEquals(0, DeletionDuration.nearestLadderIndex(0))
        assertEquals(ladder.lastIndex, DeletionDuration.nearestLadderIndex(Long.MAX_VALUE))
        // In-between values snap to the nearest rung.
        assertEquals(0, DeletionDuration.nearestLadderIndex(7))    // 5 s vs 10 s, equidistant → lower
        assertEquals(1, DeletionDuration.nearestLadderIndex(8))    // closer to 10 s
        assertEquals(
            ladder.indexOf(90L * 60),
            DeletionDuration.nearestLadderIndex(90L * 60 + 600)    // 10 min past 90 m
        )
        // A default chip lands on a rung.
        DeletionDuration.DEFAULT_CHIPS.forEach { chip ->
            assertEquals(chip, ladder[DeletionDuration.nearestLadderIndex(chip)])
        }
    }

    @Test
    fun chip_list_rules() {
        assertTrue(DeletionDuration.isValidChipList(listOf(30L)))
        assertTrue(
            DeletionDuration.isValidChipList(
                List(DeletionDuration.MAX_CHIPS) { (it + 1) * 60L }
            )
        )
        assertFalse(DeletionDuration.isValidChipList(emptyList()))                       // at least one
        assertFalse(DeletionDuration.isValidChipList(List(7) { (it + 1) * 60L }))        // too many
        assertFalse(DeletionDuration.isValidChipList(listOf(60L, 60L)))                  // duplicates
        assertFalse(DeletionDuration.isValidChipList(listOf(60L, 0L)))                   // invalid entry
    }

    @Test
    fun upsert_adds_edits_and_rejects_bad_input() {
        val base = listOf(900L, 3_600L)

        // Add.
        assertEquals(listOf(900L, 3_600L, 86_400L), DeletionDuration.upsertChip(base, null, 86_400L))
        // Duplicate add is rejected.
        assertNull(DeletionDuration.upsertChip(base, null, 900L))
        // Invalid value is rejected.
        assertNull(DeletionDuration.upsertChip(base, null, 0L))
        // Full list rejects adds but accepts edits.
        val full = List(6) { (it + 1) * 60L }
        assertNull(DeletionDuration.upsertChip(full, null, 86_400L))
        assertEquals(
            listOf(60L, 120L, 180L, 240L, 300L, 3_600L),
            DeletionDuration.upsertChip(full, 360L, 3_600L)
        )
        // Edit to an existing value is rejected.
        assertNull(DeletionDuration.upsertChip(base, 900L, 3_600L))
    }

    @Test
    fun remove_never_empties_the_list() {
        assertNull(DeletionDuration.removeChip(listOf(900L), 900L))              // last one stays
        assertEquals(listOf(900L), DeletionDuration.removeChip(listOf(900L, 3_600L), 3_600L))
        assertNull(DeletionDuration.removeChip(listOf(900L, 3_600L), 42L))       // unknown value
    }

    @Test
    fun encode_decode_round_trips() {
        val chips = listOf(900L, 3_600L, 86_400L)
        assertEquals(chips, DeletionDuration.decode(DeletionDuration.encode(chips)))
        assertEquals(listOf(900L), DeletionDuration.decode("abc, 900, -5, 0"))
        assertEquals(emptyList<Long>(), DeletionDuration.decode(null))
        assertEquals(emptyList<Long>(), DeletionDuration.decode("  "))
        // Out-of-range entries are dropped on decode.
        assertEquals(listOf(60L), DeletionDuration.decode("60, ${DeletionDuration.MAX_SECONDS + 1}"))
    }
}
