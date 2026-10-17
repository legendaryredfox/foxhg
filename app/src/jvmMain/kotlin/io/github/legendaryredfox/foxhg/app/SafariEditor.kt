package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.data.safari.SafariEncounter
import io.github.legendaryredfox.foxhg.core.data.safari.SafariGroup
import io.github.legendaryredfox.foxhg.core.data.safari.SafariRequirement
import io.github.legendaryredfox.foxhg.core.data.safari.SafariZoneFile
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The Safari Zone's twelve kinds of area (`a/2/3/0`): the Pokémon of grass, water and the rods by time of day, and the ones the player's objects bring in. */
class SafariEditorModel(project: RomProject, onChanged: () -> Unit = {}, first: Int = 0, onSelected: (Int) -> Unit = {}) {
    val files = RecordEditorModel(project, SafariZoneFile.SPEC, { i -> "%02d  %s".format(i, SafariZoneFile.NAMES.getOrElse(i) { "Area $i" }) }, onChanged, firstIndex = first, onSelected = onSelected)
    var group: Int by mutableIntStateOf(0)

    private fun change(edit: (SafariGroup) -> SafariGroup) = files.update { it.withGroup(group, edit(it.groups[group])) }

    fun setBase(time: Int, slot: Int, edit: (SafariEncounter) -> SafariEncounter) = change { it.withBase(time, slot, edit(it.base(time)[slot])) }

    fun setObject(time: Int, slot: Int, edit: (SafariEncounter) -> SafariEncounter) = change { it.withObject(time, slot, edit(it.objects(time)[slot])) }

    fun setRequirement(slot: Int, optional: Boolean, edit: (SafariRequirement) -> SafariRequirement) =
        change { it.withRequirement(slot, optional, edit((if (optional) it.optional else it.required)[slot])) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SafariEditor(model: SafariEditorModel, modifier: Modifier = Modifier) {
    val species = model.files.names?.species ?: emptyList()
    RecordListDetail(model.files, modifier, rowDetail = { i -> model.files.records.getOrNull(i)?.takeIf { it.stub }?.let { "no data" } }) { file ->
        val index = model.files.selectedIndex
        EditorHeader(index.toString(), nameOf(model.files.label(index)))
        if (file.stub) {
            Text("This area has no Pokémon data in the ROM.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@RecordListDetail
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SafariZoneFile.GROUPS.forEachIndexed { g, name -> ToggleChip(name, model.group == g, { model.group = g }) }
        }
        val group = file.groups[model.group]
        SafariZoneFile.TIMES.forEachIndexed { time, timeName ->
            SectionCard(
                "$timeName: ${SafariZoneFile.GROUPS[model.group]}",
                subtitle = "The ten Pokémon the area gives at this time of day when no object arrangement applies",
            ) {
                group.base(time).forEachIndexed { slot, e ->
                    val tag = "$timeName ${slot + 1}"
                    FieldFlow {
                        SpeciesPicker(tag, e.species, species, { v -> model.setBase(time, slot) { it.copy(species = v) } }, Modifier.width(240.dp))
                        NumberField("Level", e.level, 0..100, { v -> model.setBase(time, slot) { it.copy(level = v) } }, Modifier.width(90.dp), tag = "$tag level")
                    }
                }
            }
        }
        if (group.objectSlots > 0) {
            SectionCard(
                "Object arrangements",
                subtitle = "${plural(group.objectSlots, "arrangement")}: when the player has placed the required objects (and, if set, the optional ones) in the area, " +
                    "this Pokémon replaces the usual ones at that time of day",
            ) {
                for (n in 0 until group.objectSlots) {
                    Chip("Arrangement ${n + 1}", emphasis = true)
                    for (time in 0..2) {
                        val e = group.objects(time)[n]
                        val tag = "Arrangement ${n + 1} ${SafariZoneFile.TIMES[time]}"
                        FieldFlow {
                            SpeciesPicker(tag, e.species, species, { v -> model.setObject(time, n) { it.copy(species = v) } }, Modifier.width(240.dp))
                            NumberField("Level", e.level, 0..100, { v -> model.setObject(time, n) { it.copy(level = v) } }, Modifier.width(90.dp), tag = "$tag level")
                        }
                    }
                    for (optional in listOf(false, true)) {
                        val r = (if (optional) group.optional else group.required)[n]
                        val label = if (optional) "Optional" else "Required"
                        FieldFlow {
                            PickerField("Arrangement ${n + 1} $label", r.type, SafariZoneFile.OBJECT_TYPES, { v -> model.setRequirement(n, optional) { it.copy(type = v) } }, Modifier.width(200.dp))
                            NumberField("Quantity", r.quantity, 0..255, { v -> model.setRequirement(n, optional) { it.copy(quantity = v) } }, Modifier.width(90.dp), tag = "Arrangement ${n + 1} $label quantity")
                        }
                    }
                }
            }
        }
    }
}
