package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.learnset.LearnsetBulk
import io.github.legendaryredfox.foxhg.core.data.learnset.LearnsetChange
import java.awt.FileDialog
import java.nio.file.Files
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
            stringResource(Res.string.learnset_level_up_moves),
            subtitle = if (learnset.overLimit) stringResource(Res.string.learnset_over_the_limit_the_game_reads_only, Learnset.MAX_ENTRIES) else stringResource(Res.string.learnset_learned_in_this_order_as_the_level),
            actions = {
                Chip("${learnset.entries.size} / ${Learnset.MAX_ENTRIES}", emphasis = learnset.overLimit)
                OutlinedButton(onClick = { model.update { it.sorted() } }) { Text(stringResource(Res.string.learnset_sort_by_level)) }
            },
        ) {
            if (learnset.entries.isEmpty()) {
                Text(stringResource(Res.string.learnset_no_level_up_moves), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            learnset.entries.forEachIndexed { i, entry ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    NumberField(stringResource(Res.string.learnset_level), entry.level, 1..100, { v -> model.update { l -> l.replaceAt(i, entry.copy(level = v)) } }, Modifier.width(80.dp))
                    PickerField(stringResource(Res.string.learnset_move), entry.move, moves, { v -> model.update { l -> l.replaceAt(i, entry.copy(move = v)) } }, Modifier.width(260.dp))
                    TextButton(onClick = { model.update { l -> l.copy(entries = l.entries.filterIndexed { k, _ -> k != i }) } }) { Text(stringResource(Res.string.learnset_remove)) }
                }
            }
            Button(onClick = {
                model.update { l -> l.copy(entries = l.entries + LearnsetEntry(move = 1, level = l.entries.lastOrNull()?.level ?: 1)) }
            }) { Text(stringResource(Res.string.learnset_add_move)) }
        }
        BulkTools(model)
    }
}

/** Changes to the species the list shows (search to narrow it): teach, forget or swap one move for all of them, and CSV for every learnset. */
@Composable
private fun BulkTools(model: RecordEditorModel<Learnset>) {
    val moves = model.names?.moves ?: emptyList()
    var move by remember { mutableIntStateOf(1) }
    var level by remember { mutableIntStateOf(1) }
    var replacement by remember { mutableIntStateOf(1) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val shown = model.rows()
    fun apply(verb: String, change: (List<Learnset>) -> LearnsetChange) {
        val result = change(model.records)
        if (result.changed > 0) model.updateAll { result.records }
        failed = false
        message = "$verb ${plural(result.changed, "species")}" + if (result.skipped > 0) tr(Res.string.learnset_left_x_alone_move_already_there_no, result.skipped) else ""
    }
    SectionCard(
        stringResource(Res.string.learnset_bulk_tools),
        subtitle = stringResource(Res.string.learnset_apply_to_the_x_shown_in_the_list_s, pluralStringResource(Res.plurals.plural_species, shown.size, shown.size), Learnset.MAX_ENTRIES),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            PickerField(stringResource(Res.string.learnset_bulk_move), move, moves, { move = it }, Modifier.width(240.dp))
            NumberField(stringResource(Res.string.learnset_level), level, 1..100, { level = it }, Modifier.width(80.dp), tag = "Bulk level")
            OutlinedButton(onClick = { apply("Taught it to") { LearnsetBulk.teach(it, shown, move, level) } }, modifier = Modifier.testTag("bulk-teach")) { Text(stringResource(Res.string.learnset_teach)) }
            OutlinedButton(onClick = { apply("Took it from") { LearnsetBulk.forget(it, shown, move) } }, modifier = Modifier.testTag("bulk-forget")) { Text(stringResource(Res.string.learnset_forget)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            PickerField(stringResource(Res.string.learnset_replace_with), replacement, moves, { replacement = it }, Modifier.width(240.dp))
            OutlinedButton(onClick = { apply("Replaced it in") { LearnsetBulk.replace(it, shown, move, replacement) } }, modifier = Modifier.testTag("bulk-replace")) { Text(stringResource(Res.string.learnset_replace_the_move_above)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                val path = pickFile(tr(Res.string.learnset_export_learnsets), FileDialog.SAVE, ".csv") ?: return@OutlinedButton
                runCatching {
                    Files.writeString(path, LearnsetBulk.export(model.records, { model.names?.species?.getOrNull(it) ?: "" }, { moves.getOrNull(it) ?: "" }))
                }.onSuccess { failed = false; message = tr(Res.string.csv_buttons_exported_to_x, path.fileName) }.onFailure { failed = true; message = tr(Res.string.csv_buttons_export_failed_x, it.message) }
            }) { Text(stringResource(Res.string.learnset_export_csv)) }
            OutlinedButton(onClick = {
                val path = pickFile(tr(Res.string.learnset_import_learnsets), FileDialog.LOAD, ".csv") ?: return@OutlinedButton
                val text = runCatching { Files.readString(path) }.getOrElse { failed = true; message = tr(Res.string.csv_buttons_import_failed_x, it.message); return@OutlinedButton }
                when (val result = LearnsetBulk.import(text, model.records)) {
                    is FoxResult.Ok -> { model.updateAll { result.value }; failed = false; message = tr(Res.string.learnset_imported_x_the_species_in_the_file, path.fileName) }
                    is FoxResult.Err -> { failed = true; message = tr(Res.string.csv_buttons_import_rejected_x, result.message) }
                }
            }) { Text(stringResource(Res.string.learnset_import_csv)) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun Learnset.replaceAt(index: Int, entry: LearnsetEntry): Learnset =
    copy(entries = entries.toMutableList().also { it[index] = entry })
