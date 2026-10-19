package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.text.MessageText
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import io.github.legendaryredfox.foxhg.core.text.TextEntry

/**
 * Adds a trainer at the end of the trainer list. Four places hold one entry per trainer and all grow together: the header (`a/0/5/5`) and the
 * party (`a/0/5/6`) of [TrainerTable], the name (text bank [TextBanks.TRAINER_NAMES]) and the offset of the trainer's first battle message
 * (`a/1/3/1`, 0 for none, see [TrainerMessages]; the vanilla file stops at trainer 734, so the missing offsets of 735 to 737 are filled with 0 as well). The new trainer starts as a copy of another one, so it is a working trainer at once; its
 * messages are added in the messages editor. A refusal leaves the project as it was.
 *
 * What this does not do: a trainer on a map uses the script `3000 + trainer - 1` of script file 953, which has entries for the 737 trainers of the
 * game and then three entries of its own (738 to 740, the rematch phone call among them), so a new trainer can not be met from the shared trainer
 * script without moving those; a script of the map starts its battle with `TrainerBattle` instead. The game also keeps whether a trainer was beaten
 * in the flag `0x550 + trainer`, which for trainers from 738 on is a flag the game names unknown (0x832 and up).
 */
object NewTrainer {
    /** Trainers are numbered by a `u16` in the message table and the scripts. */
    const val MAX_TRAINERS = 0xFFFF

    fun add(project: RomProject, name: String, template: Int): FoxResult<Int> = project.atomically {
        val table = when (val t = TrainerTable.load(project)) { is FoxResult.Ok -> t.value; is FoxResult.Err -> return@atomically t }
        val count = table.trainers.size
        if (count >= MAX_TRAINERS) return@atomically FoxResult.Err("The game numbers trainers with 16 bits: there is no room for another one")
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
        FoxResult.Ok(count)
    }
}
