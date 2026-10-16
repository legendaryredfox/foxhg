package io.github.legendaryredfox.foxhg.app

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
        val path = pickFile("Export $fileName", FileDialog.SAVE, ".csv") ?: return@OutlinedButton
        runCatching {
            Files.writeString(path, csv.export(records(), label))
        }.onSuccess { report("Exported to ${path.fileName}", false) }.onFailure { report("Export failed: ${it.message}", true) }
    }) { Text("Export CSV") }
    OutlinedButton(onClick = {
        val path = pickFile("Import $fileName", FileDialog.LOAD, ".csv") ?: return@OutlinedButton
        val text = runCatching { Files.readString(path) }.getOrElse { return@OutlinedButton report("Import failed: ${it.message}", true) }
        when (val result = csv.import(text, records())) {
            is FoxResult.Ok -> { replaceAll { result.value }; report("Imported ${path.fileName}", false) }
            is FoxResult.Err -> report("Import rejected: ${result.message}", true)
        }
    }) { Text("Import CSV") }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Export / Import for a CSV that is not one row per record: [export] writes the text, [import] stores a file's text and returns an error message or null. */
@Composable
fun CsvFileButtons(fileName: String, export: () -> String, import: (String) -> String?) {
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    OutlinedButton(onClick = {
        val path = pickFile("Export $fileName", FileDialog.SAVE, ".csv") ?: return@OutlinedButton
        runCatching { Files.writeString(path, export()) }
            .onSuccess { failed = false; message = "Exported to ${path.fileName}" }
            .onFailure { failed = true; message = "Export failed: ${it.message}" }
    }) { Text("Export CSV") }
    OutlinedButton(onClick = {
        val path = pickFile("Import $fileName", FileDialog.LOAD, ".csv") ?: return@OutlinedButton
        val text = runCatching { Files.readString(path) }.getOrElse { failed = true; message = "Import failed: ${it.message}"; return@OutlinedButton }
        val problem = import(text)
        failed = problem != null
        message = problem?.let { "Import rejected: $it" } ?: "Imported ${path.fileName}"
    }) { Text("Import CSV") }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
}
