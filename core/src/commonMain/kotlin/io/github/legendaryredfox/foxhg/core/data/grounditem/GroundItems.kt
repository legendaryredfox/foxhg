package io.github.legendaryredfox.foxhg.core.data.grounditem

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.LabelRef
import io.github.legendaryredfox.foxhg.core.script.ScriptCommand
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptFile
import io.github.legendaryredfox.foxhg.core.script.ScriptItem

/**
 * One item ball: what script [scriptNumber] (7000 and up, the number an overworld event uses) gives. The scripts of the ground items are the
 * `script_N` entries of script file 141, each `SetVar 0x8008, item`, `SetVar 0x8009, quantity` and a jump to the shared function that gives
 * the item; script number 7000 + N - 1 is `script_N`. [usedBy] are the event files (maps) with an overworld event that runs it.
 */
data class GroundItem(val scriptNumber: Int, val item: Int, val quantity: Int, val usedBy: List<Int>)

object GroundItems {
    const val SCRIPT_FILE = 141
    const val FIRST_SCRIPT = 7000
    const val MAX_QUANTITY = 999
    /** The last script number the game maps to the ball file (8000 is the hidden items). */
    const val MAX_SCRIPT = 7999

    private const val SET_VAR = 0x29
    private const val CALL_STD = 20
    private const val VAR_ITEM = 0x8008L
    private const val VAR_QUANTITY = 0x8009L

    /** The positions of the two `SetVar` after the label at [at] when the script is a ground item entry, or null. */
    private fun entryAt(items: List<ScriptItem>, at: Int): Pair<Int, Int>? {
        val label = (items[at] as? ScriptItem.Label)?.ref ?: return null
        if (label.kind != LabelRef.Kind.SCRIPT) return null
        val first = (items.getOrNull(at + 1) as? ScriptItem.Instruction)?.command ?: return null
        val second = (items.getOrNull(at + 2) as? ScriptItem.Instruction)?.command ?: return null
        if (first.id != SET_VAR || first.args.getOrNull(0) != VAR_ITEM || first.args.size < 2) return null
        if (second.id != SET_VAR || second.args.getOrNull(0) != VAR_QUANTITY || second.args.size < 2) return null
        return at + 1 to at + 2
    }

    /** Puts [item] and [quantity] into the ball entry whose label is at [at]. */
    private fun setValues(items: MutableList<ScriptItem>, at: Int, item: Int, quantity: Int) {
        val (itemAt, quantityAt) = entryAt(items, at)!!
        fun put(index: Int, value: Int) {
            val old = (items[index] as ScriptItem.Instruction).command
            items[index] = ScriptItem.Instruction(ScriptCommand(old.id, old.args.toMutableList().also { it[1] = value.toLong() }, old.target))
        }
        put(itemAt, item)
        put(quantityAt, quantity)
    }

    /** [items] with every `script_N` label and jump target numbered [from] or more moved by [by]. */
    private fun renumbered(items: List<ScriptItem>, from: Int, by: Int): List<ScriptItem> {
        fun move(ref: LabelRef) = if (ref.kind == LabelRef.Kind.SCRIPT && ref.number >= from) LabelRef(ref.kind, ref.number + by) else ref
        return items.map {
            when (it) {
                is ScriptItem.Label -> ScriptItem.Label(move(it.ref))
                is ScriptItem.Instruction -> it.command.target?.let { t -> ScriptItem.Instruction(it.command.copy(target = move(t))) } ?: it
                else -> it
            }
        }
    }

    /** The shared function that gives the item is the last script of the file: its label number, and the first ball entry before it must be a ball. */
    private fun functionNumber(items: List<ScriptItem>): Int? {
        val n = items.count { it is ScriptItem.Label && it.ref.kind == LabelRef.Kind.SCRIPT }
        val ballAt = items.indexOfFirst { it is ScriptItem.Label && it.ref == LabelRef(LabelRef.Kind.SCRIPT, n - 1) }
        val functionAt = items.indexOfFirst { it is ScriptItem.Label && it.ref == LabelRef(LabelRef.Kind.SCRIPT, n) }
        if (n < 2 || ballAt < 0 || entryAt(items, ballAt) == null || functionAt < 0 || entryAt(items, functionAt) != null) return null
        return n
    }

    /** Every ground item entry of the item script file, with the maps whose events use it. */
    fun load(project: RomProject): FoxResult<List<GroundItem>> {
        val records = when (val r = ScriptEntry.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val script = (records.records.getOrNull(SCRIPT_FILE) as? ScriptEntry.Script)?.file ?: return FoxResult.Err("Script file $SCRIPT_FILE is not a script")
        val events = when (val r = EventFile.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val usedBy = HashMap<Int, MutableList<Int>>()
        events.records.forEachIndexed { file, ev ->
            for (ow in ev.overworlds) if (!ow.isAlias) usedBy.getOrPut(ow.script) { ArrayList() }.let { if (file !in it) it += file }
        }
        val out = ArrayList<GroundItem>()
        script.items.forEachIndexed { at, item ->
            val label = (item as? ScriptItem.Label)?.ref ?: return@forEachIndexed
            val (itemAt, quantityAt) = entryAt(script.items, at) ?: return@forEachIndexed
            val number = FIRST_SCRIPT + label.number - 1
            val give = (script.items[itemAt] as ScriptItem.Instruction).command.args[1].toInt()
            val quantity = (script.items[quantityAt] as ScriptItem.Instruction).command.args[1].toInt()
            out += GroundItem(number, give, quantity, usedBy[number].orEmpty())
        }
        return FoxResult.Ok(out)
    }

    /** Makes the ball of script [scriptNumber] give [quantity] of [item]; every event that runs the script changes with it. */
    fun change(project: RomProject, scriptNumber: Int, item: Int, quantity: Int, itemCount: Int = 0x10000): FoxResult<Unit> {
        if (item !in 1 until itemCount) return FoxResult.Err("Item $item does not exist")
        if (quantity !in 1..MAX_QUANTITY) return FoxResult.Err("A quantity is 1 to $MAX_QUANTITY")
        val records = when (val r = ScriptEntry.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val entry = records.records.getOrNull(SCRIPT_FILE) as? ScriptEntry.Script ?: return FoxResult.Err("Script file $SCRIPT_FILE is not a script")
        val items = entry.file.items.toMutableList()
        val at = items.indexOfFirst { it is ScriptItem.Label && it.ref == LabelRef(LabelRef.Kind.SCRIPT, scriptNumber - FIRST_SCRIPT + 1) }
        if (at < 0 || entryAt(items, at) == null) return FoxResult.Err("Script $scriptNumber is not an item ball")
        setValues(items, at, item, quantity)
        return records.with(SCRIPT_FILE, ScriptEntry.Script(entry.file.copy(items = items))).save(project)
    }

    /**
     * Adds a ball at the end of the table, a copy of the last one with [item] and [quantity], and returns its script number (an event uses it by
     * setting its script field to the number). The shared function that gives the item moves one place up (its label and every jump to it), which is
     * safe because only the balls jump to it; the game finds the file for every number from 7000 to 7999.
     */
    fun add(project: RomProject, item: Int, quantity: Int, itemCount: Int = 0x10000): FoxResult<Int> {
        if (item !in 1 until itemCount) return FoxResult.Err("Item $item does not exist")
        if (quantity !in 1..MAX_QUANTITY) return FoxResult.Err("A quantity is 1 to $MAX_QUANTITY")
        val records = when (val r = ScriptEntry.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val entry = records.records.getOrNull(SCRIPT_FILE) as? ScriptEntry.Script ?: return FoxResult.Err("Script file $SCRIPT_FILE is not a script")
        val function = functionNumber(entry.file.items) ?: return FoxResult.Err("Script file $SCRIPT_FILE does not end with the item balls and the shared function")
        if (FIRST_SCRIPT + function > MAX_SCRIPT) return FoxResult.Err("The game finds item balls only up to script $MAX_SCRIPT")
        val moved = renumbered(entry.file.items, function, 1).toMutableList()
        val lastBall = moved.indexOfFirst { it is ScriptItem.Label && it.ref == LabelRef(LabelRef.Kind.SCRIPT, function - 1) }
        val functionAt = moved.indexOfFirst { it is ScriptItem.Label && it.ref == LabelRef(LabelRef.Kind.SCRIPT, function + 1) }
        val copy = moved.subList(lastBall, functionAt).toMutableList()
        copy[0] = ScriptItem.Label(LabelRef(LabelRef.Kind.SCRIPT, function))
        moved.addAll(functionAt, copy)
        setValues(moved, functionAt, item, quantity)
        records.with(SCRIPT_FILE, ScriptEntry.Script(entry.file.copy(items = moved))).save(project).let { if (it is FoxResult.Err) return it }
        return FoxResult.Ok(FIRST_SCRIPT + function - 1)
    }

    /** Why the ball [scriptNumber] cannot go: an event, trigger, sign or `CallStd` runs it. Null when nothing does. */
    private fun inUse(scriptNumber: Int, events: NarcRecords<EventFile>, records: NarcRecords<ScriptEntry>): String? {
        events.records.forEachIndexed { file, ev ->
            if (ev.overworlds.any { it.script == scriptNumber } || ev.triggers.any { it.script == scriptNumber } || ev.spawnables.any { it.script == scriptNumber }) return "An event of event file $file runs script $scriptNumber"
        }
        records.records.forEachIndexed { file, e ->
            val script = (e as? ScriptEntry.Script)?.file ?: return@forEachIndexed
            if (script.items.any { it is ScriptItem.Instruction && it.command.id == CALL_STD && it.command.args.firstOrNull() == scriptNumber.toLong() }) return "A script of script file $file calls script $scriptNumber"
        }
        return null
    }

    /** The script number [n] after the balls from [from] to [to] move one place down. */
    private fun shifted(n: Int, from: Int, to: Int) = if (n in from..to) n - 1 else n

    /**
     * Removes the ball [scriptNumber]. Every ball after it moves one place down, and so do the numbers that name them: the script field of every
     * overworld event, trigger and sign, and the argument of every `CallStd`, in all event and script files. A ball that an event, a trigger, a
     * sign or a `CallStd` still runs stays, so no reference is left pointing at a different ball.
     */
    fun remove(project: RomProject, scriptNumber: Int): FoxResult<Unit> {
        val records = when (val r = ScriptEntry.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val events = when (val r = EventFile.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return FoxResult.Err("The event files cannot be read: ${r.message}")
        }
        val entry = records.records.getOrNull(SCRIPT_FILE) as? ScriptEntry.Script ?: return FoxResult.Err("Script file $SCRIPT_FILE is not a script")
        val function = functionNumber(entry.file.items) ?: return FoxResult.Err("Script file $SCRIPT_FILE does not end with the item balls and the shared function")
        val n = scriptNumber - FIRST_SCRIPT + 1
        if (function < 3) return FoxResult.Err("The last item ball stays")
        if (n !in 1 until function) return FoxResult.Err("Script $scriptNumber is not an item ball")
        inUse(scriptNumber, events, records)?.let { return FoxResult.Err("Script $scriptNumber stays: $it") }
        val items = entry.file.items
        val ballAt = items.indexOfFirst { it is ScriptItem.Label && it.ref == LabelRef(LabelRef.Kind.SCRIPT, n) }
        val nextAt = items.indexOfFirst { it is ScriptItem.Label && it.ref == LabelRef(LabelRef.Kind.SCRIPT, n + 1) }
        if (ballAt < 0 || nextAt < 0 || entryAt(items, ballAt) == null) return FoxResult.Err("Script $scriptNumber is not an item ball")
        val file = ScriptFile(renumbered(items.subList(0, ballAt) + items.subList(nextAt, items.size), n + 1, -1))
        file.problem()?.let { return FoxResult.Err("Script $scriptNumber stays: $it") }

        // the balls that move are scriptNumber + 1 up to the last ball (the shared function is not named outside the file)
        val from = scriptNumber + 1
        val to = FIRST_SCRIPT + function - 2
        val newEvents = events.records.map { ev ->
            if (ev.overworlds.none { it.script in from..to } && ev.triggers.none { it.script in from..to } && ev.spawnables.none { it.script in from..to }) ev
            else ev.copy(
                overworlds = ev.overworlds.map { it.copy(script = shifted(it.script, from, to)) },
                triggers = ev.triggers.map { it.copy(script = shifted(it.script, from, to)) },
                spawnables = ev.spawnables.map { it.copy(script = shifted(it.script, from, to)) },
            )
        }
        val newScripts = records.records.mapIndexed { index, e ->
            if (index == SCRIPT_FILE) return@mapIndexed ScriptEntry.Script(file)
            val script = (e as? ScriptEntry.Script)?.file ?: return@mapIndexed e
            fun names(it: ScriptItem) = it is ScriptItem.Instruction && it.command.id == CALL_STD && it.command.args.firstOrNull()?.toInt() in from..to
            if (script.items.none(::names)) return@mapIndexed e
            ScriptEntry.Script(script.copy(items = script.items.map {
                if (!names(it)) it else {
                    val command = (it as ScriptItem.Instruction).command
                    ScriptItem.Instruction(command.copy(args = command.args.toMutableList().also { a -> a[0] = a[0] - 1 }))
                }
            }))
        }
        events.withAll(newEvents).save(project).let { if (it is FoxResult.Err) return it }
        return records.withAll(newScripts).save(project)
    }

    /** Removes the last ball of the table. */
    fun removeLast(project: RomProject): FoxResult<Unit> {
        val records = when (val r = ScriptEntry.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val entry = records.records.getOrNull(SCRIPT_FILE) as? ScriptEntry.Script ?: return FoxResult.Err("Script file $SCRIPT_FILE is not a script")
        val function = functionNumber(entry.file.items) ?: return FoxResult.Err("Script file $SCRIPT_FILE does not end with the item balls and the shared function")
        if (function < 3) return FoxResult.Err("The last item ball stays")
        return remove(project, FIRST_SCRIPT + function - 2)
    }
}
