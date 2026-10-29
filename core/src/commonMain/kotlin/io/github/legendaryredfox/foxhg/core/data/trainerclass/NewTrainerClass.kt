package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.text.MessageText
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import io.github.legendaryredfox.foxhg.core.text.TextEntry
import io.github.legendaryredfox.foxhg.core.u8

/**
 * Adds a trainer class at the end of the class list (HeartGold English). Four things grow together:
 * - the class name (text bank [TextBanks.TRAINER_CLASSES]);
 * - the front picture, five files of [HgssLayout.TRAINER_FRONT_SPRITES], copied from the class [add] is told to copy;
 * - the gender table, which is read with no bound through one pointer in the ARM9: it is moved to [HgssLayout.CLASS_TABLE_AREA] of the
 *   synthetic overlay (so the ARM9 expansion patch must be applied) with the class appended. Vanilla has 128 entries for 129 classes; class
 *   128 keeps the byte the game reads for it, the first byte after the table;
 * - the prize table of overlay 12: moved to the same area with the row `{class, multiplier}` appended, its two pointers set and the three
 *   `cmp r4, #count` immediates raised. A class missing from this table gets the prize of row 2.
 * The eye-contact music table is not touched: a class missing from it plays the Youngster theme. A refusal leaves the project as it was.
 */
object NewTrainerClass {
    fun add(project: RomProject, name: String, template: Int, gender: Int, prizeMultiplier: Int): FoxResult<Int> = project.atomically {
        if (gender !in 0..2) return@atomically FoxResult.Err("Gender is 0 male, 1 female or 2 neither")
        if (prizeMultiplier !in 0..0xFFFF) return@atomically FoxResult.Err("The prize multiplier is a 16 bit number")
        val chars = when (val c = MessageText.encode(name)) { is FoxResult.Ok -> c.value; is FoxResult.Err -> return@atomically FoxResult.Err("Name: ${c.message}") }
        val synth = TrainerClassTables.syntheticOverlay(project)
            ?: return@atomically FoxResult.Err("A new class needs the synthetic overlay: apply the ARM9 expansion patch in the ROM toolbox first")

        val banks = when (val b = TextArchive.load(project)) { is FoxResult.Ok -> b.value; is FoxResult.Err -> return@atomically b }
        val names = banks.records.getOrNull(TextBanks.TRAINER_CLASSES) ?: return@atomically FoxResult.Err("The ROM has no trainer class name bank ${TextBanks.TRAINER_CLASSES}")
        val count = names.entries.size
        if (count >= HgssLayout.MAX_TRAINER_CLASSES) return@atomically FoxResult.Err("The game keeps the class in a byte and the prize rows in a counter of 8 bits: the most classes are ${HgssLayout.MAX_TRAINER_CLASSES}")
        if (template !in 0 until count) return@atomically FoxResult.Err("There is no class $template to copy (0 to ${count - 1})")

        val pictures = when (val p = NarcRecords.load(project, HgssLayout.TRAINER_FRONT_SPRITES, { FoxResult.Ok(it) }, { it })) { is FoxResult.Ok -> p.value; is FoxResult.Err -> return@atomically p }
        val per = HgssLayout.FILES_PER_TRAINER_CLASS_PICTURE
        if (pictures.records.size != count * per) return@atomically FoxResult.Err("${HgssLayout.TRAINER_FRONT_SPRITES} has ${pictures.records.size} files but $count classes need ${count * per}")

        var genders = when (val g = TrainerClassGenders.load(project)) { is FoxResult.Ok -> g.value.genders; is FoxResult.Err -> return@atomically g }
        val arm9 = when (val a = project.readArm9()) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return@atomically a }
        if (genders.size == TrainerClassGenders.COUNT) genders = genders + List(count - genders.size) { arm9.u8(HgssLayout.TRAINER_CLASS_GENDER_ARM9_OFFSET + TrainerClassGenders.COUNT + it) }
        if (genders.size != count) return@atomically FoxResult.Err("The gender table has ${genders.size} entries for $count classes")
        genders = genders + gender

        val prizes = when (val p = TrainerClassPrizes.load(project)) { is FoxResult.Ok -> p.value.entries; is FoxResult.Err -> return@atomically p }
        val rows = prizes + PrizeEntry(count, prizeMultiplier)
        val overlay = when (val o = project.readOverlay(HgssLayout.PRIZE_OVERLAY)) { is FoxResult.Ok -> o.value.copyOf(); is FoxResult.Err -> return@atomically o }

        // the area must be empty or already ours
        val area = HgssLayout.CLASS_TABLE_AREA
        val prizeAt = HgssLayout.CLASS_TABLE_AREA_PRIZES
        val moved = overlay.u8(HgssLayout.PRIZE_COUNT_IMMEDIATES_OVERLAY[0]) != TrainerClassPrizes.COUNT || prizes.size != TrainerClassPrizes.COUNT
        if (!moved && (area until area + HgssLayout.CLASS_TABLE_AREA_SIZE).any { synth[it] != 0.toByte() }) {
            return@atomically FoxResult.Err("The synthetic overlay is not empty at 0x${area.toString(16)}: something else is there")
        }

        val out = synth.copyOf()
        genders.forEachIndexed { i, g -> out[area + i] = g.toByte() }
        rows.forEachIndexed { i, e -> out.putU16(prizeAt + i * 4, e.trainerClass); out.putU16(prizeAt + i * 4 + 2, e.multiplier) }
        val genderPointer = (HgssLayout.SYNTHETIC_OVERLAY_ADDRESS + area).toLong()
        val prizePointer = (HgssLayout.SYNTHETIC_OVERLAY_ADDRESS + prizeAt).toLong()
        arm9.copyOf().also { it.putU32(HgssLayout.TRAINER_CLASS_GENDER_POINTER_ARM9, genderPointer) }.let { (project.writeArm9(it) as? FoxResult.Err)?.let { e -> return@atomically e } }
        overlay.putU32(HgssLayout.PRIZE_TABLE_POINTER_OVERLAY, prizePointer)
        overlay.putU32(HgssLayout.PRIZE_TABLE_POINTER_PLUS_2_OVERLAY, prizePointer + 2)
        for (at in HgssLayout.PRIZE_COUNT_IMMEDIATES_OVERLAY) {
            if (overlay.u8(at + 1) != 0x2C) return@atomically FoxResult.Err("Overlay ${HgssLayout.PRIZE_OVERLAY} 0x${at.toString(16)} is not the `cmp r4, #count` the prize search uses")
            overlay[at] = rows.size.toByte()
        }
        (project.writeOverlay(HgssLayout.PRIZE_OVERLAY, overlay) as? FoxResult.Err)?.let { return@atomically it }
        (PatchTarget.SyntheticOverlay.write(project, out) as? FoxResult.Err)?.let { return@atomically it }

        val grownNames = TextArchive(names.key, names.entries + TextEntry(chars))
        (banks.with(TextBanks.TRAINER_CLASSES, grownNames).save(project) as? FoxResult.Err)?.let { return@atomically it }
        val copy = pictures.records.subList(template * per, template * per + per)
        (pictures.withAll(pictures.records + copy.map { it.copyOf() }).save(project) as? FoxResult.Err)?.let { return@atomically it }
        FoxResult.Ok(count)
    }
}
