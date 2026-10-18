package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.testTag
import io.github.legendaryredfox.foxhg.app.ui.ConfirmDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.Fox
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterData
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterGroup
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterSlot

@Composable
fun EncounterEditor(model: EncounterEditorModel, modifier: Modifier = Modifier) {
    val editor = model.editor
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        VersionCard(model)
        RecordListDetail(editor, Modifier.fillMaxSize(), flagged = { model.differsFromOther(it) }) { data -> Detail(model, data) }
    }
}

/** "land 8 morning" as "Grass slot 8, morning"; the core names changes the way the file is laid out. */
internal fun friendlyChange(what: String): String {
    Regex("""land (\d+) (level|morning|day|night)""").matchEntire(what)?.let { m ->
        val part = m.groupValues[2]
        return "Grass slot ${m.groupValues[1]}, " + if (part == "level") "level" else "$part species"
    }
    Regex("""rate (.+)""").matchEntire(what)?.let { return "Rate, ${it.groupValues[1]}" }
    return what.replaceFirstChar { it.uppercase() }
}

@Composable
private fun VersionCard(model: EncounterEditorModel) {
    var message by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    val differing = model.differingAreas()
    SectionCard(
        stringResource(Res.string.encounter_compare_with_x, model.otherName),
        subtitle = if (differing > 0) "${plural(differing, "area")} ${if (differing == 1) "has" else "have"} different wild Pokémon. Areas marked \"differs\" in the list are the ones." else stringResource(Res.string.encounter_every_area_matches_x, model.otherName),
        actions = {
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            OutlinedButton(onClick = { asking = true }, enabled = differing > 0, modifier = Modifier.testTag("copy-all-open")) { Text(stringResource(Res.string.encounter_make_every_area_match_x, model.otherName)) }
        },
    ) {}
    if (asking) ConfirmDialog(
        tr(Res.string.encounter_make_every_area_match_x_2, model.otherName),
        tr(Res.string.encounter_x_will_take_x_s_wild_pok_mon_rates, pluralStringResource(Res.plurals.plural_area, differing, differing), model.otherName),
        tr(Res.string.encounter_make_them_match),
        onConfirm = {
            asking = false
            val n = model.copyAllFromOther()
            message = tr(Res.string.encounter_copied_into_x, trPlural(Res.plurals.plural_area, n, n))
        },
        onDismiss = { asking = false },
    )
}

@Composable
private fun Detail(model: EncounterEditorModel, d: EncounterData) {
    val editor = model.editor
    val species = model.names?.species ?: emptyList()
    val maps = model.areas.maps(editor.selectedIndex)
    EditorHeader(editor.selectedIndex.toString(), nameOf(editor.label(editor.selectedIndex)), trailing = { Chip(plural(maps.size, "map")) })

    val changes = model.changesFromOther()
    if (changes.isNotEmpty()) {
        var expanded by remember(editor.selectedIndex) { mutableStateOf(false) }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.encounter_x_from_x, pluralStringResource(Res.plurals.plural_difference, changes.size, changes.size), model.otherName), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = model::copySelectedFromOther) { Text(stringResource(Res.string.encounter_use_x_s_data_here, model.otherName)) }
                }
                changes.take(if (expanded) changes.size else 5).forEach { c ->
                    val isSpecies = listOf("species", "morning", "day", "night", "swarm", "sound", "fishing").any { c.what.contains(it) } && !c.what.contains("level")
                    val from = if (isSpecies) species.getOrNull(c.from) ?: c.from else c.from
                    val to = if (isSpecies) species.getOrNull(c.to) ?: c.to else c.to
                    Text("${friendlyChange(c.what)}:  $from  →  $to", style = Fox.mono.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (changes.size > 5) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) stringResource(Res.string.encounter_show_fewer) else stringResource(Res.string.encounter_show_all_x, changes.size)) }
            }
        }
    }

    SectionCard(stringResource(Res.string.encounter_encounter_rates), subtitle = stringResource(Res.string.encounter_chance_that_a_step_or_cast_starts)) {
        FieldFlow {
            NumberField(stringResource(Res.string.encounter_walking), d.rateWalking, 0..255, { v -> editor.update { it.copy(rateWalking = v) } }, Modifier.width(92.dp))
            NumberField(stringResource(Res.string.encounter_surfing), d.rateSurfing, 0..255, { v -> editor.update { it.copy(rateSurfing = v) } }, Modifier.width(92.dp))
            NumberField(stringResource(Res.string.encounter_rock_smash), d.rateRockSmash, 0..255, { v -> editor.update { it.copy(rateRockSmash = v) } }, Modifier.width(100.dp))
            NumberField(stringResource(Res.string.encounter_old_rod), d.rateOldRod, 0..255, { v -> editor.update { it.copy(rateOldRod = v) } }, Modifier.width(92.dp))
            NumberField(stringResource(Res.string.encounter_good_rod), d.rateGoodRod, 0..255, { v -> editor.update { it.copy(rateGoodRod = v) } }, Modifier.width(92.dp))
            NumberField(stringResource(Res.string.encounter_super_rod), d.rateSuperRod, 0..255, { v -> editor.update { it.copy(rateSuperRod = v) } }, Modifier.width(92.dp))
        }
    }

    RosterCard(model, d, species)

    SectionCard(stringResource(Res.string.encounter_grass), subtitle = stringResource(Res.string.encounter_each_slot_has_one_level_and_a_spec)) {
        for (i in 0 until EncounterData.LAND) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Text("%2d · %2d%%".format(i + 1, EncounterData.LAND_SLOT_PERCENT[i]), Modifier.width(62.dp).padding(bottom = 9.dp), style = Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(if (i == 0) stringResource(Res.string.learnset_level) else "", d.landLevels[i], 1..100, { v -> editor.update { it.copy(landLevels = it.landLevels.set(i, v)) } }, Modifier.width(72.dp))
                SpeciesPicker(if (i == 0) stringResource(Res.string.encounter_morning) else "", d.landMorning[i], species, { v -> editor.update { it.copy(landMorning = it.landMorning.set(i, v)) } }, Modifier.weight(1f))
                SpeciesPicker(if (i == 0) stringResource(Res.string.encounter_day) else "", d.landDay[i], species, { v -> editor.update { it.copy(landDay = it.landDay.set(i, v)) } }, Modifier.weight(1f))
                SpeciesPicker(if (i == 0) stringResource(Res.string.encounter_night) else "", d.landNight[i], species, { v -> editor.update { it.copy(landNight = it.landNight.set(i, v)) } }, Modifier.weight(1f))
            }
        }
    }

    SlotCard(stringResource(Res.string.encounter_surfing), d.surf, species) { slots -> editor.update { it.copy(surf = slots) } }
    SlotCard(stringResource(Res.string.encounter_rock_smash), d.rockSmash, species) { slots -> editor.update { it.copy(rockSmash = slots) } }
    SlotCard(stringResource(Res.string.encounter_old_rod), d.oldRod, species) { slots -> editor.update { it.copy(oldRod = slots) } }
    SlotCard(stringResource(Res.string.encounter_good_rod), d.goodRod, species) { slots -> editor.update { it.copy(goodRod = slots) } }
    SlotCard(stringResource(Res.string.encounter_super_rod), d.superRod, species) { slots -> editor.update { it.copy(superRod = slots) } }

    SectionCard(stringResource(Res.string.encounter_swarms_and_special), subtitle = stringResource(Res.string.encounter_species_id_0_means_none)) {
        FieldFlow {
            SpeciesPicker(stringResource(Res.string.encounter_grass_swarm), d.swarmLand, species, { v -> editor.update { it.copy(swarmLand = v) } }, Modifier.width(200.dp))
            SpeciesPicker(stringResource(Res.string.encounter_surf_swarm), d.swarmSurf, species, { v -> editor.update { it.copy(swarmSurf = v) } }, Modifier.width(200.dp))
            SpeciesPicker(stringResource(Res.string.encounter_night_fishing), d.nightFish, species, { v -> editor.update { it.copy(nightFish = v) } }, Modifier.width(200.dp))
            SpeciesPicker(stringResource(Res.string.encounter_fishing_swarm), d.swarmFish, species, { v -> editor.update { it.copy(swarmFish = v) } }, Modifier.width(200.dp))
        }
    }
    SectionCard(stringResource(Res.string.encounter_radio), subtitle = stringResource(Res.string.encounter_pok_mon_cries_heard_when_tuning_th)) {
        FieldFlow {
            for (i in 0 until 2) SpeciesPicker(stringResource(Res.string.encounter_hoenn_x, i + 1), d.hoennSounds[i], species, { v -> editor.update { it.copy(hoennSounds = it.hoennSounds.set(i, v)) } }, Modifier.width(200.dp))
            for (i in 0 until 2) SpeciesPicker(stringResource(Res.string.encounter_sinnoh_x, i + 1), d.sinnohSounds[i], species, { v -> editor.update { it.copy(sinnohSounds = it.sinnohSounds.set(i, v)) } }, Modifier.width(200.dp))
        }
    }
}

@Composable
private fun SlotCard(title: String, slots: List<EncounterSlot>, species: List<String>, onChange: (List<EncounterSlot>) -> Unit) {
    SectionCard(title) {
        slots.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Text("${i + 1}", Modifier.width(22.dp).padding(bottom = 9.dp), style = Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(if (i == 0) stringResource(Res.string.encounter_min) else "", s.levelMin, 1..100, { v -> onChange(slots.set(i, s.copy(levelMin = v))) }, Modifier.width(72.dp))
                NumberField(if (i == 0) stringResource(Res.string.encounter_max) else "", s.levelMax, 1..100, { v -> onChange(slots.set(i, s.copy(levelMax = v))) }, Modifier.width(72.dp))
                SpeciesPicker(if (i == 0) stringResource(Res.string.evolution_species) else "", s.species, species, { v -> onChange(slots.set(i, s.copy(species = v))) }, Modifier.width(260.dp))
            }
        }
    }
}

private fun <T> List<T>.set(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

/**
 * Add and remove Pokémon. A group has a fixed number of slots with fixed chances, so a Pokémon is added by taking slots
 * from the ones that have most, and removed by handing its slots to the others; the slots below show the result.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RosterCard(model: EncounterEditorModel, d: EncounterData, species: List<String>) {
    var adding by remember { mutableStateOf<EncounterGroup?>(null) }
    var message by remember(model.editor.selectedIndex) { mutableStateOf<String?>(null) }
    SectionCard(stringResource(Res.string.encounter_pok_mon_in_this_area), subtitle = stringResource(Res.string.encounter_each_group_has_fixed_slots_with_fi)) {
        EncounterGroup.entries.forEach { group ->
            val roster = group.roster(d)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { adding = group }, modifier = Modifier.testTag("add:${group.name}")) { Text(stringResource(Res.string.encounter_add_pok_mon)) }
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    if (roster.isEmpty()) Text(stringResource(Res.string.encounter_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    roster.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Chip("${species.getOrNull(e.species) ?: e.species}  ${e.percent}%")
                            TextButton(
                                onClick = { message = if (model.removeSpecies(group, e.species)) null else tr(Res.string.encounter_cannot_remove_the_last_pok_mon_of, group.label.lowercase()) },
                                enabled = roster.size > 1,
                                modifier = Modifier.testTag("remove:${group.name}:${e.species}"),
                            ) { Text(stringResource(Res.string.learnset_remove)) }
                        }
                    }
                }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
    adding?.let { group ->
        AddDialog(group, species, onAdd = { id, percent ->
            val ok = model.addSpecies(group, id, percent)
            message = if (ok) null else tr(Res.string.encounter_could_not_add_x_to_x_already_there, species.getOrNull(id) ?: id, group.label.lowercase())
            adding = null
        }, onDismiss = { adding = null })
    }
}

@Composable
private fun AddDialog(group: EncounterGroup, species: List<String>, onAdd: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableStateOf(0) }
    var percent by remember { mutableStateOf(10) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.encounter_add_a_pok_mon_to_x, group.label.lowercase())) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeciesPicker(stringResource(Res.string.evolution_species), chosen, species, { chosen = it }, Modifier.fillMaxWidth())
                NumberField(stringResource(Res.string.encounter_chance_percent), percent, 1..99, { percent = it }, Modifier.width(140.dp), tag = "add-percent")
                Text("Slots come from the Pokémon with the biggest chance, in steps of the slot chances (${group.percents.joinToString(", ")}%), so the result can be a little off.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onAdd(chosen, percent) }, enabled = chosen != 0, modifier = Modifier.testTag("confirm")) { Text(stringResource(Res.string.encounter_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
