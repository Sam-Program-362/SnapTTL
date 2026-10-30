package dev.snapttl.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniGameTest {

    @Test
    fun ranks_scale_with_score() {
        assertEquals(MiniGameRank.NOVICE, MiniGame.rankFor(0))
        assertEquals(MiniGameRank.NOVICE, MiniGame.rankFor(9))
        assertEquals(MiniGameRank.STEADY, MiniGame.rankFor(10))
        assertEquals(MiniGameRank.STEADY, MiniGame.rankFor(24))
        assertEquals(MiniGameRank.SHARP, MiniGame.rankFor(25))
        assertEquals(MiniGameRank.SHARP, MiniGame.rankFor(39))
        assertEquals(MiniGameRank.LEGEND, MiniGame.rankFor(40))
    }

    @Test
    fun every_rank_has_a_title() {
        MiniGameRank.values().forEach { rank ->
            assertEquals(rank, MiniGame.rankFor(titleToScore(rank)))
        }
    }

    private fun titleToScore(rank: MiniGameRank): Int = when (rank) {
        MiniGameRank.NOVICE -> 5
        MiniGameRank.STEADY -> 15
        MiniGameRank.SHARP -> 30
        MiniGameRank.LEGEND -> 45
    }
}
