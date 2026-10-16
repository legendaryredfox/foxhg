package io.github.legendaryredfox.foxhg.core.data.eggmove

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.data.csv.Csv
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

        /** Species numbers the CSV accepts: a header is `20000 + species` in a u16. */
        private val CSV_SPECIES = 1..1023
        private const val CSV_MAX_MOVE = 0x1FF

        /** `species,species_name,move,move_name`, one row per egg move in file order. */
        fun toCsv(eggs: EggMoves, speciesName: (Int) -> String, moveName: (Int) -> String): String =
            Csv.format(
                listOf(listOf("species", "species_name", "move", "move_name")) +
                    eggs.groups.flatMap { g -> g.moves.map { listOf(g.species.toString(), speciesName(g.species), it.toString(), moveName(it)) } },
            )

        /**
         * Reads the file [toCsv] writes (the name columns are ignored). A species with rows gets exactly those egg moves (at most [MAX_PER_SPECIES]), a
         * species without rows keeps its own; to remove a species' egg moves use the editor. A bad species or move rejects the whole file.
         */
        fun fromCsv(text: String, eggs: EggMoves): FoxResult<EggMoves> {
            val rows = when (val parsed = Csv.parse(text)) {
                is FoxResult.Ok -> parsed.value
                is FoxResult.Err -> return parsed
            }
            val header = rows.firstOrNull() ?: return FoxResult.Err("The CSV file is empty")
            val speciesCol = header.indexOf("species").also { if (it < 0) return FoxResult.Err("The CSV header has no 'species' column") }
            val moveCol = header.indexOf("move").also { if (it < 0) return FoxResult.Err("The CSV header has no 'move' column") }
            val bySpecies = LinkedHashMap<Int, MutableList<Int>>()
            for ((n, row) in rows.drop(1).withIndex()) {
                val line = n + 2
                if (row.size != header.size) return FoxResult.Err("Line $line has ${row.size} fields, expected ${header.size}")
                val species = row[speciesCol].trim().toIntOrNull() ?: return FoxResult.Err("Line $line: '${row[speciesCol]}' is not a whole number for species")
                val move = row[moveCol].trim().toIntOrNull() ?: return FoxResult.Err("Line $line: '${row[moveCol]}' is not a whole number for move")
                if (species !in CSV_SPECIES) return FoxResult.Err("Line $line: species $species is outside ${CSV_SPECIES.first}..${CSV_SPECIES.last}")
                if (move !in 0..CSV_MAX_MOVE) return FoxResult.Err("Line $line: move $move is outside 0..$CSV_MAX_MOVE")
                bySpecies.getOrPut(species) { ArrayList() } += move
            }
            bySpecies.entries.firstOrNull { it.value.size > MAX_PER_SPECIES }?.let { return FoxResult.Err("Species ${it.key} has ${it.value.size} egg moves, the most is $MAX_PER_SPECIES") }
            var out = eggs
            for ((species, moves) in bySpecies) out = out.withMoves(species, moves)
            return FoxResult.Ok(out)
        }

        fun load(project: RomProject): FoxResult<NarcRecords<EggMoves>> = NarcRecords.load(project, SPEC)
    }
}
