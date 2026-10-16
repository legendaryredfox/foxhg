package io.github.legendaryredfox.foxhg.core.data.pickup

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/** What one Pickup roll (0 to 99) gives: the [common] table slot (0 to 8), the [rare] one (0 or 1), or neither. */
data class PickupOutcome(val common: Int? = null, val rare: Int? = null)

/**
 * The Pickup ability data of overlay 12: [common] (18 items) and [rare] (11 items) tables, the [divisor] of the
 * activation check and the nine cumulative [weights]. After a battle a Pokémon with Pickup and no held item activates
 * when a random number modulo [divisor] is 0, then rolls 0 to 99: the first slot j whose weight is above the roll gives
 * `common[bracket + j]`, a roll of 98 or 99 that no weight catches gives `rare[bracket + 99 - roll]`, where bracket is
 * (level - 1) / 10 capped at 9. From the decomp's `BtlCmd_GenerateEndOfBattleItem` and DSPRE's `PickupTableEditor`.
 */
data class PickupTable(val common: List<Int>, val rare: List<Int>, val divisor: Int, val weights: List<Int>) {
    init {
        require(common.size == COMMON && rare.size == RARE && weights.size == SLOTS) { "$COMMON common items, $RARE rare items, $SLOTS weights" }
    }

    /** The common item a Pokémon of level bracket [bracket] (0 to 9) gets from [slot] (0 to 8). */
    fun commonItem(bracket: Int, slot: Int): Int = common[bracket + slot]

    /** The rare item for [bracket] and [slot] (0 or 1). */
    fun rareItem(bracket: Int, slot: Int): Int = rare[bracket + slot]

    /** What a roll of [roll] (0 to 99) gives, as the game decides it. */
    fun outcome(roll: Int): PickupOutcome {
        for (j in 0 until SLOTS) {
            if (weights[j] > roll) return PickupOutcome(common = j)
            if (roll in 98..99) return PickupOutcome(rare = 99 - roll)
        }
        return PickupOutcome()
    }

    /** Percent of the rolls that land on each common slot (so 0 for a slot a lower weight hides). */
    fun commonRolls(): List<Int> = List(SLOTS) { j -> (0 until 100).count { outcome(it).common == j } }

    /** Percent of the rolls that give the rare slot 0 and 1, and the rolls that give nothing. */
    fun rareRolls(): List<Int> = List(2) { k -> (0 until 100).count { outcome(it).rare == k } }
    fun missRolls(): Int = (0 until 100).count { outcome(it) == PickupOutcome() }

    /** Chance in percent that the ability activates at all after a battle: 1 in [divisor]. */
    val activationPercent: Double get() = 100.0 / divisor

    fun withCommon(index: Int, item: Int) = copy(common = common.toMutableList().also { it[index] = item })
    fun withRare(index: Int, item: Int) = copy(rare = rare.toMutableList().also { it[index] = item })
    fun withWeight(index: Int, weight: Int) = copy(weights = weights.toMutableList().also { it[index] = weight })

    /** Writes the table into a copy of [overlay]. */
    fun applyTo(overlay: ByteArray): ByteArray = overlay.copyOf().also { out ->
        common.forEachIndexed { i, item -> out.putU16(HgssLayout.PICKUP_COMMON_ITEMS + 2 * i, item) }
        rare.forEachIndexed { i, item -> out.putU16(HgssLayout.PICKUP_RARE_ITEMS + 2 * i, item) }
        out[HgssLayout.PICKUP_DIVISOR] = divisor.toByte()
        weights.forEachIndexed { i, w -> out[HgssLayout.PICKUP_WEIGHTS + i] = w.toByte() }
    }

    fun save(project: RomProject): FoxResult<Unit> = when (val o = project.readOverlay(HgssLayout.PICKUP_OVERLAY)) {
        is FoxResult.Ok -> project.writeOverlay(HgssLayout.PICKUP_OVERLAY, applyTo(o.value))
        is FoxResult.Err -> o
    }

    companion object {
        const val COMMON = 18
        const val RARE = 11
        const val SLOTS = 9
        const val BRACKETS = 10

        /** "1-10", "11-20", ... "91-100". */
        fun levelRange(bracket: Int): String = "${bracket * 10 + 1}-${(bracket + 1) * 10}"

        fun parse(overlay: ByteArray): FoxResult<PickupTable> {
            val end = maxOf(HgssLayout.PICKUP_COMMON_ITEMS + 2 * COMMON, HgssLayout.PICKUP_RARE_ITEMS + 2 * RARE, HgssLayout.PICKUP_WEIGHTS + SLOTS, HgssLayout.PICKUP_DIVISOR + 1)
            if (overlay.size < end) return FoxResult.Err("Overlay ${HgssLayout.PICKUP_OVERLAY} is too small for the Pickup tables")
            return FoxResult.Ok(
                PickupTable(
                    List(COMMON) { overlay.u16(HgssLayout.PICKUP_COMMON_ITEMS + 2 * it) },
                    List(RARE) { overlay.u16(HgssLayout.PICKUP_RARE_ITEMS + 2 * it) },
                    overlay[HgssLayout.PICKUP_DIVISOR].toInt() and 0xFF,
                    List(SLOTS) { overlay[HgssLayout.PICKUP_WEIGHTS + it].toInt() and 0xFF },
                ),
            )
        }

        fun load(project: RomProject): FoxResult<PickupTable> = when (val o = project.readOverlay(HgssLayout.PICKUP_OVERLAY)) {
            is FoxResult.Ok -> parse(o.value)
            is FoxResult.Err -> o
        }
    }
}
