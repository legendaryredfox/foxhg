package io.github.romulofer.foxhg.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.Fox
import io.github.romulofer.foxhg.app.ui.SearchBox
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.core.nds.RomDirectory
import io.github.romulofer.foxhg.core.nds.RomFile
import io.github.romulofer.foxhg.core.project.RomProject
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Files

/** One line of the tree: a folder (with how many files are under it) or a file. */
class TreeRow(val path: String, val name: String, val depth: Int, val directory: Boolean, val file: RomFile?, val children: Int)

/** The files of the ROM as a tree, read only. The bytes shown are the project's, so edited files show their edits. */
class FileTreeModel(private val project: RomProject) {
    private val expanded = mutableStateListOf<String>()
    var search by mutableStateOf("")
    var selected: String? by mutableStateOf(null)
        private set
    val fileCount: Int get() = project.rom.files.size

    init { expanded += "" }

    fun toggle(path: String) {
        if (path in expanded) expanded.remove(path) else expanded.add(path)
    }

    fun select(path: String) { selected = path }

    fun isExpanded(path: String) = path in expanded

    private fun count(dir: RomDirectory): Int = dir.files.size + dir.directories.sumOf(::count)

    /** The rows to show: the tree with the opened folders, or every file whose path matches the search. */
    fun rows(): List<TreeRow> {
        val q = search.trim()
        if (q.isNotEmpty()) {
            return project.rom.files.filter { it.path.contains(q, ignoreCase = true) }.sortedBy { it.path }
                .map { TreeRow(it.path, it.path, 0, false, it, 0) }
        }
        val out = ArrayList<TreeRow>()
        fun walk(dir: RomDirectory, depth: Int) {
            for (d in dir.directories.sortedBy { it.name }) {
                out += TreeRow(d.path, d.name, depth, true, null, count(d))
                if (d.path in expanded) walk(d, depth + 1)
            }
            for (f in dir.files.sortedBy { it.name }) out += TreeRow(f.path, f.name, depth, false, f, 0)
        }
        walk(project.rom.root, 0)
        return out
    }

    fun file(path: String): RomFile? = project.rom.file(path)

    fun isEdited(path: String): Boolean = project.isEdited(path)

    /** The current bytes of the file, with the project's edits. */
    fun bytes(path: String): ByteArray? = project.read(path)

    /** Writes the file's current bytes to [target]; null on success, else the problem. */
    fun export(path: String, target: java.nio.file.Path): String? {
        val data = bytes(path) ?: return "The ROM has no file $path"
        return try { Files.write(target, data); null } catch (e: IOException) { "Cannot write $target: ${e.message}" }
    }
}

@Composable
fun FileTreeEditor(model: FileTreeModel, appModel: AppModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        Column(Modifier.width(340.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchBox(model.search, { model.search = it }, "Search paths…", Modifier.fillMaxWidth())
            val rows = model.rows()
            LazyColumn(Modifier.fillMaxSize().testTag("file-tree"), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                items(rows, key = { it.path }) { row -> TreeLine(model, row) }
            }
        }
        val path = model.selected
        val file = path?.let(model::file)
        Column(Modifier.fillMaxSize().padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (file == null) {
                Text("${model.fileCount} files in the ROM. Pick one to see its size and bytes.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                EditorHeader(file.id.toString(), file.name, trailing = { if (model.isEdited(file.path)) Chip("edited", emphasis = true) })
                SectionCard("File") {
                    Text(file.path, style = Fox.mono)
                    Text("Size ${file.size} bytes (0x%X)  ·  ROM offset 0x%X".format(file.size, file.start), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = {
                        pickFile("Export ${file.name}", FileDialog.SAVE, "")?.let { target -> model.export(file.path, target)?.let { appModel.error = it } ?: run { appModel.status = "Exported ${file.path} to $target" } }
                    }, modifier = Modifier.testTag("export-file")) { Text("Export file…") }
                }
                SectionCard("Bytes", subtitle = "The first 512 bytes") {
                    Text(hexDump(model.bytes(file.path) ?: ByteArray(0)), style = Fox.mono, modifier = Modifier.testTag("hex-dump"))
                }
            }
        }
    }
}

@Composable
private fun TreeLine(model: FileTreeModel, row: TreeRow) {
    val selected = model.selected == row.path
    Row(
        Modifier.fillMaxWidth().height(26.dp).testTag("tree:${row.path}")
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.small)
            .clickable { if (row.directory) model.toggle(row.path) else model.select(row.path) }
            .padding(start = (8 + row.depth * 14).dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (row.directory) (if (model.isExpanded(row.path)) "▾" else "▸") else " ", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(row.name + if (row.directory) "/" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (row.directory) Text(row.children.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else {
            if (model.isEdited(row.path)) Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            Text(row.file?.size?.let(::sizeText) ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun sizeText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
}

/** `offset  hex bytes  text`, 16 bytes a line, for the first 512 bytes. */
internal fun hexDump(data: ByteArray, limit: Int = 512): String = buildString {
    val end = minOf(data.size, limit)
    for (at in 0 until end step 16) {
        append("%04X  ".format(at))
        for (i in at until at + 16) append(if (i < end) "%02X ".format(data[i].toInt() and 0xFF) else "   ")
        append(' ')
        for (i in at until minOf(end, at + 16)) append(data[i].toInt().and(0xFF).let { if (it in 32..126) it.toChar() else '.' })
        if (at + 16 < end) append('\n')
    }
    if (data.isEmpty()) append("(empty file)")
}
