package io.github.legendaryredfox.foxhg.app

import io.github.legendaryredfox.foxhg.app.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.Fox
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.core.save.Location
import io.github.legendaryredfox.foxhg.core.save.PlayerData
import java.nio.file.Files
import java.nio.file.Path

/** The "Project" button in the top bar and its menu. */
@Composable
fun ProjectMenu(model: AppModel) {
    var open by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { open = true }) { Text(stringResource(Res.string.menu_project) + "  ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            @Composable fun item(text: String, shortcut: String = "", enabled: Boolean = true, action: () -> Unit) =
                DropdownMenuItem(
                    text = { Text(text) },
                    trailingIcon = if (shortcut.isEmpty()) null else ({ Text(shortcut, Modifier.padding(start = 24.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }),
                    enabled = enabled,
                    onClick = { open = false; action() },
                )
            @Composable fun step(base: StringResource, withTarget: StringResource, step: AppModel.StepTarget?): String = when {
                step == null -> stringResource(base)
                step.files == 1 -> stringResource(withTarget, step.label)
                else -> stringResource(withTarget, pluralStringResource(Res.plurals.step_files, step.files, step.files))
            }
            item(stringResource(Res.string.menu_new_project), "Ctrl+N") { Actions.newProject(model) }
            item(stringResource(Res.string.menu_open_project), "Ctrl+O") { Actions.openProject(model) }
            item(stringResource(Res.string.menu_open_rom), "Ctrl+Shift+O") { Actions.openRom(model) }
            item(stringResource(Res.string.menu_open_save)) { Actions.openSave(model) }
            item(stringResource(Res.string.menu_close_save), enabled = model.save != null) { model.guardSave { model.closeSave() } }
            HorizontalDivider()
            item(step(Res.string.menu_undo, Res.string.menu_undo_what, model.undoStep), "Ctrl+Z", enabled = model.canUndo) { model.undo() }
            item(step(Res.string.menu_redo, Res.string.menu_redo_what, model.redoStep), "Ctrl+Shift+Z", enabled = model.canRedo) { model.redo() }
            HorizontalDivider()
            item(stringResource(Res.string.menu_save), "Ctrl+S", enabled = model.project != null && (model.projectDir == null || model.hasUnsavedWork)) { Actions.save(model) }
            item(stringResource(Res.string.menu_save_as_project), enabled = model.project != null) { Actions.saveAs(model) }
            DropdownMenuItem(
                text = { Text((if (model.autosave) "✓ " else "   ") + stringResource(Res.string.menu_autosave)) },
                enabled = model.projectDir != null,
                onClick = { model.autosave = !model.autosave },
            )
            HorizontalDivider()
            item(stringResource(Res.string.menu_settings)) { model.dialog = AppDialog.Settings() }
            if (model.recent.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(Res.string.menu_recent), Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                model.recent.take(6).forEach { entry ->
                    item("${stringResource(if (entry.kind == RecentKind.PROJECT) Res.string.kind_project else Res.string.kind_rom)}   ${entry.displayName}") { Actions.openRecent(model, entry) }
                }
            }
        }
    }
}

/** Dialogs that sit on top of the window; shown according to [AppModel.dialog]. */
@Composable
fun AppDialogs(model: AppModel) {
    model.task?.let { ProgressDialog(it) }
    when (val d = model.dialog) {
        null -> {}
        AppDialog.NewProject -> ProjectDialog(model, askRom = true, then = null)
        AppDialog.NewSave -> NewSaveDialog(model)
        is AppDialog.SaveProjectAs -> ProjectDialog(model, askRom = false, then = d.then)
        is AppDialog.UnsavedWork -> UnsavedWorkDialog(model, d)
        is AppDialog.UnsavedSave -> UnsavedSaveDialog(model, d)
        is AppDialog.RelinkRom -> RelinkDialog(model, d)
        is AppDialog.Settings -> SettingsDialog(model, d)
    }
    Choosers.folderRequest?.let { request -> FolderPicker(request) { Choosers.folderRequest = null } }
}

@Composable
private fun ProjectDialog(model: AppModel, askRom: Boolean, then: (() -> Unit)?) {
    var name by remember { mutableStateOf(if (askRom) "my-hack" else model.romPath?.fileName?.toString()?.removeSuffix(".nds")?.plus("-hack") ?: "my-hack") }
    var location by remember { mutableStateOf(model.recent.firstOrNull { it.kind == RecentKind.PROJECT }?.path?.parent ?: AppModel.defaultProjectsDir()) }
    var rom by remember { mutableStateOf<Path?>(model.recent.firstOrNull { it.kind == RecentKind.ROM }?.path ?: model.romPath) }
    val folder = AppModel.projectFolder(location, name)
    val problem = when {
        folder == null -> stringResource(Res.string.dialog_problem_name)
        askRom && (rom == null || !Files.exists(rom!!)) -> stringResource(Res.string.dialog_problem_rom)
        !AppModel.isUsableProjectFolder(folder) -> stringResource(Res.string.dialog_problem_folder)
        else -> null
    }
    val chooseLocationTitle = stringResource(Res.string.dialog_choose_location)
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text(stringResource(if (askRom) Res.string.dialog_new_project else Res.string.dialog_save_as_project)) },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    stringResource(Res.string.dialog_project_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabeledText(stringResource(Res.string.dialog_field_name), name, tag = "Name") { name = it }
                PathRow(stringResource(Res.string.dialog_field_location), location.toString()) { chooseDirectory(chooseLocationTitle, location) { location = it } }
                if (askRom) PathRow(stringResource(Res.string.dialog_field_rom), rom?.toString() ?: stringResource(Res.string.dialog_no_rom_chosen)) { chooseRomToOpen()?.let { rom = it } }
                Text(
                    problem ?: stringResource(Res.string.dialog_creates, folder.toString()),
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
            }) { Text(stringResource(if (askRom) Res.string.dialog_create_project else Res.string.dialog_save_project)) }
        },
        dismissButton = { TextButton(onClick = { model.dialog = null }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun UnsavedWorkDialog(model: AppModel, d: AppDialog.UnsavedWork) {
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text(stringResource(Res.string.unsaved_title)) },
        text = {
            Text(
                stringResource(Res.string.unsaved_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { Button(onClick = { model.dialog = AppDialog.SaveProjectAs(then = d.then) }) { Text(stringResource(Res.string.menu_save_as_project)) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.dialog = null }) { Text(stringResource(Res.string.action_cancel)) }
                TextButton(onClick = { model.dialog = null; d.then() }) { Text(stringResource(Res.string.unsaved_discard), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun UnsavedSaveDialog(model: AppModel, d: AppDialog.UnsavedSave) {
    val name = model.save?.path?.fileName ?: ""
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text(stringResource(Res.string.unsaved_save_title)) },
        text = { Text(stringResource(Res.string.unsaved_save_body, name.toString()), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(onClick = { if (model.save?.write() == true) { model.dialog = null; model.guard(d.then) } }, modifier = Modifier.testTag("unsaved-save-write")) { Text(stringResource(Res.string.unsaved_save_write)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.dialog = null }) { Text(stringResource(Res.string.action_cancel)) }
                TextButton(onClick = { model.save?.discard(); model.dialog = null; model.guard(d.then) }) { Text(stringResource(Res.string.unsaved_discard), color = MaterialTheme.colorScheme.error) }
            }
        },
    )
}

@Composable
private fun RelinkDialog(model: AppModel, d: AppDialog.RelinkRom) {
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text(stringResource(Res.string.relink_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(d.message, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(Res.string.relink_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { chooseRomToOpen()?.let { model.relinkRom(d.projectDir, it) } }) { Text(stringResource(Res.string.relink_locate)) } },
        dismissButton = { TextButton(onClick = { model.dialog = null }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

/** Shown over the window while a background task runs; it cannot be dismissed, so nothing is edited meanwhile. */
@Composable
private fun ProgressDialog(task: AppModel.Task) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(task.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("progress")) {
                val fraction = task.fraction
                if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(if (fraction == null) stringResource(Res.string.progress_working) else "${(fraction * 100).toInt()} %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun SettingsDialog(model: AppModel, d: AppDialog.Settings) {
    var theme by remember { mutableStateOf(model.settings.theme) }
    var emulator by remember { mutableStateOf(model.settings.emulatorCommand) }
    var autosave by remember { mutableStateOf(model.settings.autosave) }
    var language by remember { mutableStateOf(model.settings.language) }
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text(stringResource(Res.string.settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                d.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChoice.entries.forEach { ToggleChip(stringResource(it.labelResource()), theme == it, { _ -> theme = it }) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(Res.string.settings_language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FieldFlow {
                        AppLanguage.entries.forEach { l ->
                            ToggleChip(if (l == AppLanguage.SYSTEM) stringResource(Res.string.theme_system) else l.nativeName, language == l, { _ -> language = l }, Modifier.testTag("language:${l.tag}"))
                        }
                    }
                    if (!language.resolved().reviewed) Text(stringResource(Res.string.settings_language_unreviewed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LabeledText(stringResource(Res.string.settings_emulator), emulator, tag = "Emulator command (Run)") { emulator = it }
                Text(
                    stringResource(Res.string.settings_emulator_help),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip(stringResource(Res.string.settings_autosave), autosave, { autosave = it }, tag = "Autosave projects")
                    Text(stringResource(Res.string.settings_autosave_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // The Project menu's switch is for this session: it only follows the setting when the setting was changed here.
                val changedHere = autosave != model.settings.autosave
                model.updateSettings(AppSettings(theme, emulator.trim(), autosave, language))
                if (changedHere) model.autosave = autosave
                model.dialog = null
            }, modifier = Modifier.testTag("settings-save")) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = { model.dialog = null }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

private fun ThemeChoice.labelResource() = when (this) {
    ThemeChoice.SYSTEM -> Res.string.theme_system
    ThemeChoice.LIGHT -> Res.string.theme_light
    ThemeChoice.DARK -> Res.string.theme_dark
}

@Composable
internal fun LabeledText(label: String, value: String, tag: String = label, onChange: (String) -> Unit) {
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
            modifier = Modifier.fillMaxWidth().testTag("field:$tag"),
        )
    }
}

@Composable
private fun PathRow(label: String, value: String, onBrowse: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(value, Modifier.weight(1f), style = Fox.mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = onBrowse) { Text(stringResource(Res.string.action_browse)) }
        }
    }
}

private fun Modifier.height34(): Modifier = this.then(Modifier.height(Fox.fieldHeight))

/** A save file written from scratch: the player's name and gender and where the game continues; the file is asked for after Create. */
@Composable
private fun NewSaveDialog(model: AppModel) {
    var name by remember { mutableStateOf("FOX") }
    var gender by remember { mutableStateOf(0) }
    var map by remember { mutableStateOf(PRESETS[0].place.map) }
    var x by remember { mutableStateOf(PRESETS[0].place.x) }
    var y by remember { mutableStateOf(PRESETS[0].place.y) }
    var direction by remember { mutableStateOf(PRESETS[0].place.direction) }
    val problem = when {
        name.isBlank() -> stringResource(Res.string.new_save_problem_empty)
        name.length > PlayerData.MAX_NAME -> stringResource(Res.string.new_save_problem_long, PlayerData.MAX_NAME)
        else -> null
    }
    val writeTitle = stringResource(Res.string.new_save_write_title)
    AlertDialog(
        onDismissRequest = { model.dialog = null },
        title = { Text(stringResource(Res.string.new_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.new_save_body),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LabeledText(stringResource(Res.string.new_save_name, PlayerData.MAX_NAME), name, tag = "Name (up to ${PlayerData.MAX_NAME} characters)") { name = it }
                PickerField(stringResource(Res.string.new_save_gender), gender, PlayerData.GENDERS, { gender = it }, Modifier.width(140.dp), tag = "Gender")
                FieldFlow {
                    NumberField(stringResource(Res.string.new_save_map), map, 0..65535, { map = it }, Modifier.width(100.dp), tag = "Map")
                    NumberField(stringResource(Res.string.new_save_tile_x), x, 0..1023, { x = it }, Modifier.width(100.dp), tag = "Tile X")
                    NumberField(stringResource(Res.string.new_save_tile_y), y, 0..1023, { y = it }, Modifier.width(100.dp), tag = "Tile Y")
                    PickerField(stringResource(Res.string.new_save_facing), direction, Location.DIRECTIONS, { direction = it }, Modifier.width(140.dp), tag = "Facing")
                }
                PRESETS.forEach { p ->
                    OutlinedButton(onClick = { map = p.place.map; x = p.place.x; y = p.place.y; direction = p.place.direction }) { Text(p.label) }
                }
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                enabled = problem == null,
                onClick = {
                    val target = pickFile(writeTitle, java.awt.FileDialog.SAVE, ".sav")
                    if (target != null) { model.dialog = null; model.createSave(target, name, gender, Location(map, -1, x, y, direction)) }
                },
                modifier = Modifier.testTag("new-save-create"),
            ) { Text(stringResource(Res.string.new_save_create)) }
        },
        dismissButton = { TextButton(onClick = { model.dialog = null }) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
