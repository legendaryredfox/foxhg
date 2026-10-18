package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.testTag
import io.github.legendaryredfox.foxhg.app.ui.ConfirmDialog
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
import io.github.legendaryredfox.foxhg.app.ui.PokemonFront
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.TrainerClassIcon
import io.github.legendaryredfox.foxhg.app.ui.TrainerClassPicker
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.data.trainer.Trainer
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerMon

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
                placeholder = stringResource(Res.string.trainer_name_class_or_pok_mon),
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
    var open by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val fromName = species.getOrNull(from) ?: "#$from"
    val toName = species.getOrNull(to) ?: "#$to"
    SectionCard(
        stringResource(Res.string.trainer_replace_a_pok_mon_in_every_trainer),
        subtitle = stringResource(Res.string.trainer_keeps_level_and_everything_else_ch),
        actions = { OutlinedButton(onClick = { open = !open }, modifier = Modifier.testTag("replace-toggle")) { Text(if (open) stringResource(Res.string.trainer_hide) else stringResource(Res.string.trainer_show)) } },
    ) {
        if (open) FieldFlow {
            SpeciesPicker(stringResource(Res.string.trainer_replace), from, species, { from = it }, Modifier.width(200.dp))
            SpeciesPicker(stringResource(Res.string.trainer_with), to, species, { to = it }, Modifier.width(200.dp))
            OutlinedButton(onClick = { asking = true }, enabled = from != to, modifier = Modifier.testTag("replace-open")) { Text(stringResource(Res.string.trainer_replace_2)) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
    }
    if (asking) {
        val n = model.countSpecies(from)
        ConfirmDialog(
            tr(Res.string.trainer_replace_x_with_x, fromName, toName),
            if (n == 0) tr(Res.string.trainer_no_trainer_uses_x_so_nothing_would, fromName) else "${plural(n, "Pokémon")} in trainer parties will become $toName. Levels and moves stay. Ctrl+Z undoes it.",
            tr(Res.string.trainer_replace),
            onConfirm = {
                asking = false
                val changed = model.replaceSpeciesEverywhere(from, to)
                message = if (changed == 0) tr(Res.string.trainer_no_trainer_uses_x, fromName) else tr(Res.string.trainer_replaced_x_pok_mon, changed)
            },
            onDismiss = { asking = false },
        )
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

    SectionCard(stringResource(Res.string.trainer_trainer)) {
        FieldFlow {
            TrainerClassPicker(stringResource(Res.string.trainer_class), h.trainerClass, names?.trainerClasses ?: emptyList(), { v -> model.update { it.withHeader(it.header.copy(trainerClass = v)) } }, Modifier.width(220.dp))
            NumberField(stringResource(Res.string.trainer_ai_flags), h.aiFlags.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 0..Int.MAX_VALUE, { v -> model.update { it.withHeader(it.header.copy(aiFlags = v.toLong())) } }, Modifier.width(130.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.move_battle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The game stores 2 for a double battle and 0 for a single one.
            ToggleChip(stringResource(Res.string.trainer_double_battle), h.doubleBattle != 0L, { on -> model.update { it.withHeader(it.header.copy(doubleBattle = if (on) 2L else 0L)) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.trainer_party_format), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToggleChip(stringResource(Res.string.trainer_custom_moves), h.hasCustomMoves, { v -> model.update { it.withHeader(it.header.withFlags(v, it.header.hasHeldItems)) } })
            ToggleChip(stringResource(Res.string.trainer_held_items), h.hasHeldItems, { v -> model.update { it.withHeader(it.header.withFlags(it.header.hasCustomMoves, v)) } })
        }
    }

    SectionCard(stringResource(Res.string.trainer_items_used_in_battle)) {
        FieldFlow {
            for (slot in 0 until 4) {
                ItemPicker(stringResource(Res.string.mart_item_x, slot + 1), h.items.getOrElse(slot) { 0 }, names?.items ?: emptyList(), { v ->
                    model.update { it.withHeader(it.header.copy(items = it.header.items.toMutableList().also { l -> l[slot] = v })) }
                }, Modifier.width(200.dp))
            }
        }
    }

    SectionCard(
        stringResource(Res.string.editor_save_party),
        subtitle = stringResource(Res.string.trainer_x_of_x, trainer.party.size, Trainer.MAX_PARTY),
        actions = { Button(onClick = model::addMon, enabled = trainer.party.size < Trainer.MAX_PARTY) { Text(stringResource(Res.string.trainer_add_pok_mon)) } },
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
                TextButton(onClick = { model.removeMon(slot) }) { Text(stringResource(Res.string.learnset_remove)) }
            }
            FieldFlow {
                SpeciesPicker(stringResource(Res.string.evolution_species), mon.species, names?.species ?: emptyList(), { v -> model.updateMon(slot) { it.withSpecies(v) } }, Modifier.width(200.dp))
                NumberField(stringResource(Res.string.learnset_level), mon.level, 1..100, { v -> model.updateMon(slot) { it.copy(level = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.trainer_form), mon.form, 0..63, { v -> model.updateMon(slot) { it.copy(speciesAndForm = (v shl 10) or it.species) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.trainer_iv_scale), mon.difficulty, 0..255, { v -> model.updateMon(slot) { it.copy(difficulty = v) } }, Modifier.width(90.dp))
                NumberField(stringResource(Res.string.trainer_gender_ability), mon.genderAbility, 0..255, { v -> model.updateMon(slot) { it.copy(genderAbility = v) } }, Modifier.width(130.dp))
                if (h.hasHeldItems) {
                    ItemPicker(stringResource(Res.string.starter_held_item), mon.item, names?.items ?: emptyList(), { v -> model.updateMon(slot) { it.copy(item = v) } }, Modifier.width(200.dp))
                }
            }
            if (h.hasCustomMoves) {
                FieldFlow {
                    for (m in 0 until 4) {
                        PickerField(stringResource(Res.string.trainer_move_x, m + 1), mon.moves.getOrElse(m) { 0 }, names?.moves ?: emptyList(), { v ->
                            model.updateMon(slot) { it.copy(moves = it.moves.toMutableList().also { l -> l[m] = v }) }
                        }, Modifier.width(190.dp))
                    }
                }
            }
        }
    }
}
