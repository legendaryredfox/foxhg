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
fun <T> CsvButtons(model: RecordEditorModel<T>, csv: RecordCsv<T>, fileName: String) {
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    fun report(text: String, error: Boolean) { message = text; failed = error }
    OutlinedButton(onClick = {
        val path = pickFile("Export $fileName", FileDialog.SAVE, ".csv") ?: return@OutlinedButton
        runCatching {
            Files.writeString(path, csv.export(model.records) { nameOf(model.label(it)) })
        }.onSuccess { report("Exported to ${path.fileName}", false) }.onFailure { report("Export failed: ${it.message}", true) }
    }) { Text("Export CSV") }
    OutlinedButton(onClick = {
        val path = pickFile("Import $fileName", FileDialog.LOAD, ".csv") ?: return@OutlinedButton
        val text = runCatching { Files.readString(path) }.getOrElse { return@OutlinedButton report("Import failed: ${it.message}", true) }
        when (val result = csv.import(text, model.records)) {
            is FoxResult.Ok -> { model.updateAll { result.value }; report("Imported ${path.fileName}", false) }
            is FoxResult.Err -> report("Import rejected: ${result.message}", true)
        }
    }) { Text("Import CSV") }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
}
