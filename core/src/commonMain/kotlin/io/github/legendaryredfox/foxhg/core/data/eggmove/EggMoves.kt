package io.github.legendaryredfox.foxhg.core.data.eggmove

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

data class EggMoveGroup(val species: Int, val moves: List<Int>)

/**
 * Egg moves of every species (`a/2/2/9`, a NARC with one file). The file is a flat list of u16: `20000 + species`
 * opens a group, the move ids that follow belong to it, and 0xFFFF ends the list. The game finds a species by scanning
 * the first [SCAN_LIMIT] words for its header, so a header at or past that index is never found. Bytes after the
 * terminator are kept in [trailing].
 */
data class EggMoves(val groups: List<EggMoveGroup>, val trailing: ByteArray = ByteArray(0)) {
    fun movesOf(species: Int): List<Int> = groups.firstOrNull { it.species == species }?.moves ?: emptyList()

    /** Replaces the moves of [species]; an empty list removes its group. New groups keep the file sorted by species. */
    fun withMoves(species: Int, moves: List<Int>): EggMoves {
        val rest = groups.filter { it.species != species }
        if (moves.isEmpty()) return copy(groups = rest)
        val at = groups.indexOfFirst { it.species == species }
        val group = EggMoveGroup(species, moves)
        if (at >= 0) return copy(groups = groups.toMutableList().also { it[at] = group })
        val insert = rest.indexOfFirst { it.species > species }.let { if (it < 0) rest.size else it }
        return copy(groups = rest.toMutableList().also { it.add(insert, group) })
    }

    /** Index of the last header word; the game only finds headers below [SCAN_LIMIT]. */
    val lastHeaderIndex: Int
        get() {
            var words = 0
            var last = -1
            for (g in groups) { last = words; words += 1 + g.moves.size }
            return last
        }

    val overLimit: Boolean get() = lastHeaderIndex >= SCAN_LIMIT

    fun toBytes(): ByteArray {
        val words = groups.sumOf { 1 + it.moves.size } + 1
        val out = ByteArray(words * 2 + trailing.size)
        var p = 0
        for (g in groups) {
            out.putU16(p, SPECIES_BASE + g.species); p += 2
            for (m in g.moves) { out.putU16(p, m); p += 2 }
        }
        out.putU16(p, END)
        trailing.copyInto(out, p + 2)
        return out
    }

    override fun equals(other: Any?) = other is EggMoves && groups == other.groups && trailing.contentEquals(other.trailing)

    override fun hashCode() = groups.hashCode() * 31 + trailing.contentHashCode()

    companion object {
        const val SPECIES_BASE = 20000
        const val END = 0xFFFF
        const val SCAN_LIMIT = 2045
        const val MAX_PER_SPECIES = 16

        fun parse(bytes: ByteArray): FoxResult<EggMoves> {
            val groups = ArrayList<EggMoveGroup>()
            var species = -1
            var moves = ArrayList<Int>()
            var p = 0
            while (true) {
                if (p + 2 > bytes.size) return FoxResult.Err("Egg move list has no 0xFFFF terminator")
                val word = bytes.u16(p)
                p += 2
                if (word == END || word > SPECIES_BASE) {
                    if (species >= 0) groups += EggMoveGroup(species, moves)
                    if (word == END) break
                    species = word - SPECIES_BASE
                    moves = ArrayList()
                } else {
                    if (species < 0) return FoxResult.Err("Egg move list starts with a move, not a species header")
                    moves += word
                }
            }
            return FoxResult.Ok(EggMoves(groups, bytes.copyOfRange(p, bytes.size)))
        }

        val SPEC = RecordSpec(HgssLayout.EGG_MOVES, ::parse, EggMoves::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<EggMoves>> = NarcRecords.load(project, SPEC)
    }
}
