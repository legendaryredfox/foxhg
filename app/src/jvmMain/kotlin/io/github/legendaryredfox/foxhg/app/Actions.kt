package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.legendaryredfox.foxhg.app.ui.TextFocus
import io.github.legendaryredfox.foxhg.core.project.ProjectStore

/** What the menu, the buttons and the keyboard shortcuts do. One place, so they cannot drift apart. */
object Actions {
    /** The window's keyboard shortcuts (see `Main.kt`); true when [e] was one of them. */
    fun shortcut(model: AppModel, e: androidx.compose.ui.input.key.KeyEvent): Boolean =
        e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && shortcut(model, e.key, e.isCtrlPressed || e.isMetaPressed, e.isShiftPressed)

    /** Ctrl (or Cmd) + [key], with Shift when [shift]; true when it is a shortcut. */
    fun shortcut(model: AppModel, key: Key, ctrl: Boolean, shift: Boolean): Boolean {
        if (!ctrl) return false
        return when {
            key == Key.N -> { newProject(model); true }
            key == Key.O && shift -> { openRom(model); true }
            key == Key.O -> { openProject(model); true }
            key == Key.S -> { save(model); true }
            key == Key.B -> { build(model); true }
            key == Key.R -> { run(model); true }
            key == Key.F -> SearchFocus.request()
            // In a script or message box Ctrl+Z belongs to the box (it undoes the typing); elsewhere it undoes the project,
            // and a key with nothing to undo or redo is left alone.
            (key == Key.Z && shift) || key == Key.Y -> if (TextFocus.active || !model.canRedo) false else { model.redo(); true }
            key == Key.Z -> if (TextFocus.active || !model.canUndo) false else { model.undo(); true }
            else -> false
        }
    }

    fun newProject(model: AppModel) = model.guard { model.dialog = AppDialog.NewProject }

    fun openProject(model: AppModel) = model.guard {
        val start = model.recent.firstOrNull { it.kind == RecentKind.PROJECT }?.path?.parent ?: AppModel.defaultProjectsDir().takeIf { it.toFile().exists() }
        chooseDirectory("Open project folder", start) { dir ->
            if (ProjectStore.isProject(dir)) model.openProject(dir) else model.error = "$dir is not a foxhg project folder"
        }
    }

    fun openRom(model: AppModel) = model.guard { chooseRomToOpen()?.let(model::openRom) }

    /** Opens a save file in the save editor; asks first when the save that is open has edits that are not written. */
    fun openSave(model: AppModel) = model.guardSave { pickFile("Open save file (.sav or .dsv)", java.awt.FileDialog.LOAD, "")?.let(model::openSave) }

    /** A file or folder dropped on the window: a project folder opens as a project, a `.nds` as a ROM, anything else is refused. */
    fun openDropped(model: AppModel, files: List<java.nio.file.Path>) {
        val path = files.firstOrNull() ?: return
        when {
            java.nio.file.Files.isDirectory(path) ->
                if (ProjectStore.isProject(path)) model.guard { model.openProject(path) } else model.error = "$path is not a foxhg project folder"
            path.fileName.toString().endsWith(".nds", ignoreCase = true) -> model.guard { model.openRom(path) }
            path.fileName.toString().let { it.endsWith(".sav", ignoreCase = true) || it.endsWith(".dsv", ignoreCase = true) } -> model.guardSave { model.openSave(path) }
            else -> model.error = "Drop a .nds ROM, a .sav save or a foxhg project folder, not ${path.fileName}"
        }
    }

    fun openRecent(model: AppModel, entry: RecentEntry) = model.guard {
        when (entry.kind) {
            RecentKind.PROJECT -> model.openProject(entry.path)
            RecentKind.ROM -> model.openRom(entry.path)
        }
    }

    /** Ctrl+S: saves the project, or asks where to create one when only a ROM is open. */
    fun save(model: AppModel) {
        // On a screen of the save editor Ctrl+S writes the save file.
        if (model.selected?.group == SAVE_GROUP) { model.save?.takeIf { it.dirty }?.write(); return }
        if (model.project == null) return
        if (model.projectDir != null) model.saveProject() else model.dialog = AppDialog.SaveProjectAs()
    }

    fun saveAs(model: AppModel) {
        if (model.project != null) model.dialog = AppDialog.SaveProjectAs()
    }

    fun run(model: AppModel) = model.run()

    fun build(model: AppModel) {
        if (model.project != null) chooseBuildTarget()?.let(model::buildInBackground)
    }

    /**
     * Closes the window. A running task (build, Run) is waited for off the UI thread, so the progress window keeps
     * moving instead of the whole window freezing; [exit] runs on the UI thread once it is done.
     */
    fun quit(model: AppModel, exit: () -> Unit) = model.guard {
        if (model.task == null) {
            model.close()
            exit()
        } else {
            Thread {
                model.awaitTask()
                javax.swing.SwingUtilities.invokeLater { model.close(); exit() }
            }.apply { isDaemon = true; name = "foxhg-quit"; start() }
        }
    }
}
