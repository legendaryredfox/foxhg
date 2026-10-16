package io.github.legendaryredfox.foxhg.app

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The file and folder choosers the app opens. Tests swap them, since a native dialog cannot be driven from a test. */
internal object Choosers {
    var file: (title: String, mode: Int, extension: String) -> Path? = ::nativePickFile
    /** Asks for a folder and calls the callback with it, now or later; nothing is called when the user cancels. */
    var directory: (title: String, start: Path?, onPick: (Path) -> Unit) -> Unit = { title, start, onPick -> folderRequest = FolderRequest(title, start, onPick) }

    /** The folder picker that is open, if any; [AppDialogs] draws it. */
    var folderRequest: FolderRequest? by mutableStateOf(null)
}

internal fun pickFile(title: String, mode: Int, extension: String): Path? = Choosers.file(title, mode, extension)

internal fun chooseDirectory(title: String, start: Path? = null, onPick: (Path) -> Unit) = Choosers.directory(title, start, onPick)

private fun nativePickFile(title: String, mode: Int, extension: String): Path? {
    val dialog = FileDialog(null as Frame?, title, mode).apply {
        if (mode == FileDialog.LOAD) setFilenameFilter { _, name -> name.endsWith(extension, ignoreCase = true) }
        isVisible = true
    }
    val file = dialog.file ?: return null
    val path = Path.of(dialog.directory, file)
    return if (mode == FileDialog.SAVE && !file.endsWith(extension, ignoreCase = true)) Path.of(path.toString() + extension) else path
}

internal fun chooseRomToOpen(): Path? = pickFile("Open ROM", FileDialog.LOAD, ".nds")

internal fun chooseBuildTarget(): Path? = pickFile("Build ROM", FileDialog.SAVE, ".nds")
