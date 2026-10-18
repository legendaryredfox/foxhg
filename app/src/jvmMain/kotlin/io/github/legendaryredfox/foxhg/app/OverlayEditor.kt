package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.Fox
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.nds.OverlayEntry
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The ARM9 overlays of the ROM: the table the game loads them from, and each overlay's decompressed image to export, replace (same size) or revert.
 * foxhg compresses an edited overlay again when it builds the ROM, as the table says it is stored, so the compression flags are shown and not edited
 * (DSPRE's own overlay editor has its compression switch disabled for the same reason: the packed size and the flag must agree with the loader).
 */
class OverlayEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val overlays: List<OverlayEntry> get() = project.rom.arm9Overlays
    var selected: Int? by mutableStateOf(null)
        private set
    var message: String? by mutableStateOf(null)
        private set
    var revision: Int by mutableStateOf(0)
        private set

    fun select(id: Int) { selected = id; message = null }

    fun entry(id: Int): OverlayEntry? = overlays.firstOrNull { it.id == id }

    fun isEdited(id: Int): Boolean = id in project.editedOverlays

    /** The decompressed image including edits. */
    fun image(id: Int): ByteArray? = (project.readOverlay(id) as? FoxResult.Ok)?.value

    /** Size of the overlay file as the ROM stores it, with no edits. */
    fun storedSize(entry: OverlayEntry): Long? = project.rom.file(entry.fileId)?.size

    fun export(id: Int, target: Path): String? {
        val data = image(id) ?: return "Overlay $id cannot be read"
        return try { Files.write(target, data); null } catch (e: IOException) { "Cannot write $target: ${e.message}" }
    }

    /** Replaces the decompressed image with the file at [source]; it must be as big as the overlay is. Returns the problem, or null. */
    fun import(id: Int, source: Path): String? {
        val bytes = try { Files.readAllBytes(source) } catch (e: IOException) { return "Cannot read $source: ${e.message}" }
        return when (val r = project.writeOverlay(id, bytes)) {
            is FoxResult.Ok -> { revision++; onChanged(); message = "Overlay $id replaced; the ROM changes when it is built."; null }
            is FoxResult.Err -> r.message
        }
    }

    fun revert(id: Int) {
        project.revertOverlay(id)
        revision++
        onChanged()
        message = "Overlay $id is the ROM's again."
    }
}

@Composable
fun OverlayEditor(model: OverlayEditorModel, appModel: AppModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.overlay_x_overlays, model.overlays.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @Suppress("UNUSED_EXPRESSION") model.revision
            LazyColumn(Modifier.fillMaxSize().testTag("overlay-list"), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                items(model.overlays, key = { it.id }) { o ->
                    val selected = model.selected == o.id
                    Row(
                        Modifier.fillMaxWidth().height(26.dp).testTag("overlay:${o.id}")
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, MaterialTheme.shapes.small)
                            .clickable { model.select(o.id) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("%3d".format(o.id), style = Fox.mono)
                        Text("0x%08X".format(o.ramAddress), Modifier.weight(1f), style = Fox.mono)
                        if (model.isEdited(o.id)) Text("●", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        if (o.isCompressed) Text(stringResource(Res.string.message_packed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val id = model.selected
        val entry = id?.let(model::entry)
        Column(Modifier.fillMaxSize().padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (entry == null) {
                Text(stringResource(Res.string.overlay_pick_an_overlay_to_see_where_the_g), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            @Suppress("UNUSED_EXPRESSION") model.revision
            val image = model.image(entry.id)
            EditorHeader(entry.id.toString(), "Overlay ${entry.id}", trailing = { if (model.isEdited(entry.id)) Chip(stringResource(Res.string.map_edited), emphasis = true) })
            SectionCard(stringResource(Res.string.overlay_table_entry), subtitle = stringResource(Res.string.overlay_from_the_overlay_table_y9_bin_of_t)) {
                Text(
                    "RAM address 0x%08X  ·  code 0x%X bytes  ·  BSS 0x%X bytes\nStatic initialisers 0x%08X to 0x%08X\nFile %d  ·  flags 0x%02X".format(
                        entry.ramAddress, entry.ramSize, entry.bssSize, entry.staticInitStart, entry.staticInitEnd, entry.fileId, entry.flags,
                    ),
                    style = Fox.mono,
                )
                val stored = model.storedSize(entry)
                Text(
                    if (entry.isCompressed) "Stored compressed (BLZ): ${stored ?: "?"} bytes in the ROM, ${image?.size ?: "?"} bytes decompressed. Built again compressed when the ROM is built."
                    else "Stored as it is: ${stored ?: "?"} bytes in the ROM.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SectionCard(stringResource(Res.string.overlay_image), subtitle = "The decompressed overlay. A replacement must be exactly ${image?.size ?: "?"} bytes: the table and the code after it depend on the size.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickFile("Export overlay ${entry.id}", FileDialog.SAVE, "")?.let { target ->
                            model.export(entry.id, target)?.let { appModel.error = it } ?: run { appModel.status = "Exported overlay ${entry.id} to $target" }
                        }
                    }, modifier = Modifier.testTag("export-overlay")) { Text(stringResource(Res.string.overlay_export_image)) }
                    OutlinedButton(onClick = {
                        pickFile("Replace overlay ${entry.id}", FileDialog.LOAD, "")?.let { source -> model.import(entry.id, source)?.let { appModel.error = it } }
                    }, modifier = Modifier.testTag("import-overlay")) { Text(stringResource(Res.string.overlay_replace_with_a_file)) }
                    OutlinedButton(onClick = { model.revert(entry.id) }, enabled = model.isEdited(entry.id), modifier = Modifier.testTag("revert-overlay")) { Text(stringResource(Res.string.message_revert)) }
                }
                model.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            SectionCard(stringResource(Res.string.file_tree_bytes), subtitle = stringResource(Res.string.file_tree_the_first_512_bytes)) {
                Text(hexDump(image ?: ByteArray(0)), style = Fox.mono, modifier = Modifier.testTag("overlay-hex"))
            }
        }
    }
}
