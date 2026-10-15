package io.github.romulofer.foxhg.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.Fox
import java.nio.file.Files
import java.nio.file.Path

/** The "Project" button in the top bar and its menu. */
@Composable
fun ProjectMenu(model: AppModel) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) { Text("Project  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable fun item(text: String, enabled: Boolean = true, action: () -> Unit) =
                DropdownMenuItem(text = { Text(text) }, enabled = enabled, onClick = { open = false; action() })
            item("New project…          Ctrl+N") { Actions.newProject(model) }
            item("Open project…         Ctrl+O") { Actions.openProject(model) }
            item("Open ROM without a project…") { Actions.openRom(model) }
            HorizontalDivider()
            item("Save                         Ctrl+S", enabled = model.project != null && (model.projectDir == null || model.hasUnsavedWork)) { Actions.save(model) }
            item("Save as project…", enabled = model.project != null) { Actions.saveAs(model) }
            DropdownMenuItem(
                text = { Text(if (model.autosave) "✓ Autosave" else "   Autosave") },
                enabled = model.projectDir != null,
                onClick = { model.autosave = !model.autosave },
            )
            if (model.recent.isNotEmpty()) {
                HorizontalDivider()
                Text("Recent", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                model.recent.take(6).forEach { entry ->
                    item("${if (entry.kind == RecentKind.PROJECT) "Project" else "ROM"}   ${entry.displayName}") { Actions.openRecent(model, entry) }
                }
            }
        }
    }
}

/** Dialogs that sit on top of the window; shown according to [AppModel.dialog]. */
@Composable
fun AppDialogs(model: AppModel) {
    when (val d = model.dialog) {
        null -> {}
        AppDialog.NewProject -> ProjectDialog(model, askRom = true, then = null)
        is AppDialog.SaveProjectAs -> ProjectDialog(model, askRom = false, then = d.then)
        is AppDialog.UnsavedWork -> UnsavedWorkDialog(model, d)
        is AppDialog.RelinkRom -> RelinkDialog(model, d)
    }
}

@Composable
private fun ProjectDialog(model: AppModel, askRom: Boolean, then: (() -> Unit)?) {
    var name by remember { mutableStateOf(if (askRom) "my-hack" else model.romPath?.fileName?.toString()?.removeSuffix(".nds")?.plus("-hack") ?: "my-hack") }
    var location by remember { mutableStateOf(model.recent.firstOrNull { it.kind == RecentKind.PROJECT }?.path?.parent ?: AppModel.defaultProjectsDir()) }
    var rom by remember { mutableStateOf<Path?>(model.recent.firstOrNull { it.kind == RecentKind.ROM }?.path ?: model.romPath) }
    val folder = AppModel.projectFolder(location, name)
    val problem = when {
        folder == null -> "Use a name without slashes"
        askRom && (rom == null || !Files.exists(rom!!)) -> "Choose the ROM this project edits"
        !AppModel.isUsableProjectFolder(folder) -> "A project already exists in that folder"
        else -> null
    }
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text(if (askRom) "New project" else "Save as project") },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "A project keeps your changes in a folder and saves them automatically. The ROM is never modified or copied.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabeledText("Name", name) { name = it }
                PathRow("Location", location.toString()) { chooseDirectory("Choose where to create the project", location)?.let { location = it } }
                if (askRom) PathRow("ROM", rom?.toString() ?: "No ROM chosen") { chooseRomToOpen()?.let { rom = it } }
                Text(
                    problem ?: "Creates ${folder}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            Button(enabled = problem == null, onClick = {
                val ok = if (askRom) model.newProject(rom!!, folder!!) else model.saveProjectAs(folder!!)
                if (ok) { model.dialog = null; then?.invoke() }
            }) { Text(if (askRom) "Create project" else "Save project") }
        },
        dismissButton = { TextButton(onClick = { model.dialog = null }) { Text("Cancel") } },
    )
}

@Composable
private fun UnsavedWorkDialog(model: AppModel, d: AppDialog.UnsavedWork) {
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text("Your changes are not saved") },
        text = {
            Text(
                "You edited a ROM without a project, so these changes exist only in memory. Save them as a project to keep them, or discard them. You can also cancel and use Build ROM… to write a patched ROM.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { Button(onClick = { model.dialog = AppDialog.SaveProjectAs(then = d.then) }) { Text("Save as project…") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.dialog = null }) { Text("Cancel") }
                TextButton(onClick = { model.dialog = null; d.then() }) { Text("Discard changes", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun RelinkDialog(model: AppModel, d: AppDialog.RelinkRom) {
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text("Can't find the ROM") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.message, style = MaterialTheme.typography.bodyMedium)
                Text("If you moved or renamed it, point the project at it again. Your saved changes are kept.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { chooseRomToOpen()?.let { model.relinkRom(d.projectDir, it) } }) { Text("Locate ROM…") } },
        dismissButton = { TextButton(onClick = { model.dialog = null }) { Text("Cancel") } },
    )
}

@Composable
private fun LabeledText(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().height34().background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { inner() }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PathRow(label: String, value: String, onBrowse: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(value, Modifier.weight(1f), style = Fox.mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = onBrowse) { Text("Browse…") }
        }
    }
}

private fun Modifier.height34(): Modifier = this.then(Modifier.height(Fox.fieldHeight))
