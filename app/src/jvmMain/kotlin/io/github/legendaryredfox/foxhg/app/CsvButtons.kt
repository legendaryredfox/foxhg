package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.csv.RecordCsv
import java.awt.FileDialog
import java.nio.file.Files

/** Export / Import buttons for a table of records, with the outcome shown beside them. Import is all or nothing. */
@Composable
fun <T> CsvButtons(model: RecordEditorModel<T>, csv: RecordCsv<T>, fileName: String) =
    CsvButtons(csv, fileName, { model.records }, { nameOf(model.label(it)) }, model::updateAll)

/** The buttons for an editor that keeps its records its own way: [records] now, [label] of a row, and [replaceAll] to store an import. */
@Composable
fun <T> CsvButtons(csv: RecordCsv<T>, fileName: String, records: () -> List<T>, label: (Int) -> String, replaceAll: ((List<T>) -> List<T>) -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    fun report(text: String, error: Boolean) { message = text; failed = error }
    OutlinedButton(onClick = {
        val path = pickFile(tr(Res.string.csv_buttons_export_x, fileName), FileDialog.SAVE, ".csv") ?: return@OutlinedButton
        runCatching {
            Files.writeString(path, csv.export(records(), label))
        }.onSuccess { report(tr(Res.string.csv_buttons_exported_to_x, path.fileName), false) }.onFailure { report(tr(Res.string.csv_buttons_export_failed_x, it.message), true) }
    }) { Text(stringResource(Res.string.learnset_export_csv)) }
    OutlinedButton(onClick = {
        val path = pickFile(tr(Res.string.csv_buttons_import_x, fileName), FileDialog.LOAD, ".csv") ?: return@OutlinedButton
        val text = runCatching { Files.readString(path) }.getOrElse { return@OutlinedButton report(tr(Res.string.csv_buttons_import_failed_x, it.message), true) }
        when (val result = csv.import(text, records())) {
            is FoxResult.Ok -> { replaceAll { result.value }; report(tr(Res.string.csv_buttons_imported_x, path.fileName), false) }
            is FoxResult.Err -> report(tr(Res.string.csv_buttons_import_rejected_x, result.message), true)
        }
    }) { Text(stringResource(Res.string.learnset_import_csv)) }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Export / Import for a CSV that is not one row per record: [export] writes the text, [import] stores a file's text and returns an error message or null. */
@Composable
fun CsvFileButtons(fileName: String, export: () -> String, import: (String) -> String?) {
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        val path = pickFile(tr(Res.string.csv_buttons_export_x, fileName), FileDialog.SAVE, ".csv") ?: return@OutlinedButton
        runCatching { Files.writeString(path, export()) }
            .onSuccess { failed = false; message = tr(Res.string.csv_buttons_exported_to_x, path.fileName) }
            .onFailure { failed = true; message = tr(Res.string.csv_buttons_export_failed_x, it.message) }
    }) { Text(stringResource(Res.string.learnset_export_csv)) }
    OutlinedButton(onClick = {
        val path = pickFile(tr(Res.string.csv_buttons_import_x, fileName), FileDialog.LOAD, ".csv") ?: return@OutlinedButton
        val text = runCatching { Files.readString(path) }.getOrElse { failed = true; message = tr(Res.string.csv_buttons_import_failed_x, it.message); return@OutlinedButton }
        val problem = import(text)
        failed = problem != null
        message = problem?.let { tr(Res.string.csv_buttons_import_rejected_x, it) } ?: tr(Res.string.csv_buttons_imported_x, path.fileName)
    }) { Text(stringResource(Res.string.learnset_import_csv)) }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** An Export JSON button for a file that is only written out, never read back: [export] gives the text, the outcome shows beside the button. */
@Composable
fun JsonExportButton(fileName: String, export: () -> String) {
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        val path = pickFile(tr(Res.string.csv_buttons_export_x, fileName), FileDialog.SAVE, ".json") ?: return@OutlinedButton
        runCatching { Files.writeString(path, export()) }
            .onSuccess { failed = false; message = tr(Res.string.csv_buttons_exported_to_x, path.fileName) }
            .onFailure { failed = true; message = tr(Res.string.csv_buttons_export_failed_x, it.message) }
    }) { Text(stringResource(Res.string.encounter_export_json)) }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
}
