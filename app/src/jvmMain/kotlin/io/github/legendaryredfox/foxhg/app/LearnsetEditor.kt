package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.PokemonPortraits
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.PokemonIcon
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.data.learnset.Learnset
import io.github.legendaryredfox.foxhg.core.data.learnset.LearnsetEntry

@Composable
fun LearnsetEditor(model: RecordEditorModel<Learnset>, modifier: Modifier = Modifier) {
    RecordListDetail(model, modifier, leading = { PokemonIcon(it) }) { learnset ->
        val moves = model.names?.moves ?: emptyList()
        EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = { PokemonPortraits(model.selectedIndex) })
        SectionCard(
            "Level-up moves",
            subtitle = if (learnset.overLimit) "Over the limit: the game reads only the first ${Learnset.MAX_ENTRIES}" else "Learned in this order as the level is reached",
            actions = {
                Chip("${learnset.entries.size} / ${Learnset.MAX_ENTRIES}", emphasis = learnset.overLimit)
                OutlinedButton(onClick = { model.update { it.sorted() } }) { Text("Sort by level") }
            },
        ) {
            if (learnset.entries.isEmpty()) {
                Text("No level-up moves.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            learnset.entries.forEachIndexed { i, entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    NumberField("Level", entry.level, 1..100, { v -> model.update { l -> l.replaceAt(i, entry.copy(level = v)) } }, Modifier.width(80.dp))
                    PickerField("Move", entry.move, moves, { v -> model.update { l -> l.replaceAt(i, entry.copy(move = v)) } }, Modifier.width(260.dp))
                    TextButton(onClick = { model.update { l -> l.copy(entries = l.entries.filterIndexed { k, _ -> k != i }) } }) { Text("Remove") }
                }
            }
            Button(onClick = {
                model.update { l -> l.copy(entries = l.entries + LearnsetEntry(move = 1, level = l.entries.lastOrNull()?.level ?: 1)) }
            }) { Text("Add move") }
        }
    }
}

private fun Learnset.replaceAt(index: Int, entry: LearnsetEntry): Learnset =
    copy(entries = entries.toMutableList().also { it[index] = entry })
