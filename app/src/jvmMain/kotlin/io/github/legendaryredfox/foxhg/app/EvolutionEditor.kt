package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
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
        stringResource(Res.string.evolution_remove_trade_evolutions),
        subtitle = if (remaining > 0) "${plural(remaining, "trade evolution")} left. Trade becomes a level-up; trade with an item becomes using that item." else "No trade evolutions left.",
    ) {
        FieldFlow {
            NumberField(stringResource(Res.string.evolution_trade_becomes_level), level, 2..100, { level = it }, Modifier.width(150.dp))
            Button(onClick = {
                var changed = 0
                model.updateAll { records ->
                    val (converted, changes) = TradeEvolutions.convert(records, level)
                    changed = changes.size
                    converted
                }
                message = "Converted ${plural(changed, "evolution")}"
            }, enabled = remaining > 0) { Text(stringResource(Res.string.evolution_convert_all)) }
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
    EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = {
        CsvButtons(model, EvolutionData.CSV, "evolutions")
        PokemonPortraits(model.selectedIndex)
    })
    SectionCard(stringResource(Res.string.editor_evolutions), subtitle = stringResource(Res.string.evolution_up_to_x_ways_to_evolve_leave_the_m, EvolutionData.SLOTS)) {
        data.slots.forEachIndexed { slot, entry ->
            val method = entry.known
            FieldFlow {
                PickerField(
                    stringResource(Res.string.evolution_method_x, slot + 1), method?.ordinal ?: 0, methods,
                    { v -> model.update { it.with(slot, entry.copy(method = EvolutionMethod.entries[v].id)) } },
                    Modifier.width(230.dp),
                )
                if (method == null) {
                    Text(stringResource(Res.string.evolution_unknown_method_x, entry.method), color = MaterialTheme.colorScheme.error)
                } else {
                    val setParam = { v: Int -> model.update { it.with(slot, entry.copy(param = v)) } }
                    when (method.param) {
                        ParamKind.NONE -> {}
                        ParamKind.LEVEL -> NumberField(stringResource(Res.string.learnset_level), entry.param, 1..100, setParam, Modifier.width(90.dp))
                        ParamKind.FRIENDSHIP -> NumberField(stringResource(Res.string.personal_friendship), entry.param, 0..255, setParam, Modifier.width(110.dp))
                        ParamKind.BEAUTY -> NumberField(stringResource(Res.string.evolution_beauty), entry.param, 0..255, setParam, Modifier.width(110.dp))
                        ParamKind.ITEM -> ItemPicker(stringResource(Res.string.evolution_item), entry.param, items, setParam, Modifier.width(220.dp))
                        ParamKind.MOVE -> PickerField(stringResource(Res.string.learnset_move), entry.param, moves, setParam, Modifier.width(220.dp))
                        ParamKind.SPECIES -> SpeciesPicker(stringResource(Res.string.evolution_species), entry.param, species, setParam, Modifier.width(220.dp))
                    }
                    if (method != EvolutionMethod.NONE) {
                        SpeciesPicker(stringResource(Res.string.evolution_evolves_into), entry.target, species, { v -> model.update { it.with(slot, entry.copy(target = v)) } }, Modifier.width(220.dp))
                    }
                }
            }
        }
    }
}
