package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
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
import io.github.legendaryredfox.foxhg.app.ui.PokemonPortraits
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.PokemonIcon
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.eggmove.EggMoves

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
            placeholder = stringResource(Res.string.egg_move_search),
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
                stringResource(Res.string.editor_egg_moves),
                subtitle = if (eggs.overLimit) "Over the limit: the game only finds species whose header is within the first ${EggMoves.SCAN_LIMIT} words" else "Only moves the parents can pass down appear here",
                actions = {
                    Chip("${list.size} / ${EggMoves.MAX_PER_SPECIES}", emphasis = list.size > EggMoves.MAX_PER_SPECIES || eggs.overLimit)
                    CsvFileButtons(
                        "egg moves",
                        { EggMoves.toCsv(eggs, { model.names?.species?.getOrNull(it) ?: "" }, { moves.getOrNull(it) ?: "" }) },
                        { text ->
                            when (val r = EggMoves.fromCsv(text, eggs)) {
                                is FoxResult.Ok -> { model.update { r.value }; null }
                                is FoxResult.Err -> r.message
                            }
                        },
                    )
                },
            ) {
                if (list.isEmpty()) Text(stringResource(Res.string.egg_move_no_egg_moves), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                list.forEachIndexed { i, move ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                        PickerField(stringResource(Res.string.learnset_move), move, moves, { v -> model.update { e -> e.withMoves(species, list.toMutableList().also { it[i] = v }) } }, Modifier.width(260.dp))
                        TextButton(onClick = { model.update { e -> e.withMoves(species, list.filterIndexed { k, _ -> k != i }) } }) { Text(stringResource(Res.string.learnset_remove)) }
                    }
                }
                Button(onClick = { model.update { e -> e.withMoves(species, list + 1) } }) { Text(stringResource(Res.string.learnset_add_move)) }
            }
        }
    }
}
