package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.PokemonIcon
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.app.ui.TrainerClassPicker
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerSet
import io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerTrainer
import io.github.legendaryredfox.foxhg.core.game.GameNames

/** Row label of a Battle Tower trainer: its number, name and class. */
fun battleTowerTrainerLabel(names: GameNames?, classOf: (Int) -> Int): (Int) -> String = { i ->
    "%03d  %s".format(i, names?.battleTowerNames?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: "Trainer $i") +
        (names?.trainerClasses?.getOrNull(classOf(i))?.let { "  ($it)" } ?: "")
}

@Composable
fun BattleTowerTrainerEditor(model: RecordEditorModel<BattleTowerTrainer>, sets: List<BattleTowerSet>, modifier: Modifier = Modifier) {
    val names = model.names
    RecordListDetail(model, modifier) { t ->
        val index = model.selectedIndex
        EditorHeader(index.toString(), names?.battleTowerNames?.getOrNull(index) ?: "Trainer $index")
        SectionCard("Trainer") {
            FieldFlow {
                TrainerClassPicker("Class", t.trainerClass, names?.trainerClasses ?: emptyList(), { v -> model.update { it.copy(trainerClass = v) } }, Modifier.width(240.dp))
            }
        }
        SectionCard("Messages", subtitle = "Name and messages are in message banks 27 and 724 (messages 3n to 3n + 2); edit them in the text editor") {
            for (m in 0 until 3) {
                Text(names?.battleTowerMessages?.getOrNull(index * 3 + m) ?: "", style = MaterialTheme.typography.bodySmall)
            }
        }
        SectionCard("Pokémon sets", subtitle = "${t.setIds.size} sets this trainer picks its team from, a random few each battle. Sets are edited in the Battle Tower sets editor") {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                t.setIds.forEachIndexed { i, id ->
                    OutlinedButton(onClick = { model.update { it.removeSet(i) } }, enabled = t.setIds.size > 1, modifier = Modifier.testTag("bt-remove:$i")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            sets.getOrNull(id)?.let { PokemonIcon(it.species, 20.dp) }
                            Text("%03d  %s  ✕".format(id, sets.getOrNull(id)?.let { names?.species?.getOrNull(it.species) } ?: "?"))
                        }
                    }
                }
            }
            var next by remember(index) { mutableIntStateOf(minOf(1, maxOf(sets.size - 1, 0))) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                NumberField("Set to add", next, 0..maxOf(sets.size - 1, 0), { next = it }, Modifier.width(120.dp), tag = "Set to add")
                OutlinedButton(onClick = { model.update { it.addSet(next) } }, enabled = next in sets.indices, modifier = Modifier.testTag("bt-add")) {
                    Text("Add ${sets.getOrNull(next)?.let { names?.species?.getOrNull(it.species) } ?: ""}".trim())
                }
            }
        }
    }
}

@Composable
fun BattleTowerSetEditor(model: RecordEditorModel<BattleTowerSet>, modifier: Modifier = Modifier) {
    val names = model.names
    val species = names?.species ?: emptyList()
    val moves = names?.moves ?: emptyList()
    RecordListDetail(model, modifier, leading = { i -> model.records.getOrNull(i)?.let { PokemonIcon(it.species) } }) { s ->
        EditorHeader(model.selectedIndex.toString(), species.getOrNull(s.species) ?: "Set ${model.selectedIndex}")
        SectionCard("Pokémon") {
            FieldFlow {
                SpeciesPicker("Species", s.species, species, { v -> model.update { it.copy(species = v) } }, Modifier.width(240.dp))
                NumberField("Form", s.form, 0..255, { v -> model.update { it.copy(form = v) } }, Modifier.width(80.dp))
                PickerField("Nature", s.nature.coerceIn(0, BattleTowerSet.NATURES.lastIndex), BattleTowerSet.NATURES, { v -> model.update { it.copy(nature = v) } }, Modifier.width(160.dp))
                ItemPicker("Held item", s.item, names?.items ?: emptyList(), { v -> model.update { it.copy(item = v) } }, Modifier.width(240.dp))
            }
        }
        SectionCard("Moves") {
            FieldFlow {
                for (slot in 0 until 4) PickerField("Move ${slot + 1}", s.moves[slot], moves, { v -> model.update { it.withMove(slot, v) } }, Modifier.width(220.dp))
            }
        }
        SectionCard("Maximum EVs", subtitle = "The stats that get 255 EVs") {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BattleTowerSet.EV_STATS.forEachIndexed { stat, name -> ToggleChip(name, s.hasEv(stat), { on -> model.update { it.withEv(stat, on) } }) }
            }
        }
    }
}
