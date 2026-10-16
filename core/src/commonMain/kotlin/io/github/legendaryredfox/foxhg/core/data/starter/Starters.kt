package io.github.legendaryredfox.foxhg.core.data.starter

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalTable
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u8

/**
 * The three starter Pokémon of HeartGold (Chikorita, Cyndaquil, Totodile in vanilla) and the level and held item they
 * are given. None of it is a table of a NARC: the chooser's code in the ARM9 holds the species as three words in a
 * literal pool, the level as the operand of one instruction and the held item as another, and the chooser overlay
 * keeps a second copy of the species. The places are found by the bytes around them, as the layout of the ARM9 can
 * shift in a modified ROM (see docs/formats.md). [heldItem] and [level] are null when their place is not recognised.
 */
data class Starters(val species: List<Int>, val heldItem: Int?, val level: Int?) {
    init { require(species.size == 3) { "There are exactly three starters" } }

    /** Whether [species] are the same as in [other], so the follow-up patches have nothing to do. */
    fun sameSpecies(other: Starters) = species == other.species

    /** Writes the species, level and held item into a copy of [arm9]; null when the species place is not found. */
    fun applyToArm9(arm9: ByteArray): ByteArray? {
        val base = StarterPlaces.speciesBase(arm9) ?: return null
        val out = arm9.copyOf()
        species.forEachIndexed { i, s -> out.putU16(base + 4 * i, s) }
        if (heldItem != null) StarterPlaces.heldItem(arm9)?.let { out[it] = heldItem.toByte() }
        if (level != null) StarterPlaces.level(arm9)?.let { out[it] = level.toByte() }
        return out
    }

    /** Writes the species into the chooser overlay's table; null when that table is not found. */
    fun applyToOverlay(overlay: ByteArray): ByteArray? {
        val at = StarterPlaces.criesTable(overlay) ?: return null
        val out = overlay.copyOf()
        species.forEachIndexed { i, s -> out.putU16(at + 4 * i, s) }
        return out
    }

    companion object {
        val VANILLA = Starters(listOf(152, 155, 158), heldItem = 0, level = 5)
        const val MAX_SPECIES = 493

        fun parse(arm9: ByteArray): FoxResult<Starters> {
            val base = StarterPlaces.speciesBase(arm9) ?: return FoxResult.Err("The starter table was not found in the ARM9 (it may be changed by another tool)")
            return FoxResult.Ok(Starters(List(3) { arm9.u16(base + 4 * it) }, StarterPlaces.heldItem(arm9)?.let { arm9.u8(it) }, StarterPlaces.level(arm9)?.let { arm9.u8(it) }))
        }

        fun load(project: RomProject): FoxResult<Starters> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> parse(a.value)
            is FoxResult.Err -> a
        }

        /** What [change] did besides the starters themselves. */
        data class Report(val rivalScriptsPatched: Int, val textPatched: Boolean, val chooserPatched: Boolean)

        /**
         * Makes [new] the starters: the ARM9, the chooser overlay, the rival's counter-pick in the scripts and the species
         * and type words in the chooser text. Nothing is written when the ARM9 place is not found; the other places are
         * best effort and the [Report] says what was reached.
         */
        fun change(project: RomProject, new: Starters): FoxResult<Report> {
            if (new.species.any { it !in 1..MAX_SPECIES }) return FoxResult.Err("Starter species must be 1 to $MAX_SPECIES")
            if (new.level != null && new.level !in 1..100) return FoxResult.Err("The starter level must be 1 to 100")
            if (new.heldItem != null && new.heldItem !in 0..255) return FoxResult.Err("The starter held item must be an item of 0 to 255 (the game stores one byte)")
            val arm9 = when (val a = project.readArm9()) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val old = when (val o = parse(arm9)) { is FoxResult.Ok -> o.value; is FoxResult.Err -> return o }
            val patched = new.applyToArm9(arm9) ?: return FoxResult.Err("The starter table was not found in the ARM9")
            (project.writeArm9(patched) as? FoxResult.Err)?.let { return it }

            val chooser = (project.readOverlay(HgssLayout.STARTER_CHOOSER_OVERLAY) as? FoxResult.Ok)?.value?.let { new.applyToOverlay(it) }
                ?.also { project.writeOverlay(HgssLayout.STARTER_CHOOSER_OVERLAY, it) } != null
            if (old.sameSpecies(new)) return FoxResult.Ok(Report(0, false, chooser))
            val scripts = RivalScripts.retarget(project, old.species, new.species)
            val text = StarterText.retarget(project, old.species, new.species)
            return FoxResult.Ok(Report(scripts, text, chooser))
        }
    }
}

/** Where the starter values sit, found by the bytes around them. */
internal object StarterPlaces {
    /** Ends the literal pool of the three species words: the words start 13 bytes before it. */
    private val SPECIES_SIGNATURE = byteArrayOf(0x03, 0x03, 0x1A, 0x12, 0x01, 0x23, 0x00, 0x00)
    private const val SPECIES_BEFORE_SIGNATURE = 13

    /** The bytes right after the held item operand; the vanilla place is tried first, then a search. */
    private val HELD_ITEM_SUFFIX = intArrayOf(0x20, 0x09, 0x90, 0x60, 0x19, 0x06, 0x21, 0x09, 0xAA, 0xD8, 0xF7, 0x9A, 0xFD, 0x06, 0x98, 0xEC, 0x35).map { it.toByte() }.toByteArray()
    private const val HELD_ITEM_VANILLA = 0x960FE

    /** The level operand sits this far before the held item operand, followed by the bytes 22 20. */
    private const val LEVEL_BEFORE_HELD_ITEM = 28
    private val LEVEL_SUFFIX = byteArrayOf(0x22, 0x20)

    /** The chooser overlay's table of the three species (u32 each) follows these bytes. */
    private val CRIES_PREFIX = intArrayOf(
        0x00, 0x04, 0x00, 0x0C, 0x10, 0xBD, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0xE0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xE0, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00,
    ).map { it.toByte() }.toByteArray()

    fun speciesBase(arm9: ByteArray): Int? = findOnce(arm9, SPECIES_SIGNATURE)?.let { it - SPECIES_BEFORE_SIGNATURE }?.takeIf { it >= 0 && it + 10 <= arm9.size }

    fun heldItem(arm9: ByteArray): Int? {
        if (matchesAt(arm9, HELD_ITEM_VANILLA + 1, HELD_ITEM_SUFFIX)) return HELD_ITEM_VANILLA
        return findOnce(arm9, HELD_ITEM_SUFFIX)?.minus(1)
    }

    fun level(arm9: ByteArray): Int? {
        val at = (heldItem(arm9) ?: return null) - LEVEL_BEFORE_HELD_ITEM
        return at.takeIf { it >= 0 && matchesAt(arm9, it + 1, LEVEL_SUFFIX) }
    }

    fun criesTable(overlay: ByteArray): Int? = findOnce(overlay, CRIES_PREFIX)?.plus(CRIES_PREFIX.size)?.takeIf { it + 12 <= overlay.size }

    fun matchesAt(data: ByteArray, at: Int, pattern: ByteArray): Boolean =
        at >= 0 && at + pattern.size <= data.size && pattern.indices.all { data[at + it] == pattern[it] }

    /** The offset of [pattern] in [data] when it occurs exactly once, else null. */
    fun findOnce(data: ByteArray, pattern: ByteArray): Int? {
        var found = -1
        for (i in 0..data.size - pattern.size) {
            if (matchesAt(data, i, pattern)) { if (found >= 0) return null; found = i }
        }
        return found.takeIf { it >= 0 }
    }
}

/**
 * The rival's team depends on the player's starter: seven script files compare the starter variable with the first
 * starter's species and, on the other branch, with the second's. They are kept in step with the starters.
 */
internal object RivalScripts {
    /** Commands up to and including the comparison with the first species (a placeholder at [FIRST]), then a conditional jump. */
    private val PATTERN = intArrayOf(0xCE, 0x00, 0x0C, 0x80, 0x11, 0x00, 0x0C, 0x80, 0x00, 0x00, 0x1C, 0x00, 0x05).map { it.toByte() }.toByteArray()
    private const val FIRST = 8
    private const val JUMP = 13
    private const val AFTER_JUMP = 17
    private const val COMPARE = 0x11
    private const val SECOND_VALUE = 4

    /** Rewrites the comparisons for [old] to [new]; returns how many script files were changed. */
    fun retarget(project: RomProject, old: List<Int>, new: List<Int>): Int {
        val bytes = project.read(HgssLayout.SCRIPTS) ?: return 0
        val narc = (Narc.parse(bytes) as? FoxResult.Ok)?.value ?: return 0
        var changed = 0
        val files = narc.files.toMutableList()
        for (id in HgssLayout.RIVAL_SCRIPT_FILES) {
            val data = files.getOrNull(id) ?: continue
            patch(data, old, new)?.let { files[id] = it; changed++ }
        }
        if (changed > 0) project.write(HgssLayout.SCRIPTS, Narc(files, narc.nameTable).toBytes())
        return changed
    }

    internal fun patch(data: ByteArray, old: List<Int>, new: List<Int>): ByteArray? {
        val pattern = PATTERN.copyOf().also { it.putU16(FIRST, old[0]) }
        val base = StarterPlaces.findOnce(data, pattern) ?: return null
        val out = data.copyOf()
        out.putU16(base + FIRST, new[0])
        val jump = u32(out, base + JUMP)
        val second = jump + base + AFTER_JUMP
        if (second >= 0 && second + SECOND_VALUE + 2 <= out.size && out[second].toInt() == COMPARE && out.u16(second + SECOND_VALUE) == old[1]) {
            out.putU16(second + SECOND_VALUE, new[1])
        }
        return out.takeUnless { it.contentEquals(data) }
    }

    private fun u32(d: ByteArray, at: Int) = d.u16(at) or (d.u16(at + 2) shl 16)
}

/** The professor's questions and the Poké Ball descriptions name each starter and its type; they follow the species. */
internal object StarterText {
    /** Messages 1 to 3 ask about each starter, 4 to 6 describe it (HeartGold English: "Professor Elm: So, you like CHIKORITA, the Grass-type Pokémon?"). */
    private fun messagesOf(starter: Int) = listOf(starter + 1, starter + 4)

    fun retarget(project: RomProject, old: List<Int>, new: List<Int>): Boolean {
        val names = project.names ?: return false
        val personal = (PersonalTable.load(project) as? FoxResult.Ok)?.value ?: return false
        fun speciesName(s: Int) = names.species.getOrNull(s)?.takeIf { it.isNotBlank() }
        fun typeName(s: Int) = personal.records.getOrNull(s)?.let { names.types.getOrNull(it.type1) }?.takeIf { it.isNotBlank() }
        val table = (io.github.legendaryredfox.foxhg.core.text.TextArchive.load(project) as? FoxResult.Ok)?.value ?: return false
        var archive = table.records.getOrNull(HgssLayout.STARTER_TEXT_BANK) ?: return false
        var changed = false
        for (i in 0 until 3) {
            for (m in messagesOf(i)) {
                val current = archive.entries.getOrNull(m)?.text() ?: continue
                var text = current
                for ((from, to) in listOf(speciesName(old[i]) to speciesName(new[i]), typeName(old[i]) to typeName(new[i]))) {
                    if (from != null && to != null && from != to && text.contains(from)) text = text.replace(from, to)
                }
                if (text != current) (archive.withText(m, text) as? FoxResult.Ok)?.let { archive = it.value; changed = true }
            }
        }
        if (changed) table.with(HgssLayout.STARTER_TEXT_BANK, archive).save(project)
        return changed
    }
}
