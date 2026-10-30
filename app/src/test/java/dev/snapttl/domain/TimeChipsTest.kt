package dev.snapttl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeChipsTest {

    @Test
    fun free_chips_are_15_and_45_minutes() {
        assertEquals(listOf(900L, 2700L), TimeChips.FREE)
    }

    @Test
    fun advanced_chips_cover_the_original_durations() {
        assertEquals(
            listOf(30L, 300L, 1800L, 259_200L),
            TimeChips.ADVANCED
        )
    }

    @Test
    fun allowed_chips_expand_with_unlock() {
        assertEquals(TimeChips.FREE, TimeChips.allowed(unlocked = false))
        assertEquals(TimeChips.FREE + TimeChips.ADVANCED, TimeChips.allowed(unlocked = true))
    }

    @Test
    fun labels_render_seconds_minutes_hours_and_days() {
        assertEquals("30s", TimeChips.label(30))
        assertEquals("15m", TimeChips.label(900))
        assertEquals("45m", TimeChips.label(2700))
        assertEquals("2h", TimeChips.label(7_200))
        assertEquals("3d", TimeChips.label(259_200))
    }

    @Test
    fun unit_and_value_is_canonical() {
        assertEquals(15 to TimeUnit.MINUTES, TimeChips.unitAndValue(900))
        assertEquals(30 to TimeUnit.SECONDS, TimeChips.unitAndValue(30))
        assertEquals(2 to TimeUnit.HOURS, TimeChips.unitAndValue(7_200))
        assertEquals(3 to TimeUnit.DAYS, TimeChips.unitAndValue(259_200))
        assertEquals(90 to TimeUnit.SECONDS, TimeChips.unitAndValue(90)) // 1.5 min stays exact
    }

    @Test
    fun encode_decode_round_trips_sorted_and_deduped() {
        val chips = listOf(2700L, 900L, 900L)
        assertEquals(listOf(900L, 2700L), TimeChips.decode(TimeChips.encode(chips)))
    }

    @Test
    fun decode_drops_garbage_entries() {
        assertEquals(listOf(900L), TimeChips.decode("abc, 900, -5, 0"))
        assertEquals(emptyList<Long>(), TimeChips.decode(null))
        assertEquals(emptyList<Long>(), TimeChips.decode("  "))
    }
}
