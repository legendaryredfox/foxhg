package io.github.legendaryredfox.foxhg.core.data.research

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.ParamSpec
import io.github.legendaryredfox.foxhg.core.script.ScriptCommands
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptItem

/** What a number can be looked up as: a script flag, a script variable, an item, a trainer, or an overworld sprite entry. */
enum class UsageKind(val label: String, private val matches: ((ParamSpec) -> Boolean)?) {
    /** The `flag` parameter of `SetFlag`, `ClearFlag`, `CheckFlag` and the like (a flag held in a variable is a variable use). */
    FLAG("Flag", { it.type == "u16" && it.name == "flag" }),

    /** Parameters of type `var` and the ones named like variables (`variable`, `var_1`, `result`, `retvar`...). */
    VARIABLE("Variable", { it.type == "var" || VARIABLE_NAME.matches(it.name) }),
    ITEM("Item", { it.type == "u16" && (it.name == "item" || it.name == "item_id") }),

    /** The trainer parameters of `TrainerBattle` and the like (not `trainer_class`). */
    TRAINER("Trainer", { TRAINER_NAME.matches(it.name) }),
    SPRITE("Overworld sprite entry", null),
    ;

    /** True for scripts: [SPRITE] is only on events. */
    val inScripts: Boolean get() = matches != null
    internal fun matchesParam(p: ParamSpec): Boolean = matches?.invoke(p) ?: false

    private companion object {
        val VARIABLE_NAME = Regex("var(_\\d+|_or_addend|_dest|_flag)?|variable(_2)?|result|retvar|countdown_variable")
        val TRAINER_NAME = Regex("trainer(_[12])?(_id)?|trainer_[12]_id")
    }
}

/** Where a number is used: a command of script file [file] (the item number [index] of the file) or an event of event file [file] ([index] counts within its list). */
data class Usage(val place: Place, val file: Int, val index: Int, val text: String) {
    enum class Place { SCRIPT, EVENT }
}

/**
 * The "Research Helper": every place of the game that uses a given flag, variable, item, trainer or overworld sprite entry, found by reading all script
 * files (the commands whose parameter has that type in the command database) and all event files (the flag of an overworld object, the variable of
 * a trigger, the sprite of an object, and for trainers the object whose script is `3000 + trainer - 1` or `5000 + trainer - 1`, the numbers of the decomp's
 * `std_trainer` and `std_trainer_2`). A command whose parameter is a literal number that happens to equal a variable number is not told apart:
 * the database says which parameters are variables, not which hold a literal.
 */
object Usages {
    const val STD_TRAINER = 3000
    const val STD_TRAINER_2 = 5000
    const val TRAINER_COUNT = 740

    fun find(project: RomProject, kind: UsageKind, id: Int): FoxResult<List<Usage>> {
        val out = ArrayList<Usage>()
        if (kind.inScripts) {
            val scripts = when (val r = ScriptEntry.load(project)) {
                is FoxResult.Ok -> r.value
                is FoxResult.Err -> return r
            }
            scripts.records.forEachIndexed { file, entry ->
                val script = (entry as? ScriptEntry.Script)?.file ?: return@forEachIndexed
                script.items.forEachIndexed { index, item ->
                    val command = (item as? ScriptItem.Instruction)?.command ?: return@forEachIndexed
                    val info = ScriptCommands.byId(command.id) ?: return@forEachIndexed
                    val params = info.params ?: return@forEachIndexed
                    val hit = params.withIndex().any { (i, p) -> kind.matchesParam(p) && command.args.getOrNull(i) == id.toLong() }
                    if (hit) out += Usage(Usage.Place.SCRIPT, file, index, "${info.name} ${command.args.joinToString(", ")}")
                }
            }
        }
        val events = when (val r = EventFile.load(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        events.records.forEachIndexed { file, ev ->
            ev.overworlds.forEachIndexed { i, ow ->
                if (ow.isAlias) return@forEachIndexed
                when (kind) {
                    UsageKind.FLAG -> if (ow.flag == id) out += Usage(Usage.Place.EVENT, file, i, "overworld ${ow.id} appears or hides with this flag")
                    UsageKind.SPRITE -> if (ow.spriteEntry == id) out += Usage(Usage.Place.EVENT, file, i, "overworld ${ow.id} uses this sprite entry")
                    UsageKind.TRAINER -> if (ow.script == STD_TRAINER + id - 1 || ow.script == STD_TRAINER_2 + id - 1) out += Usage(Usage.Place.EVENT, file, i, "overworld ${ow.id} is this trainer (script ${ow.script})")
                    else -> {}
                }
            }
            if (kind == UsageKind.VARIABLE) ev.triggers.forEachIndexed { i, t -> if (t.variable == id && t.variable != 0) out += Usage(Usage.Place.EVENT, file, i, "trigger script ${t.script} runs while this variable is ${t.expectedValue}") }
        }
        return FoxResult.Ok(out)
    }
}
