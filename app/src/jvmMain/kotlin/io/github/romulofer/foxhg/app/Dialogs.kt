package io.github.romulofer.foxhg.app

import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JFileChooser

internal fun pickFile(title: String, mode: Int, extension: String): Path? {
    val dialog = FileDialog(null as Frame?, title, mode).apply {
        if (mode == FileDialog.LOAD) setFilenameFilter { _, name -> name.endsWith(extension, ignoreCase = true) }
        isVisible = true
    }
    val file = dialog.file ?: return null
    val path = Path.of(dialog.directory, file)
    return if (mode == FileDialog.SAVE && !file.endsWith(extension, ignoreCase = true)) Path.of(path.toString() + extension) else path
}

/** Folder chooser (the AWT file dialog cannot choose folders on Linux). */
internal fun chooseDirectory(title: String, start: Path? = null): Path? {
    val chooser = JFileChooser(start?.toFile()).apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}

internal fun chooseRomToOpen(): Path? = pickFile("Open ROM", FileDialog.LOAD, ".nds")

internal fun chooseBuildTarget(): Path? = pickFile("Build ROM", FileDialog.SAVE, ".nds")
