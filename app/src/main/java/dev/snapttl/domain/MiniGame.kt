package dev.snapttl.domain

/** Cell state for the hidden "Sweep Blitz" mini-game grid. */
data class MiniGameCell(
    val isActive: Boolean = false,
    val activatedAtMs: Long = 0L
)

enum class MiniGameState { READY, PLAYING, FINISHED }

enum class MiniGameRank { NOVICE, STEADY, SHARP, LEGEND }

/**
 * Clean-room rename of Mark's "Shred Frenzy" easter egg. Same rank ladder, own branding.
 */
object MiniGame {

    private val NOVICE_MAX = 9
    private val STEADY_MAX = 24
    private val SHARP_MAX = 39

    val TITLES: Map<MiniGameRank, String> = mapOf(
        MiniGameRank.NOVICE to "Rookie Shredder — keep practising!",
        MiniGameRank.STEADY to "Storage Saver — nice reflexes!",
        MiniGameRank.SHARP to "Shred Master — impressive!",
        MiniGameRank.LEGEND to "Shredding Legend — are you even human?"
    )

    fun rankFor(score: Int): MiniGameRank = when {
        score <= NOVICE_MAX -> MiniGameRank.NOVICE
        score <= STEADY_MAX -> MiniGameRank.STEADY
        score <= SHARP_MAX -> MiniGameRank.SHARP
        else -> MiniGameRank.LEGEND
    }

    fun titleFor(score: Int): String = TITLES.getValue(rankFor(score))
}
