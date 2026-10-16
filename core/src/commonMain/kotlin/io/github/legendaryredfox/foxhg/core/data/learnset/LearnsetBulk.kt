package io.github.legendaryredfox.foxhg.core.data.learnset

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.csv.Csv

/** What a bulk change did: the new [records], how many species it [changed] and how many it left alone ([skipped]: the move was already there, or the list is full). */
data class LearnsetChange(val records: List<Learnset>, val changed: Int, val skipped: Int)

/** Edits of the level-up moves of many species at once, and their CSV form (one row per move). */
object LearnsetBulk {
    /** Level 1 to 100 fits the 7 bits the game has, and a move number the 9 bits. */
    private val LEVELS = 1..100
    private const val MAX_MOVE = 0x1FF

    /** Teaches [move] at [level] to every species in [species] that does not know it yet and has room: it goes after the last move of the same or a lower level. */
    fun teach(records: List<Learnset>, species: Iterable<Int>, move: Int, level: Int): LearnsetChange =
        edit(records, species) { l ->
            if (l.entries.any { it.move == move } || l.entries.size >= Learnset.MAX_ENTRIES) null
            else {
                val at = l.entries.indexOfLast { it.level <= level } + 1
                l.copy(entries = l.entries.toMutableList().also { it.add(at, LearnsetEntry(move, level)) })
            }
        }

    /** Takes [move] out of every species in [species] that knows it (every level it is learned at). */
    fun forget(records: List<Learnset>, species: Iterable<Int>, move: Int): LearnsetChange =
        edit(records, species) { l -> if (l.entries.none { it.move == move }) null else l.copy(entries = l.entries.filter { it.move != move }) }

    /** Swaps [from] for [to] in every species in [species] that knows [from] and not [to] already, at the same level. */
    fun replace(records: List<Learnset>, species: Iterable<Int>, from: Int, to: Int): LearnsetChange =
        edit(records, species) { l ->
            if (from == to || l.entries.none { it.move == from } || l.entries.any { it.move == to }) null
            else l.copy(entries = l.entries.map { if (it.move == from) it.copy(move = to) else it })
        }

    private fun edit(records: List<Learnset>, species: Iterable<Int>, change: (Learnset) -> Learnset?): LearnsetChange {
        val out = records.toMutableList()
        var changed = 0
        var skipped = 0
        for (s in species.toSet()) {
            val current = out.getOrNull(s) ?: continue
            val updated = change(current)
            if (updated == null) skipped++ else { out[s] = updated; changed++ }
        }
        return LearnsetChange(out, changed, skipped)
    }

    /** `species,species_name,level,move,move_name`, one row per level-up move in the order the game learns them. */
    fun export(records: List<Learnset>, speciesName: (Int) -> String, moveName: (Int) -> String): String =
        Csv.format(
            listOf(listOf("species", "species_name", "level", "move", "move_name")) +
                records.flatMapIndexed { s, l -> l.entries.map { listOf(s.toString(), speciesName(s), it.level.toString(), it.move.toString(), moveName(it.move)) } },
        )

    /**
     * Reads the file [export] writes (the name columns are ignored, the header must have `species`, `level` and `move`). Every species with rows gets
     * exactly those moves in file order; species without rows stay as they are. A bad species, level or move rejects the whole file.
     */
    fun import(text: String, records: List<Learnset>): FoxResult<List<Learnset>> {
        val rows = when (val parsed = Csv.parse(text)) {
            is FoxResult.Ok -> parsed.value
            is FoxResult.Err -> return parsed
        }
        val header = rows.firstOrNull() ?: return FoxResult.Err("The CSV file is empty")
        val columns = listOf("species", "level", "move").map { name -> header.indexOf(name).also { if (it < 0) return FoxResult.Err("The CSV header has no '$name' column") } }
        val (speciesCol, levelCol, moveCol) = columns
        val entries = LinkedHashMap<Int, MutableList<LearnsetEntry>>()
        for ((n, row) in rows.drop(1).withIndex()) {
            val line = n + 2
            if (row.size != header.size) return FoxResult.Err("Line $line has ${row.size} fields, expected ${header.size}")
            val species = row[speciesCol].trim().toIntOrNull() ?: return FoxResult.Err("Line $line: '${row[speciesCol]}' is not a whole number for species")
            val level = row[levelCol].trim().toIntOrNull() ?: return FoxResult.Err("Line $line: '${row[levelCol]}' is not a whole number for level")
            val move = row[moveCol].trim().toIntOrNull() ?: return FoxResult.Err("Line $line: '${row[moveCol]}' is not a whole number for move")
            if (species !in records.indices) return FoxResult.Err("Line $line: species $species is outside 0..${records.lastIndex}")
            if (level !in LEVELS) return FoxResult.Err("Line $line: level $level is outside ${LEVELS.first}..${LEVELS.last}")
            if (move !in 0..MAX_MOVE) return FoxResult.Err("Line $line: move $move is outside 0..$MAX_MOVE")
            entries.getOrPut(species) { ArrayList() } += LearnsetEntry(move, level)
        }
        val out = records.toMutableList()
        for ((species, list) in entries) out[species] = out[species].copy(entries = list)
        return FoxResult.Ok(out)
    }
}
