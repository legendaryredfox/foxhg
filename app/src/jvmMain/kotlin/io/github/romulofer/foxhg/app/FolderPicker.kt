package io.github.romulofer.foxhg.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.Fox
import java.nio.file.Files
import java.nio.file.Path

/** A folder to choose; [onPick] gets exactly the folder that was showing when the user pressed the button. */
class FolderRequest(val title: String, val start: Path?, val onPick: (Path) -> Unit)

/** The first existing folder at or above [start], or the home folder. */
internal fun nearestFolder(start: Path?): Path {
    var p = start?.toAbsolutePath()?.normalize()
    while (p != null && !Files.isDirectory(p)) p = p.parent
    return p ?: Path.of(System.getProperty("user.home"))
}

/** The visible, readable subfolders of [dir], sorted by name. */
internal fun subfolders(dir: Path): List<Path> = try {
    Files.newDirectoryStream(dir).use { stream ->
        stream.filter { Files.isDirectory(it) && !it.fileName.toString().startsWith(".") && Files.isReadable(it) }
            .sortedBy { it.fileName.toString().lowercase() }
    }
} catch (_: Exception) {
    emptyList()
}

/** A folder browser in the app's own style, used instead of the toolkit's folder chooser (ugly, and unreliable on Linux). */
@Composable
fun FolderPicker(request: FolderRequest, onClose: () -> Unit) {
    var current by remember { mutableStateOf(nearestFolder(request.start)) }
    var typed by remember(current) { mutableStateOf(current.toString()) }
    val folders = remember(current) { subfolders(current) }
    val typedFolder = runCatching { Path.of(typed) }.getOrNull()?.takeIf { Files.isDirectory(it) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(request.title) },
        text = {
            Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { current.parent?.let { current = it } }, enabled = current.parent != null, modifier = Modifier.testTag("folder-up")) { Text("Up") }
                    OutlinedButton(onClick = { current = Path.of(System.getProperty("user.home")) }) { Text("Home") }
                    BasicTextField(
                        value = typed,
                        onValueChange = { typed = it; runCatching { Path.of(it) }.getOrNull()?.takeIf { p -> Files.isDirectory(p) }?.let { p -> current = p.toAbsolutePath().normalize() } },
                        singleLine = true,
                        textStyle = Fox.mono.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Row(
                                Modifier.fillMaxWidth().height(Fox.fieldHeight).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                                    .border(1.dp, if (typedFolder == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { inner() }
                        },
                        modifier = Modifier.weight(1f).testTag("folder-path"),
                    )
                }
                Box(Modifier.fillMaxWidth().height(280.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)) {
                    if (folders.isEmpty()) {
                        Text("No subfolders", Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.fillMaxWidth()) {
                            items(folders) { folder ->
                                val name = folder.fileName.toString()
                                Text(
                                    name,
                                    Modifier.fillMaxWidth().clickable { current = folder }.padding(horizontal = 14.dp, vertical = 8.dp).testTag("folder:$name"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Text("Choosing: $current", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = { Button(onClick = { request.onPick(current); onClose() }, modifier = Modifier.testTag("folder-choose")) { Text("Choose this folder") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
