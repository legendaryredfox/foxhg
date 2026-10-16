package io.github.legendaryredfox.foxhg.core.data.battletower

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/**
 * A Battle Tower trainer (a file of `a/2/0/2`): the [trainerClass] and the numbers of the Pokémon sets ([BattleTowerSet],
 * files of `a/2/0/3`) it picks its team from. 4 bytes plus 2 per set, padded with zeros to a multiple of 4 as the game's files are.
 * Layout from DSPRE's `BattleTowerTrainerFile`.
 */
data class BattleTowerTrainer(val trainerClass: Int, val setIds: List<Int>) {
    fun toBytes(): ByteArray = ByteArray(sizeFor(setIds.size)).also { out ->
        out.putU16(0, trainerClass)
        out.putU16(2, setIds.size)
        setIds.forEachIndexed { i, id -> out.putU16(4 + 2 * i, id) }
    }

    fun withSet(index: Int, id: Int) = copy(setIds = setIds.toMutableList().also { it[index] = id })
    fun addSet(id: Int) = copy(setIds = setIds + id)
    fun removeSet(index: Int) = copy(setIds = setIds.filterIndexed { i, _ -> i != index })

    companion object {
        /** File size for [count] sets: the header and the ids, rounded up to a multiple of 4. */
        fun sizeFor(count: Int): Int = (4 + 2 * count + 3) / 4 * 4

        fun parse(bytes: ByteArray): FoxResult<BattleTowerTrainer> {
            if (bytes.size < 4) return FoxResult.Err("A Battle Tower trainer is at least 4 bytes, got ${bytes.size}")
            val count = bytes.u16(2)
            if (bytes.size != sizeFor(count)) return FoxResult.Err("A Battle Tower trainer with $count sets is ${sizeFor(count)} bytes, got ${bytes.size}")
            return FoxResult.Ok(BattleTowerTrainer(bytes.u16(0), List(count) { bytes.u16(4 + 2 * it) }))
        }

        val SPEC = RecordSpec(HgssLayout.BATTLE_TOWER_TRAINERS, ::parse, BattleTowerTrainer::toBytes)
    }
}

/**
 * A Battle Tower Pokémon set (a file of `a/2/0/3`, 16 bytes): [species], four [moves], [evFlags] (bit 0 HP, 1 Attack, 2 Defense,
 * 3 Speed, 4 Sp. Atk, 5 Sp. Def: the stats that get maximum EVs), [nature] (0 to 24), held [item] and [form]. Layout from DSPRE's
 * `BattleTowerPokemonSetFile`.
 */
data class BattleTowerSet(val species: Int, val moves: List<Int>, val evFlags: Int, val nature: Int, val item: Int, val form: Int) {
    init { require(moves.size == 4) { "four moves" } }

    fun hasEv(stat: Int): Boolean = (evFlags ushr stat) and 1 == 1
    fun withEv(stat: Int, on: Boolean) = copy(evFlags = if (on) evFlags or (1 shl stat) else evFlags and (1 shl stat).inv())
    fun withMove(slot: Int, move: Int) = copy(moves = moves.toMutableList().also { it[slot] = move })

    fun toBytes(): ByteArray = ByteArray(SIZE).also { out ->
        out.putU16(0, species)
        moves.forEachIndexed { i, m -> out.putU16(2 + 2 * i, m) }
        out[10] = evFlags.toByte()
        out[11] = nature.toByte()
        out.putU16(12, item)
        out.putU16(14, form)
    }

    companion object {
        const val SIZE = 16
        val EV_STATS = listOf("HP", "Attack", "Defense", "Speed", "Sp. Atk", "Sp. Def")
        val NATURES = listOf(
            "Hardy", "Lonely", "Brave", "Adamant", "Naughty", "Bold", "Docile", "Relaxed", "Impish", "Lax", "Timid", "Hasty", "Serious",
            "Jolly", "Naive", "Modest", "Mild", "Quiet", "Bashful", "Rash", "Calm", "Gentle", "Sassy", "Careful", "Quirky",
        )

        fun parse(bytes: ByteArray): FoxResult<BattleTowerSet> {
            if (bytes.size != SIZE) return FoxResult.Err("A Battle Tower set is $SIZE bytes, got ${bytes.size}")
            return FoxResult.Ok(
                BattleTowerSet(
                    bytes.u16(0), List(4) { bytes.u16(2 + 2 * it) }, bytes[10].toInt() and 0xFF, bytes[11].toInt() and 0xFF, bytes.u16(12), bytes.u16(14),
                ),
            )
        }

        val SPEC = RecordSpec(HgssLayout.BATTLE_TOWER_SETS, ::parse, BattleTowerSet::toBytes)
    }
}
