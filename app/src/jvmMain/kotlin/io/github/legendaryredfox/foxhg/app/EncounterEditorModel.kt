package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterAreas
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterChange
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterData
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterDiff
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterGroup
import io.github.legendaryredfox.foxhg.core.nds.Game
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * Encounter editor state: the opened game's own table (editable) next to the other version's table (read-only
 * reference). Each ROM carries both; the game only reads its own, so "use SoulSilver's data" in a HeartGold ROM copies
 * from the table the game never loads.
 */
class EncounterEditorModel(project: RomProject, onChanged: () -> Unit = {}) {
    private val ownSpec: RecordSpec<EncounterData>
    private val otherSpec: RecordSpec<EncounterData>
    val otherName: String

    init {
        val isHeartGold = project.rom.header.game != Game.SOULSILVER_EN
        ownSpec = if (isHeartGold) EncounterData.HEARTGOLD else EncounterData.SOULSILVER
        otherSpec = if (isHeartGold) EncounterData.SOULSILVER else EncounterData.HEARTGOLD
        otherName = if (isHeartGold) "SoulSilver" else "HeartGold"
    }

    val areas: EncounterAreas = EncounterAreas.load(project, project.names)
    val editor = RecordEditorModel(project, ownSpec, { areas.label(it) }, onChanged, firstIndex = 1)
    private val other: List<EncounterData> = (NarcRecords.load(project, otherSpec) as? FoxResult.Ok)?.value?.records ?: emptyList()

    val names get() = editor.names

    /** Differences between the selected area and the other version's. */
    fun changesFromOther(index: Int = editor.selectedIndex): List<EncounterChange> {
        val mine = editor.records.getOrNull(index) ?: return emptyList()
        val theirs = other.getOrNull(index) ?: return emptyList()
        return EncounterDiff.between(mine, theirs)
    }

    fun differsFromOther(index: Int): Boolean = changesFromOther(index).isNotEmpty()

    fun otherRecord(index: Int): EncounterData? = other.getOrNull(index)

    fun copySelectedFromOther() {
        val theirs = other.getOrNull(editor.selectedIndex) ?: return
        editor.update { theirs }
    }

    /** Makes every area equal to the other version's table; returns how many areas changed. */
    fun copyAllFromOther(): Int {
        var changed = 0
        editor.updateAll { mine ->
            mine.mapIndexed { i, rec ->
                val theirs = other.getOrNull(i)
                if (theirs != null && theirs != rec) { changed++; theirs } else rec
            }
        }
        return changed
    }

    fun differingAreas(): Int = editor.records.indices.count { differsFromOther(it) }

    /** Takes [species] out of [group] in the selected area; false when it is absent or the last one there. */
    fun removeSpecies(group: EncounterGroup, species: Int): Boolean {
        val next = group.remove(editor.records.getOrNull(editor.selectedIndex) ?: return false, species) ?: return false
        editor.update { next }
        return true
    }

    /** Puts [species] into [group] of the selected area at about [percent] percent; false when it is already there or no slot is free to take. */
    fun addSpecies(group: EncounterGroup, species: Int, percent: Int): Boolean {
        val next = group.add(editor.records.getOrNull(editor.selectedIndex) ?: return false, species, percent) ?: return false
        editor.update { next }
        return true
    }
}
