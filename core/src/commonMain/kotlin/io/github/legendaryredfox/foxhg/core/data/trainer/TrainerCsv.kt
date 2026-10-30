package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.csv.Csv

/**
 * CSV import and export of every trainer, in two files: one row per trainer for the header ([exportTrainers], [importTrainers]) and one row
 * per Pokémon for the parties ([exportParties], [importParties]). The `name` columns are for reading and ignored on import. A trainer or a
 * column that a file leaves out stays as it is, and any bad id, unknown column, number or range rejects the whole file.
 */
object TrainerCsv {
    private val TRAINER_COLUMNS = listOf("class", "ai_flags", "double_battle", "item1", "item2", "item3", "item4", "custom_moves", "held_items", "unknown2")
    private val PARTY_COLUMNS = listOf("species", "form", "level", "difficulty", "gender", "ability", "item", "move1", "move2", "move3", "move4", "capsule")

    /** `id,name,class,class_name,ai_flags,double_battle,item1..item4,custom_moves,held_items,unknown2,party_size`; the party size is for reading. */
    fun exportTrainers(trainers: List<Trainer>, trainerName: (Int) -> String, className: (Int) -> String): String =
        Csv.format(
            listOf(listOf("id", "name", "class", "class_name", "ai_flags", "double_battle", "item1", "item2", "item3", "item4", "custom_moves", "held_items", "unknown2", "party_size")) +
                trainers.mapIndexed { i, t ->
                    val h = t.header
                    listOf(i.toString(), trainerName(i), h.trainerClass.toString(), className(h.trainerClass), h.aiFlags.toString(), h.doubleBattle.toString()) +
                        List(4) { h.items.getOrElse(it) { 0 }.toString() } +
                        listOf(if (h.hasCustomMoves) "1" else "0", if (h.hasHeldItems) "1" else "0", h.unknown2.toString(), h.partySize.toString())
                },
        )

    /** Reads a file made by [exportTrainers]: each row changes the header of the trainer with its `id`. The party is not touched. */
    fun importTrainers(text: String, trainers: List<Trainer>): FoxResult<List<Trainer>> {
        val (header, rows) = when (val r = table(text, setOf("id", "name", "class_name", "party_size") + TRAINER_COLUMNS)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        if ("id" !in header) return FoxResult.Err("The CSV header has no 'id' column")
        val out = trainers.toMutableList()
        val seen = HashSet<Int>()
        try {
            for ((n, row) in rows.withIndex()) {
                val line = n + 2
                val id = integer(row, header, "id", line, 0L..(trainers.size - 1).toLong())!!.toInt()
                if (!seen.add(id)) return FoxResult.Err("Line $line: trainer $id appears twice")
                var h = out[id].header
                fun value(column: String, range: LongRange) = integer(row, header, column, line, range)
                value("class", 0L..255L)?.let { h = h.copy(trainerClass = it.toInt()) }
                value("unknown2", 0L..255L)?.let { h = h.copy(unknown2 = it.toInt()) }
                value("ai_flags", 0L..0xFFFFFFFFL)?.let { h = h.copy(aiFlags = it) }
                value("double_battle", 0L..0xFFFFFFFFL)?.let { h = h.copy(doubleBattle = it) }
                for (i in 0 until 4) value("item${i + 1}", 0L..65535L)?.let { v -> h = h.copy(items = List(4) { k -> if (k == i) v.toInt() else h.items.getOrElse(k) { 0 } }) }
                value("custom_moves", 0L..1L)?.let { h = h.withFlags(customMoves = it == 1L, heldItems = h.hasHeldItems) }
                value("held_items", 0L..1L)?.let { h = h.withFlags(customMoves = h.hasCustomMoves, heldItems = it == 1L) }
                out[id] = out[id].withHeader(h)
            }
        } catch (e: IllegalArgumentException) {
            return FoxResult.Err(e.message ?: "bad value")
        }
        return FoxResult.Ok(out)
    }

    /**
     * `trainer,trainer_name,slot,species,species_name,form,level,difficulty,gender,ability,item,move1..move4,capsule`, one row for each Pokémon,
     * the slot counted from 1. `gender` is 0 none, 1 male, 2 female and `ability` 0 none, 1 first, 2 second (the two halves of the stored byte).
     */
    fun exportParties(trainers: List<Trainer>, trainerName: (Int) -> String, speciesName: (Int) -> String): String =
        Csv.format(
            listOf(listOf("trainer", "trainer_name", "slot", "species", "species_name", "form", "level", "difficulty", "gender", "ability", "item", "move1", "move2", "move3", "move4", "capsule")) +
                trainers.flatMapIndexed { i, t ->
                    t.party.mapIndexed { slot, m ->
                        listOf(i.toString(), trainerName(i), (slot + 1).toString(), m.species.toString(), speciesName(m.species), m.form.toString(), m.level.toString(), m.difficulty.toString(),
                            (m.genderAbility and 0xF).toString(), (m.genderAbility ushr 4).toString(), m.item.toString()) + List(4) { m.moves.getOrElse(it) { 0 }.toString() } + m.capsule.toString()
                    }
                },
        )

    /**
     * Reads a file made by [exportParties]. The rows of a trainer are its whole new party: the slots must be 1 to the number of rows with none
     * repeated or missing, at most [Trainer.MAX_PARTY]. A trainer with no row keeps its party. A held item or a move turns the trainer's
     * held item or custom moves flag on, so the party record has room for it.
     */
    fun importParties(text: String, trainers: List<Trainer>): FoxResult<List<Trainer>> {
        val (header, rows) = when (val r = table(text, setOf("trainer", "trainer_name", "slot", "species_name") + PARTY_COLUMNS)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        for (required in listOf("trainer", "slot", "species", "level")) if (required !in header) return FoxResult.Err("The CSV header has no '$required' column")
        val bySlot = LinkedHashMap<Int, MutableMap<Int, TrainerMon>>()
        for ((n, row) in rows.withIndex()) {
            val line = n + 2
            fun num(column: String, range: LongRange): Int? = integer(row, header, column, line, range)?.toInt()
            try {
                val id = num("trainer", 0L..(trainers.size - 1).toLong()) ?: return FoxResult.Err("Line $line: no trainer")
                val slot = num("slot", 1L..Trainer.MAX_PARTY.toLong()) ?: return FoxResult.Err("Line $line: no slot")
                val old = trainers[id].party.getOrNull(slot - 1)
                val species = num("species", 0L..1023L) ?: old?.species ?: return FoxResult.Err("Line $line: no species")
                val form = num("form", 0L..63L) ?: old?.form ?: 0
                val gender = num("gender", 0L..2L) ?: ((old?.genderAbility ?: 0) and 0xF)
                val ability = num("ability", 0L..2L) ?: ((old?.genderAbility ?: 0) ushr 4)
                val moves = List(4) { num("move${it + 1}", 0L..65535L) ?: old?.moves?.getOrElse(it) { 0 } ?: 0 }
                val mon = TrainerMon(
                    difficulty = num("difficulty", 0L..255L) ?: old?.difficulty ?: 0,
                    genderAbility = (ability shl 4) or gender,
                    level = num("level", 1L..100L) ?: old?.level ?: return FoxResult.Err("Line $line: no level"),
                    speciesAndForm = (form shl 10) or species,
                    item = num("item", 0L..65535L) ?: old?.item ?: 0,
                    moves = moves,
                    capsule = num("capsule", 0L..65535L) ?: old?.capsule ?: 0,
                )
                if (bySlot.getOrPut(id) { HashMap() }.put(slot, mon) != null) return FoxResult.Err("Line $line: slot $slot of trainer $id appears twice")
            } catch (e: IllegalArgumentException) {
                return FoxResult.Err(e.message ?: "Line $line: bad value")
            }
        }
        val out = trainers.toMutableList()
        for ((id, slots) in bySlot) {
            if (slots.keys.sorted() != (1..slots.size).toList()) return FoxResult.Err("Trainer $id: the slots must be 1 to ${slots.size} with none missing, got ${slots.keys.sorted()}")
            val party = (1..slots.size).map { slots.getValue(it) }
            val t = out[id]
            val header = t.header.withFlags(customMoves = t.header.hasCustomMoves || party.any { m -> m.moves.any { it != 0 } }, heldItems = t.header.hasHeldItems || party.any { it.item != 0 })
            out[id] = Trainer(header.copy(partySize = party.size), party, t.trailing)
        }
        return FoxResult.Ok(out)
    }

    private class Table(val header: List<String>, val rows: List<List<String>>) {
        operator fun component1() = header
        operator fun component2() = rows
    }

    /** The header and rows of [text], checking that every column is one of [known] and every row has a field for each column. */
    private fun table(text: String, known: Set<String>): FoxResult<Table> {
        val rows = when (val parsed = Csv.parse(text)) {
            is FoxResult.Ok -> parsed.value
            is FoxResult.Err -> return parsed
        }
        val header = rows.firstOrNull()?.map { it.trim() } ?: return FoxResult.Err("The CSV file is empty")
        header.firstOrNull { it !in known }?.let { return FoxResult.Err("Unknown column '$it'") }
        if (header.toSet().size != header.size) return FoxResult.Err("The CSV header repeats a column")
        for ((n, row) in rows.drop(1).withIndex()) if (row.size != header.size) return FoxResult.Err("Line ${n + 2} has ${row.size} fields, expected ${header.size}")
        return FoxResult.Ok(Table(header, rows.drop(1)))
    }

    /** The integer of [column] in [row], null when the file has no such column; throws with the reason when it is not a number in [range]. */
    private fun integer(row: List<String>, header: List<String>, column: String, line: Int, range: LongRange): Long? {
        val at = header.indexOf(column)
        if (at < 0) return null
        val v = row[at].trim().toLongOrNull() ?: throw IllegalArgumentException("Line $line: '$column' is not a whole number ('${row[at]}')")
        if (v !in range) throw IllegalArgumentException("Line $line: '$column' is $v, outside ${range.first}..${range.last}")
        return v
    }
}
