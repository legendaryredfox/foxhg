package io.github.romulofer.foxhg.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.testTag
import io.github.romulofer.foxhg.app.ui.ConfirmDialog
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
import io.github.romulofer.foxhg.app.ui.SpeciesPicker
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.Fox
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.core.data.encounter.EncounterData
import io.github.romulofer.foxhg.core.data.encounter.EncounterSlot

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
        "Compare with ${model.otherName}",
        subtitle = if (differing > 0) "${plural(differing, "area")} ${if (differing == 1) "has" else "have"} different wild Pokémon. Areas marked \"differs\" in the list are the ones." else "Every area matches ${model.otherName}.",
        actions = {
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            OutlinedButton(onClick = { asking = true }, enabled = differing > 0, modifier = Modifier.testTag("copy-all-open")) { Text("Make every area match ${model.otherName}…") }
        },
    ) {}
    if (asking) ConfirmDialog(
        "Make every area match ${model.otherName}?",
        "${plural(differing, "area")} will take ${model.otherName}'s wild Pokémon, rates and levels. Your edits in those areas are replaced. Ctrl+Z undoes it.",
        "Make them match",
        onConfirm = {
            asking = false
            val n = model.copyAllFromOther()
            message = "Copied into ${plural(n, "area")}"
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
                    Text("${plural(changes.size, "difference")} from ${model.otherName}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = model::copySelectedFromOther) { Text("Use ${model.otherName}'s data here") }
                }
                changes.take(if (expanded) changes.size else 5).forEach { c ->
                    val isSpecies = listOf("species", "morning", "day", "night", "swarm", "sound", "fishing").any { c.what.contains(it) } && !c.what.contains("level")
                    val from = if (isSpecies) species.getOrNull(c.from) ?: c.from else c.from
                    val to = if (isSpecies) species.getOrNull(c.to) ?: c.to else c.to
                    Text("${friendlyChange(c.what)}:  $from  →  $to", style = Fox.mono.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (changes.size > 5) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Show fewer" else "Show all ${changes.size}") }
            }
        }
    }

    SectionCard("Encounter rates", subtitle = "Chance that a step or cast starts an encounter") {
        FieldFlow {
            NumberField("Walking", d.rateWalking, 0..255, { v -> editor.update { it.copy(rateWalking = v) } }, Modifier.width(92.dp))
            NumberField("Surfing", d.rateSurfing, 0..255, { v -> editor.update { it.copy(rateSurfing = v) } }, Modifier.width(92.dp))
            NumberField("Rock smash", d.rateRockSmash, 0..255, { v -> editor.update { it.copy(rateRockSmash = v) } }, Modifier.width(100.dp))
            NumberField("Old rod", d.rateOldRod, 0..255, { v -> editor.update { it.copy(rateOldRod = v) } }, Modifier.width(92.dp))
            NumberField("Good rod", d.rateGoodRod, 0..255, { v -> editor.update { it.copy(rateGoodRod = v) } }, Modifier.width(92.dp))
            NumberField("Super rod", d.rateSuperRod, 0..255, { v -> editor.update { it.copy(rateSuperRod = v) } }, Modifier.width(92.dp))
        }
    }

    SectionCard("Grass", subtitle = "Each slot has one level and a species for morning, day and night") {
        for (i in 0 until EncounterData.LAND) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Text("%2d · %2d%%".format(i + 1, EncounterData.LAND_SLOT_PERCENT[i]), Modifier.width(62.dp).padding(bottom = 9.dp), style = Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(if (i == 0) "Level" else "", d.landLevels[i], 1..100, { v -> editor.update { it.copy(landLevels = it.landLevels.set(i, v)) } }, Modifier.width(72.dp))
                SpeciesPicker(if (i == 0) "Morning" else "", d.landMorning[i], species, { v -> editor.update { it.copy(landMorning = it.landMorning.set(i, v)) } }, Modifier.weight(1f))
                SpeciesPicker(if (i == 0) "Day" else "", d.landDay[i], species, { v -> editor.update { it.copy(landDay = it.landDay.set(i, v)) } }, Modifier.weight(1f))
                SpeciesPicker(if (i == 0) "Night" else "", d.landNight[i], species, { v -> editor.update { it.copy(landNight = it.landNight.set(i, v)) } }, Modifier.weight(1f))
            }
        }
    }

    SlotCard("Surfing", d.surf, species) { slots -> editor.update { it.copy(surf = slots) } }
    SlotCard("Rock smash", d.rockSmash, species) { slots -> editor.update { it.copy(rockSmash = slots) } }
    SlotCard("Old rod", d.oldRod, species) { slots -> editor.update { it.copy(oldRod = slots) } }
    SlotCard("Good rod", d.goodRod, species) { slots -> editor.update { it.copy(goodRod = slots) } }
    SlotCard("Super rod", d.superRod, species) { slots -> editor.update { it.copy(superRod = slots) } }

    SectionCard("Swarms and special", subtitle = "Species id 0 means none") {
        FieldFlow {
            SpeciesPicker("Grass swarm", d.swarmLand, species, { v -> editor.update { it.copy(swarmLand = v) } }, Modifier.width(200.dp))
            SpeciesPicker("Surf swarm", d.swarmSurf, species, { v -> editor.update { it.copy(swarmSurf = v) } }, Modifier.width(200.dp))
            SpeciesPicker("Night fishing", d.nightFish, species, { v -> editor.update { it.copy(nightFish = v) } }, Modifier.width(200.dp))
            SpeciesPicker("Fishing swarm", d.swarmFish, species, { v -> editor.update { it.copy(swarmFish = v) } }, Modifier.width(200.dp))
        }
    }
    SectionCard("Radio", subtitle = "Pokémon cries heard when tuning the Pokégear radio") {
        FieldFlow {
            for (i in 0 until 2) SpeciesPicker("Hoenn ${i + 1}", d.hoennSounds[i], species, { v -> editor.update { it.copy(hoennSounds = it.hoennSounds.set(i, v)) } }, Modifier.width(200.dp))
            for (i in 0 until 2) SpeciesPicker("Sinnoh ${i + 1}", d.sinnohSounds[i], species, { v -> editor.update { it.copy(sinnohSounds = it.sinnohSounds.set(i, v)) } }, Modifier.width(200.dp))
        }
    }
}

@Composable
private fun SlotCard(title: String, slots: List<EncounterSlot>, species: List<String>, onChange: (List<EncounterSlot>) -> Unit) {
    SectionCard(title) {
        slots.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                Text("${i + 1}", Modifier.width(22.dp).padding(bottom = 9.dp), style = Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberField(if (i == 0) "Min" else "", s.levelMin, 1..100, { v -> onChange(slots.set(i, s.copy(levelMin = v))) }, Modifier.width(72.dp))
                NumberField(if (i == 0) "Max" else "", s.levelMax, 1..100, { v -> onChange(slots.set(i, s.copy(levelMax = v))) }, Modifier.width(72.dp))
                SpeciesPicker(if (i == 0) "Species" else "", s.species, species, { v -> onChange(slots.set(i, s.copy(species = v))) }, Modifier.width(260.dp))
            }
        }
    }
}

private fun <T> List<T>.set(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
