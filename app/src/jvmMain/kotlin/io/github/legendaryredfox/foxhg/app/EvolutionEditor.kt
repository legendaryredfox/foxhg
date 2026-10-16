package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.PokemonPortraits
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.PokemonIcon
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionData
import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionMethod
import io.github.legendaryredfox.foxhg.core.data.evolution.ParamKind
import io.github.legendaryredfox.foxhg.core.data.evolution.TradeEvolutions

@Composable
fun EvolutionEditor(model: RecordEditorModel<EvolutionData>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TradeConversionCard(model)
        RecordListDetail(model, Modifier.fillMaxSize(), leading = { PokemonIcon(it) }) { data -> Detail(model, data) }
    }
}

@Composable
private fun TradeConversionCard(model: RecordEditorModel<EvolutionData>) {
    var level by remember { mutableStateOf(36) }
    var message by remember { mutableStateOf<String?>(null) }
    val remaining = model.records.sumOf { r -> r.slots.count { it.known?.isTrade == true } }
    SectionCard(
        "Remove trade evolutions",
        subtitle = if (remaining > 0) "${plural(remaining, "trade evolution")} left. Trade becomes a level-up; trade with an item becomes using that item." else "No trade evolutions left.",
    ) {
        FieldFlow {
            NumberField("Trade becomes level", level, 2..100, { level = it }, Modifier.width(150.dp))
            Button(onClick = {
                var changed = 0
                model.updateAll { records ->
                    val (converted, changes) = TradeEvolutions.convert(records, level)
                    changed = changes.size
                    converted
                }
                message = "Converted ${plural(changed, "evolution")}"
            }, enabled = remaining > 0) { Text("Convert all") }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable
private fun Detail(model: RecordEditorModel<EvolutionData>, data: EvolutionData) {
    val names = model.names
    val species = names?.species ?: emptyList()
    val items = names?.items ?: emptyList()
    val moves = names?.moves ?: emptyList()
    val methods = EvolutionMethod.entries.map { it.label }
    EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = { PokemonPortraits(model.selectedIndex) })
    SectionCard("Evolutions", subtitle = "Up to ${EvolutionData.SLOTS} ways to evolve. Leave the method on None to unuse a slot.") {
        data.slots.forEachIndexed { slot, entry ->
            val method = entry.known
            FieldFlow {
                PickerField(
                    "Method ${slot + 1}", method?.ordinal ?: 0, methods,
                    { v -> model.update { it.with(slot, entry.copy(method = EvolutionMethod.entries[v].id)) } },
                    Modifier.width(230.dp),
                )
                if (method == null) {
                    Text("Unknown method ${entry.method}", color = MaterialTheme.colorScheme.error)
                } else {
                    val setParam = { v: Int -> model.update { it.with(slot, entry.copy(param = v)) } }
                    when (method.param) {
                        ParamKind.NONE -> {}
                        ParamKind.LEVEL -> NumberField("Level", entry.param, 1..100, setParam, Modifier.width(90.dp))
                        ParamKind.FRIENDSHIP -> NumberField("Friendship", entry.param, 0..255, setParam, Modifier.width(110.dp))
                        ParamKind.BEAUTY -> NumberField("Beauty", entry.param, 0..255, setParam, Modifier.width(110.dp))
                        ParamKind.ITEM -> ItemPicker("Item", entry.param, items, setParam, Modifier.width(220.dp))
                        ParamKind.MOVE -> PickerField("Move", entry.param, moves, setParam, Modifier.width(220.dp))
                        ParamKind.SPECIES -> SpeciesPicker("Species", entry.param, species, setParam, Modifier.width(220.dp))
                    }
                    if (method != EvolutionMethod.NONE) {
                        SpeciesPicker("Evolves into", entry.target, species, { v -> model.update { it.with(slot, entry.copy(target = v)) } }, Modifier.width(220.dp))
                    }
                }
            }
        }
    }
}
