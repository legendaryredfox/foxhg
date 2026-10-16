package io.github.legendaryredfox.foxhg.core.data.growth

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u32

/**
 * One growth rate: [exp] holds the total experience needed at levels 0 to 100 (so 101 values, 404 bytes of u32). The
 * game looks levels up in this table instead of computing them. Layout from the DS Pokémon Hacking wiki's growth rate
 * guide and the decomp's `LoadGrowthTable`.
 */
data class GrowthCurve(val exp: List<Int>) {
    init { require(exp.size == LEVELS + 1) { "${LEVELS + 1} values" } }

    /** Experience needed to go from [level] to the next one. */
    fun toNext(level: Int): Int = exp[level + 1] - exp[level]

    /**
     * Sets the total at [level] (2 to 100) to [total]. With [shiftLater] every later level moves by the same amount,
     * which keeps the experience per level as it was (what the guide says to do); without it only this level changes.
     */
    fun withLevel(level: Int, total: Int, shiftLater: Boolean = true): GrowthCurve {
        val delta = total - exp[level]
        return GrowthCurve(exp.mapIndexed { l, v -> if (l == level) total else if (shiftLater && l > level) v + delta else v })
    }

    /** Levels whose total is below the one before: the game would count backwards there. */
    fun problems(): List<Int> = (2..LEVELS).filter { exp[it] < exp[it - 1] }

    fun toBytes(): ByteArray {
        val out = ByteArray(SIZE)
        exp.forEachIndexed { i, v -> out.putU32(i * 4, v.toLong()) }
        return out
    }

    companion object {
        const val LEVELS = 100
        const val SIZE = (LEVELS + 1) * 4

        /** Names in the order of the personal data's growth rate field; the last two are unused and equal to Medium Fast. */
        val NAMES = listOf("Medium Fast", "Erratic", "Fluctuating", "Medium Slow", "Fast", "Slow", "Unused 1", "Unused 2")

        fun parse(bytes: ByteArray): FoxResult<GrowthCurve> {
            if (bytes.size != SIZE) return FoxResult.Err("A growth table is $SIZE bytes, got ${bytes.size}")
            return FoxResult.Ok(GrowthCurve(List(LEVELS + 1) { bytes.u32(it * 4).toInt() }))
        }

        val SPEC = RecordSpec(HgssLayout.GROWTH_RATES, ::parse, GrowthCurve::toBytes)
    }
}
