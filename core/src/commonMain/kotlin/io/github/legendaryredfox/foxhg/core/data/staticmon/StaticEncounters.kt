package io.github.legendaryredfox.foxhg.core.data.staticmon

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.LabelRef
import io.github.legendaryredfox.foxhg.core.script.ScriptCommand
import io.github.legendaryredfox.foxhg.core.script.ScriptCommands
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptFile
import io.github.legendaryredfox.foxhg.core.script.ScriptItem

/** The script commands that put a Pokémon in front of the player without a wild encounter table: a fixed battle, a gift, an egg, a roamer. */
enum class StaticKind(val commandId: Int, val label: String, val hasLevel: Boolean) {
    /** `WildBattle species, level, shiny`: Lugia, Ho-Oh, the Snorlax, the Red Gyarados and the other scripted battles. */
    BATTLE(589, "Battle", true),

    /** `GiveMon species, level, item, form, ability, var`: the starters' gift Pokémon, the Eevee from Bill, Kenya the Spearow, Shuckle. */
    GIFT(137, "Gift", true),

    /** `GiveEgg species, location`: the Mystery Egg and the other eggs; the hatching level is the game's own. */
    EGG(138, "Egg", false),
    ;

    companion object { fun of(commandId: Int): StaticKind? = entries.firstOrNull { it.commandId == commandId } }
}

/**
 * One scripted Pokémon: the command at [index] of the items of script file [file] (`a/0/1/2`). [species] and [level] are what the script says;
 * when it is not a plain number but a script variable (0x4000 and up), [speciesIsVariable] or [levelIsVariable] is set and the editor leaves
 * it alone.
 *
 * When the species is a variable and the script sets it with a `SetVar` or `SetOrCopyVar` of a plain number on a path that reaches the command
 * (the legendaries, the Game Corner prizes, the gift starters), one encounter is listed for each such assignment: [viaVariable] is set,
 * [index] is the assignment and [commandIndex] the `WildBattle`, `GiveMon` or `GiveEgg` that uses it. Otherwise [index] and [commandIndex] are the same.
 */
data class StaticEncounter(
    val file: Int, val index: Int, val kind: StaticKind, val species: Int, val level: Int?, val speciesIsVariable: Boolean, val levelIsVariable: Boolean,
    val viaVariable: Boolean = false, val commandIndex: Int = index,
) {
    val editable: Boolean get() = !speciesIsVariable
}

/**
 * The fixed Pokémon of the game: every `WildBattle`, `GiveMon` and `GiveEgg` in the scripts. Found by scanning the script files, so a ROM with more
 * of them (added by the user) shows them too; a script that does not parse is skipped, as the Scripts editor shows it raw. Roamers
 * (`CreateRoamer`) are not here: their species and level are in the game's code, not in the script.
 */
object StaticEncounters {
    const val FIRST_VARIABLE = 0x4000L

    fun find(project: RomProject): FoxResult<List<StaticEncounter>> {
        val records = when (val r = ScriptEntry.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val out = ArrayList<StaticEncounter>()
        records.records.forEachIndexed { file, entry ->
            val script = (entry as? ScriptEntry.Script)?.file ?: return@forEachIndexed
            script.items.forEachIndexed { index, item ->
                val command = (item as? ScriptItem.Instruction)?.command ?: return@forEachIndexed
                val kind = StaticKind.of(command.id) ?: return@forEachIndexed
                val species = command.args.getOrNull(0) ?: return@forEachIndexed
                val level = if (kind.hasLevel) command.args.getOrNull(1) else null
                val levelIsVariable = level != null && level >= FIRST_VARIABLE
                val sources = if (species >= FIRST_VARIABLE) assignmentsReaching(script, index, species.toInt()) else emptyList()
                if (sources.isEmpty()) {
                    out += StaticEncounter(file, index, kind, species.toInt(), level?.toInt(), speciesIsVariable = species >= FIRST_VARIABLE, levelIsVariable = levelIsVariable)
                } else {
                    for (source in sources) {
                        val value = source.value
                        if (out.any { it.file == file && it.index == source.index }) continue
                        out += StaticEncounter(file, source.index, kind, value, level?.toInt(), speciesIsVariable = false, levelIsVariable = levelIsVariable, viaVariable = true, commandIndex = index)
                    }
                }
            }
        }
        return FoxResult.Ok(out)
    }

    private class Assignment(val index: Int, val value: Int)

    /**
     * The `SetVar` and `SetOrCopyVar` of a species number to [variable] that can run just before the command at [commandIndex]: found by walking
     * the script backwards from the command through fall-through and jumps, stopping at the first assignment of [variable] on each path. A path
     * that meets anything else that writes the variable, or that gets to the start of the script, adds nothing. A function is entered from its callers, but what a call does to the variable in between is not followed.
     */
    private fun assignmentsReaching(script: ScriptFile, commandIndex: Int, variable: Int): List<Assignment> {
        val items = script.items
        val labelAt = HashMap<LabelRef, Int>()
        items.forEachIndexed { i, item -> if (item is ScriptItem.Label) labelAt[item.ref] = i }
        val jumpsTo = HashMap<Int, MutableList<Int>>()
        items.forEachIndexed { i, item ->
            val c = (item as? ScriptItem.Instruction)?.command ?: return@forEachIndexed
            val target = c.target ?: return@forEachIndexed
            if (target.kind == LabelRef.Kind.ACTION) return@forEachIndexed
            labelAt[target]?.let { jumpsTo.getOrPut(it) { ArrayList() } += i }
        }
        val found = LinkedHashMap<Int, Assignment>()
        val seen = HashSet<Int>()
        val pending = ArrayDeque<Int>()
        fun before(i: Int) {
            if (i > 0) {
                val previous = items[i - 1]
                val falls = when (previous) {
                    is ScriptItem.Label -> true
                    is ScriptItem.Instruction -> previous.command.id !in ScriptCommands.endCodes
                    else -> false
                }
                if (falls) pending += i - 1
            }
            jumpsTo[i]?.let { pending += it }
        }
        before(commandIndex)
        while (pending.isNotEmpty()) {
            val i = pending.removeFirst()
            if (!seen.add(i)) continue
            val c = (items[i] as? ScriptItem.Instruction)?.command
            if (c == null) { before(i); continue }
            val writes = when (c.id) {
                SET_VAR, SET_OR_COPY_VAR -> c.args.getOrNull(0) == variable.toLong()
                COPY_VAR, ADD_VAR, SUB_VAR -> c.args.getOrNull(0) == variable.toLong()
                else -> false
            }
            if (!writes) { before(i); continue }
            val value = c.args.getOrNull(1)
            if ((c.id == SET_VAR || c.id == SET_OR_COPY_VAR) && value != null && value in 1..MAX_SPECIES.toLong()) found[i] = Assignment(i, value.toInt())
        }
        return found.values.sortedBy { it.index }
    }

    /** Sets the species (and the level, for kinds that have one and when it is not a variable) of [encounter] in its script. */
    fun change(project: RomProject, encounter: StaticEncounter, species: Int, level: Int?): FoxResult<Unit> {
        if (encounter.speciesIsVariable) return FoxResult.Err("This script takes the species from a variable; edit the script instead")
        if (species !in 1..MAX_SPECIES) return FoxResult.Err("Species $species does not exist (1 to $MAX_SPECIES)")
        if (level != null && level !in 1..100) return FoxResult.Err("A level is 1 to 100")
        val records = when (val r = ScriptEntry.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val entry = records.records.getOrNull(encounter.file) as? ScriptEntry.Script ?: return FoxResult.Err("Script file ${encounter.file} is not a script")
        val items = entry.file.items.toMutableList()
        val command = items.getOrNull(encounter.commandIndex) as? ScriptItem.Instruction ?: return FoxResult.Err("The script changed: the command is gone")
        if (command.command.id != encounter.kind.commandId) return FoxResult.Err("The script changed: the command is not a ${encounter.kind.label.lowercase()} any more")
        fun put(index: Int, args: List<Long>) {
            val old = (items[index] as ScriptItem.Instruction).command
            items[index] = ScriptItem.Instruction(ScriptCommand(old.id, args, old.target))
        }
        if (encounter.viaVariable) {
            val assignment = items.getOrNull(encounter.index) as? ScriptItem.Instruction ?: return FoxResult.Err("The script changed: the assignment is gone")
            if (assignment.command.id != SET_VAR && assignment.command.id != SET_OR_COPY_VAR) return FoxResult.Err("The script changed: the assignment is gone")
            put(encounter.index, assignment.command.args.toMutableList().also { it[1] = species.toLong() })
        } else {
            put(encounter.commandIndex, command.command.args.toMutableList().also { it[0] = species.toLong() })
        }
        if (encounter.kind.hasLevel && level != null && !encounter.levelIsVariable) put(encounter.commandIndex, (items[encounter.commandIndex] as ScriptItem.Instruction).command.args.toMutableList().also { it[1] = level.toLong() })
        val changed = entry.file.copy(items = items)
        return records.with(encounter.file, ScriptEntry.Script(changed)).save(project)
    }

    const val MAX_SPECIES = 493

    private const val SET_VAR = 0x29
    private const val COPY_VAR = 0x2A
    private const val SET_OR_COPY_VAR = 0x2B
    private const val ADD_VAR = 0x27
    private const val SUB_VAR = 0x28
}
