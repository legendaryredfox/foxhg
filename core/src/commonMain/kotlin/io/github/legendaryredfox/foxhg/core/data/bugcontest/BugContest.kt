package io.github.legendaryredfox.foxhg.core.data.bugcontest

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/**
 * One Bug Catching Contest Pokémon: species, level range, [rate] (a threshold of the 0 to 99 roll, not a percentage, see
 * [BugContestTable.effectiveRates]), [score] (the rarity part of the judge's score) and [padding] (u16, always 0 in the
 * game, kept so an unedited table writes back the same bytes).
 */
data class BugContestEncounter(val species: Int, val minLevel: Int, val maxLevel: Int, val rate: Int, val score: Int, val padding: Int = 0)

/**
 * `data/mushi/mushi_encount.bin`: [SETS] sets of [PER_SET] entries of 8 bytes (u16 species, u8 min level, u8 max level,
 * u8 rate, u8 score, u16 padding). Layout from the DS Pokémon Hacking wiki's Bug Catching Contests guide and DSPRE's
 * `BugContestEncounterFile`.
 */
data class BugContestTable(val sets: List<List<BugContestEncounter>>) {
    init { require(sets.size == SETS && sets.all { it.size == PER_SET }) { "$SETS sets of $PER_SET entries" } }

    fun with(set: Int, slot: Int, entry: BugContestEncounter): BugContestTable =
        BugContestTable(sets.mapIndexed { s, list -> if (s == set) list.toMutableList().also { it[slot] = entry } else list })

    fun toBytes(): ByteArray {
        val out = ByteArray(SIZE)
        var at = 0
        for (e in sets.flatten()) {
            out.putU16(at, e.species)
            out[at + 2] = e.minLevel.toByte()
            out[at + 3] = e.maxLevel.toByte()
            out[at + 4] = e.rate.toByte()
            out[at + 5] = e.score.toByte()
            out.putU16(at + 6, e.padding)
            at += ENTRY
        }
        return out
    }

    fun save(project: RomProject): FoxResult<Unit> = project.write(PATH, toBytes())

    companion object {
        const val PATH = HgssLayout.BUG_CONTEST
        const val SETS = 4
        const val PER_SET = 10
        const val ENTRY = 8
        const val SIZE = SETS * PER_SET * ENTRY

        /** When each set is used: set 0 without the National Dex and on Sunday and Monday, then by weekday / 2 with it. */
        val SET_NAMES = listOf("Set 0: no National Dex, Sunday, Monday", "Set 1: Tuesday, Wednesday", "Set 2: Thursday, Friday", "Set 3: Saturday")

        /**
         * The chance in percent of each slot of [set] (a list of [PER_SET] entries): the game rolls 0 to 99 and takes the
         * first slot whose rate is at or below the roll, so slot 0 gets 100 - rate and each later one the gap to the
         * slot before, or 0 when its rate is not lower (it can never be reached). A last slot above 0 leaves rolls with
         * no Pokémon, which is not counted here, see [problems].
         */
        fun effectiveRates(set: List<BugContestEncounter>): List<Int> {
            var ceiling = 100
            return set.map { e ->
                val share = (ceiling - e.rate).coerceAtLeast(0)
                ceiling = minOf(ceiling, e.rate)
                share
            }
        }

        /** What would go wrong in game with [set]: slots that can never appear, and a last rate above 0. */
        fun problems(set: List<BugContestEncounter>): List<String> {
            val out = ArrayList<String>()
            val rates = effectiveRates(set)
            set.indices.filter { rates[it] == 0 }.forEach { out += "Slot ${it + 1} can never appear: its rate must be lower than the one above" }
            if (set.last().rate != 0) out += "The last slot's rate must be 0, or some rolls match no Pokémon"
            return out
        }

        fun parse(bytes: ByteArray): FoxResult<BugContestTable> {
            if (bytes.size != SIZE) return FoxResult.Err("The Bug Contest table is $SIZE bytes, got ${bytes.size}")
            val entries = List(SETS * PER_SET) { i ->
                val at = i * ENTRY
                BugContestEncounter(
                    bytes.u16(at), bytes[at + 2].toInt() and 0xFF, bytes[at + 3].toInt() and 0xFF,
                    bytes[at + 4].toInt() and 0xFF, bytes[at + 5].toInt() and 0xFF, bytes.u16(at + 6),
                )
            }
            return FoxResult.Ok(BugContestTable(entries.chunked(PER_SET)))
        }

        fun load(project: RomProject): FoxResult<BugContestTable> =
            project.read(PATH)?.let(::parse) ?: FoxResult.Err("The ROM has no $PATH")
    }
}
