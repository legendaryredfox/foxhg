package io.github.romulofer.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.PokemonPortraits
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.PokemonIcon
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.core.data.eggmove.EggMoves

/** The egg move list is one file for every species; [model] holds that single record, species is picked here. */
@Composable
fun EggMoveEditor(model: RecordEditorModel<EggMoves>, modifier: Modifier = Modifier) {
    model.loadError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    var species by remember { mutableIntStateOf(1) }
    var search by remember { mutableStateOf("") }
    val eggs = model.selected ?: return
    val label = RecordEditorModel.speciesLabel(model.names)
    val speciesCount = model.names?.species?.size ?: 0
    val rows = (1 until speciesCount).filter { search.isBlank() || label(it).contains(search.trim(), ignoreCase = true) }
    Row(modifier) {
        ListPane(
            rows = rows,
            selected = species,
            search = search,
            onSearch = { search = it },
            placeholder = "Search…",
            label = label,
            onSelect = { species = it },
            isEdited = { false },
            detail = { s -> eggs.movesOf(s).size.takeIf { it > 0 }?.toString() },
            leading = { PokemonIcon(it) },
        )
        DetailPane {
            val moves = model.names?.moves ?: emptyList()
            val list = eggs.movesOf(species)
            EditorHeader(species.toString(), nameOf(label(species)), trailing = { PokemonPortraits(species) })
            SectionCard(
                "Egg moves",
                subtitle = if (eggs.overLimit) "Over the limit: the game only finds species whose header is within the first ${EggMoves.SCAN_LIMIT} words" else "Only moves the parents can pass down appear here",
                actions = { Chip("${list.size} / ${EggMoves.MAX_PER_SPECIES}", emphasis = list.size > EggMoves.MAX_PER_SPECIES || eggs.overLimit) },
            ) {
                if (list.isEmpty()) Text("No egg moves.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.forEachIndexed { i, move ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        PickerField("Move", move, moves, { v -> model.update { e -> e.withMoves(species, list.toMutableList().also { it[i] = v }) } }, Modifier.width(260.dp))
                        TextButton(onClick = { model.update { e -> e.withMoves(species, list.filterIndexed { k, _ -> k != i }) } }) { Text("Remove") }
                    }
                }
                Button(onClick = { model.update { e -> e.withMoves(species, list + 1) } }) { Text("Add move") }
            }
        }
    }
}
