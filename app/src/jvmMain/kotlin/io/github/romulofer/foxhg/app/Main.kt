package io.github.romulofer.foxhg.app

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.nio.file.Path

/**
 * `foxhg [project-folder | rom.nds] [--editor=PERSONAL|LEARNSET|EVOLUTIONS|MOVES|TRAINERS|ENCOUNTERS|...]`
 *
 * Shortcuts: Ctrl+N new project, Ctrl+O open project, Ctrl+Shift+O open a ROM only, Ctrl+S save, Ctrl+B build ROM, Ctrl+Z undo, Ctrl+Shift+Z or Ctrl+Y redo, Ctrl+F focus the list search; Up/Down/PageUp/PageDown move through a list.
 */
fun main(args: Array<String>) = application {
    val model = remember(args) { AppModel().also { applyLaunchArguments(it, args) } }
    val title = buildString {
        append("foxhg")
        model.projectName?.let { append(" - ").append(it) }
        if (model.hasUnsavedWork) append(" •")
    }
    Window(
        onPreviewKeyEvent = { e -> Actions.shortcut(model, e) },
        onCloseRequest = { Actions.quit(model, ::exitApplication) },
        title = title,
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(1180.dp, 760.dp)),
    ) {
        App(model)
    }
}

internal fun applyLaunchArguments(model: AppModel, args: Array<String>) {
    args.firstOrNull { !it.startsWith("--") }?.let { target ->
        val path = Path.of(target)
        if (io.github.romulofer.foxhg.core.project.ProjectStore.isProject(path)) model.openProject(path) else model.openRom(path)
    }
    args.firstOrNull { it.startsWith("--editor=") }?.removePrefix("--editor=")?.let { name ->
        EditorKind.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { if (model.project != null) model.selected = it }
    }
}
