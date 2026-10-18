package io.github.legendaryredfox.foxhg.core.data.grounditem

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.LabelRef
import io.github.legendaryredfox.foxhg.core.script.ScriptCommand
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
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

    private const val SET_VAR = 0x29
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
        val (itemAt, quantityAt) = (if (at < 0) null else entryAt(items, at)) ?: return FoxResult.Err("Script $scriptNumber is not an item ball")
        fun put(index: Int, value: Int) {
            val old = (items[index] as ScriptItem.Instruction).command
            items[index] = ScriptItem.Instruction(ScriptCommand(old.id, old.args.toMutableList().also { it[1] = value.toLong() }, old.target))
        }
        put(itemAt, item)
        put(quantityAt, quantity)
        return records.with(SCRIPT_FILE, ScriptEntry.Script(entry.file.copy(items = items))).save(project)
    }
}
