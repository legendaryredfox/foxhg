package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.LabelRef
import io.github.legendaryredfox.foxhg.core.script.ScriptCommand
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptFile
import io.github.legendaryredfox.foxhg.core.script.ScriptItem
import io.github.legendaryredfox.foxhg.core.text.MessageText
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import io.github.legendaryredfox.foxhg.core.text.TextEntry

/**
 * Adds a trainer at the end of the trainer list. Four places hold one entry per trainer and all grow together: the header (`a/0/5/5`) and the
 * party (`a/0/5/6`) of [TrainerTable], the name (text bank [TextBanks.TRAINER_NAMES]) and the offset of the trainer's first battle message
 * (`a/1/3/1`, 0 for none, see [TrainerMessages]; the vanilla file stops at trainer 734, so the missing offsets of 735 to 737 are filled with 0 as well). The new trainer starts as a copy of another one, so it is a working trainer at once; its
 * messages are added in the messages editor. A refusal leaves the project as it was.
 *
 * A trainer on a map runs the script `3000 + trainer - 1`, entry `script_<trainer>` of script file 953. The vanilla file has 740 entries: `script_1` to
 * `script_739` share one body (so trainers 738 and 739 already work) and `script_740`, the game's own eye-contact script, belongs to the engine, so
 * trainer 740 can only be fought from a `TrainerBattle` in a script. From trainer 741 on this appends `script_<trainer>: GoTo script_1` at the end
 * of the file (the shared body reads the trainer from the number of the running script, so a jump keeps it). The game also keeps whether a trainer
 * was beaten in the flag `0x550 + trainer`, which for trainers from 735 on is a flag the game names unknown (0x82E and up). No script or object
 * of the vanilla ROM uses a flag from 0x82E to 0x963 (`RealRomTrainerFlagScanTest`), so trainers up to 1043 get a flag of their own; the next flag
 * is a story flag, so there is no trainer after that.
 */
object NewTrainer {
    /** Trainer flags are `0x550 + trainer`; the first flag a script or object uses above the trainer block is 0x964. */
    const val FIRST_USED_FLAG_ABOVE_TRAINERS = 0x964
    private const val TRAINER_FLAG_BASE = 0x550
    const val MAX_TRAINERS = FIRST_USED_FLAG_ABOVE_TRAINERS - TRAINER_FLAG_BASE

    fun add(project: RomProject, name: String, template: Int): FoxResult<Int> = project.atomically {
        val table = when (val t = TrainerTable.load(project)) { is FoxResult.Ok -> t.value; is FoxResult.Err -> return@atomically t }
        val count = table.trainers.size
        if (count >= MAX_TRAINERS) return@atomically FoxResult.Err("Trainer $count would use flag 0x${(TRAINER_FLAG_BASE + count).toString(16).uppercase()}, which the game already uses for something else: the most trainers are $MAX_TRAINERS")
        val source = table.trainers.getOrNull(template) ?: return@atomically FoxResult.Err("There is no trainer $template to copy (0 to ${count - 1})")
        val chars = when (val c = MessageText.encode(name)) { is FoxResult.Ok -> c.value; is FoxResult.Err -> return@atomically FoxResult.Err("Name: ${c.message}") }

        val messages = when (val m = TrainerMessages.load(project)) { is FoxResult.Ok -> m.value; is FoxResult.Err -> return@atomically m }
        if (messages.offsets.size > count) return@atomically FoxResult.Err("The message offsets cover ${messages.offsets.size} trainers but the ROM has $count")
        val banks = when (val b = TextArchive.load(project)) { is FoxResult.Ok -> b.value; is FoxResult.Err -> return@atomically b }
        val names = banks.records.getOrNull(TextBanks.TRAINER_NAMES) ?: return@atomically FoxResult.Err("The ROM has no trainer name bank ${TextBanks.TRAINER_NAMES}")
        if (names.entries.size != count) return@atomically FoxResult.Err("The name bank has ${names.entries.size} names but the ROM has $count trainers")

        (table.withAll(table.trainers + source).save(project) as? FoxResult.Err)?.let { return@atomically it }
        val grown = TextArchive(names.key, names.entries + TextEntry(chars))
        (banks.with(TextBanks.TRAINER_NAMES, grown).save(project) as? FoxResult.Err)?.let { return@atomically it }
        (TrainerMessages(messages.trainers, messages.triggers, messages.offsets + List(count + 1 - messages.offsets.size) { 0 }, messages.texts).save(project) as? FoxResult.Err)?.let { return@atomically it }
        (giveScript(project, count) as? FoxResult.Err)?.let { return@atomically it }
        FoxResult.Ok(count)
    }

    /** Script file of the trainers placed on maps. */
    const val TRAINER_SCRIPT_FILE = 953
    private const val GO_TO = 0x16

    /** Makes sure the script file 953 has an entry `script_<trainer>`; a trainer the file already covers changes nothing. */
    private fun giveScript(project: RomProject, trainer: Int): FoxResult<Unit> {
        val records = when (val r = ScriptEntry.load(project)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return r }
        val file = (records.records.getOrNull(TRAINER_SCRIPT_FILE) as? ScriptEntry.Script)?.file ?: return FoxResult.Err("Script file $TRAINER_SCRIPT_FILE is not a script")
        if (file.scriptCount >= trainer) return FoxResult.Ok(Unit)
        val jump = ScriptItem.Instruction(ScriptCommand(GO_TO, listOf(0L), LabelRef(LabelRef.Kind.SCRIPT, 1)))
        val items = file.items + (file.scriptCount + 1..trainer).flatMap { listOf(ScriptItem.Label(LabelRef(LabelRef.Kind.SCRIPT, it)), jump) }
        val grown = ScriptFile(items)
        grown.problem()?.let { return FoxResult.Err("Script file $TRAINER_SCRIPT_FILE: $it") }
        return records.with(TRAINER_SCRIPT_FILE, ScriptEntry.Script(grown)).save(project)
    }
}
