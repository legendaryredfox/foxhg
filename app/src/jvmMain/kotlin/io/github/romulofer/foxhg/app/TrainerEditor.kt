package io.github.romulofer.foxhg.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import io.github.romulofer.foxhg.app.ui.PokemonFront
import io.github.romulofer.foxhg.app.ui.SpeciesPicker
import io.github.romulofer.foxhg.app.ui.ItemPicker
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.TrainerClassIcon
import io.github.romulofer.foxhg.app.ui.TrainerClassPicker
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.app.ui.ToggleChip
import io.github.romulofer.foxhg.core.data.trainer.Trainer
import io.github.romulofer.foxhg.core.data.trainer.TrainerMon

@Composable
fun TrainerEditor(model: TrainerEditorModel, modifier: Modifier = Modifier) {
    model.loadError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ReplaceSpeciesCard(model)
        Row(Modifier.fillMaxSize()) {
            ListPane(
                rows = model.rows(),
                selected = model.selectedIndex,
                search = model.search,
                onSearch = { model.search = it },
                placeholder = "Name, class or Pokémon…",
                label = model::label,
                onSelect = model::select,
                isEdited = model::isEdited,
                width = 300.dp,
                detail = { model.className(it) },
                leading = { TrainerClassIcon(model.classOf(it), 26.dp) },
            )
            model.selected?.let { t -> DetailPane { Detail(model, t) } }
        }
    }
}

@Composable
private fun ReplaceSpeciesCard(model: TrainerEditorModel) {
    val species = model.names?.species ?: emptyList()
    var from by remember { mutableStateOf(16) }
    var to by remember { mutableStateOf(19) }
    var message by remember { mutableStateOf<String?>(null) }
    SectionCard("Replace a Pokémon in every trainer party", subtitle = "Keeps level and everything else; changes the species only") {
        FieldFlow {
            SpeciesPicker("Replace", from, species, { from = it }, Modifier.width(200.dp))
            SpeciesPicker("With", to, species, { to = it }, Modifier.width(200.dp))
            Button(onClick = {
                val n = model.replaceSpeciesEverywhere(from, to)
                message = if (n == 0) "No trainer uses ${species.getOrNull(from)}" else "Replaced $n Pokémon"
            }, enabled = from != to) { Text("Replace") }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable
private fun Detail(model: TrainerEditorModel, trainer: Trainer) {
    val names = model.names
    val h = trainer.header
    EditorHeader(model.selectedIndex.toString(), model.name(model.selectedIndex), trailing = {
        Chip(model.className(model.selectedIndex) ?: "")
        TrainerClassIcon(h.trainerClass, 64.dp)
    })

    SectionCard("Trainer") {
        FieldFlow {
            TrainerClassPicker("Class", h.trainerClass, names?.trainerClasses ?: emptyList(), { v -> model.update { it.withHeader(it.header.copy(trainerClass = v)) } }, Modifier.width(220.dp))
            NumberField("AI flags", h.aiFlags.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 0..Int.MAX_VALUE, { v -> model.update { it.withHeader(it.header.copy(aiFlags = v.toLong())) } }, Modifier.width(130.dp))
            NumberField("Double battle", h.doubleBattle.coerceAtMost(255).toInt(), 0..255, { v -> model.update { it.withHeader(it.header.copy(doubleBattle = v.toLong())) } }, Modifier.width(120.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Party format", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToggleChip("Custom moves", h.hasCustomMoves, { v -> model.update { it.withHeader(it.header.withFlags(v, it.header.hasHeldItems)) } })
            ToggleChip("Held items", h.hasHeldItems, { v -> model.update { it.withHeader(it.header.withFlags(it.header.hasCustomMoves, v)) } })
        }
    }

    SectionCard("Items used in battle") {
        FieldFlow {
            for (slot in 0 until 4) {
                ItemPicker("Item ${slot + 1}", h.items.getOrElse(slot) { 0 }, names?.items ?: emptyList(), { v ->
                    model.update { it.withHeader(it.header.copy(items = it.header.items.toMutableList().also { l -> l[slot] = v })) }
                }, Modifier.width(200.dp))
            }
        }
    }

    SectionCard(
        "Party",
        subtitle = "${trainer.party.size} of ${Trainer.MAX_PARTY}",
        actions = { Button(onClick = model::addMon, enabled = trainer.party.size < Trainer.MAX_PARTY) { Text("Add Pokémon") } },
    ) {
        trainer.party.forEachIndexed { slot, mon -> MonCard(model, trainer, slot, mon) }
    }
}

@Composable
private fun MonCard(model: TrainerEditorModel, trainer: Trainer, slot: Int, mon: TrainerMon) {
    val names = model.names
    val h = trainer.header
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("${slot + 1}", emphasis = true)
                PokemonFront(mon.species)
                Text(names?.species?.getOrNull(mon.species) ?: "#${mon.species}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.removeMon(slot) }) { Text("Remove") }
            }
            FieldFlow {
                SpeciesPicker("Species", mon.species, names?.species ?: emptyList(), { v -> model.updateMon(slot) { it.withSpecies(v) } }, Modifier.width(200.dp))
                NumberField("Level", mon.level, 1..100, { v -> model.updateMon(slot) { it.copy(level = v) } }, Modifier.width(80.dp))
                NumberField("Form", mon.form, 0..63, { v -> model.updateMon(slot) { it.copy(speciesAndForm = (v shl 10) or it.species) } }, Modifier.width(80.dp))
                NumberField("IV scale", mon.difficulty, 0..255, { v -> model.updateMon(slot) { it.copy(difficulty = v) } }, Modifier.width(90.dp))
                NumberField("Gender / ability", mon.genderAbility, 0..255, { v -> model.updateMon(slot) { it.copy(genderAbility = v) } }, Modifier.width(130.dp))
                if (h.hasHeldItems) {
                    ItemPicker("Held item", mon.item, names?.items ?: emptyList(), { v -> model.updateMon(slot) { it.copy(item = v) } }, Modifier.width(200.dp))
                }
            }
            if (h.hasCustomMoves) {
                FieldFlow {
                    for (m in 0 until 4) {
                        PickerField("Move ${m + 1}", mon.moves.getOrElse(m) { 0 }, names?.moves ?: emptyList(), { v ->
                            model.updateMon(slot) { it.copy(moves = it.moves.toMutableList().also { l -> l[m] = v }) }
                        }, Modifier.width(190.dp))
                    }
                }
            }
        }
    }
}
