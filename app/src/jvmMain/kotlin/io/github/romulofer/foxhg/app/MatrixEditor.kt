package io.github.romulofer.foxhg.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.Fox
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.app.ui.ToggleChip
import io.github.romulofer.foxhg.core.data.mapheader.MapHeader
import io.github.romulofer.foxhg.core.data.matrix.GameMatrix

/** Matrix editor state: the generic record model plus which maps (by header) use each matrix. */
class MatrixEditorModel(val records: RecordEditorModel<GameMatrix>, val usedBy: Map<Int, List<String>>, val headerNames: List<String>, val pictures: MapPictures)

@Composable
fun MatrixEditor(model: MatrixEditorModel, modifier: Modifier = Modifier) {
    val editor = model.records
    RecordListDetail(
        editor, modifier,
        rowDetail = { i -> editor.records.getOrNull(i)?.let { "${it.width}×${it.height}" } },
        leading = { i -> editor.records.getOrNull(i)?.let { MapPicture(model.pictures.ofMatrix(it), 26.dp) } },
    ) { matrix ->
        val index = editor.selectedIndex
        var cell by remember(index) { mutableStateOf(0 to 0) }
        val (cx, cy) = cell.let { (x, y) -> minOf(x, matrix.width - 1) to minOf(y, matrix.height - 1) }
        val users = model.usedBy[index].orEmpty()

        EditorHeader(index.toString(), matrix.name, trailing = { Chip("${matrix.width} × ${matrix.height}") })
        if (users.isNotEmpty()) {
            Text("Used by: " + users.distinct().take(6).joinToString(", ") + if (users.distinct().size > 6) " …" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("Overview", subtitle = "The tiles of every cell, as in the Maps editor") {
            val schematic = model.pictures.ofMatrix(matrix)
            // The rendered scenes replace the schematic when they are ready.
            var progress by remember(matrix) { mutableStateOf(0f) }
            var rendering by remember(matrix) { mutableStateOf(true) }
            val bitmap by produceState(schematic, matrix) {
                rendering = true
                value = withContext(Dispatchers.Default) { model.pictures.sceneOfMatrix(matrix) { done, total -> progress = done.toFloat() / total } }
                rendering = false
            }
            if (rendering && matrix.width * matrix.height > 4) {
                androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().testTag("matrix-progress"))
                Text("Rendering the maps… ${(progress * 100).toInt()} %", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val width = minOf(720f, schematic.width * 2f)
            MapPicture(bitmap, width.dp, (width * schematic.height / schematic.width).dp)
        }

        SectionCard("Grid", subtitle = "Each cell is a map file. Click a cell to edit it; empty cells have no map.") {
            Grid(matrix, cx, cy) { x, y -> cell = x to y }
        }

        SectionCard("Cell ($cx, $cy)") {
            FieldFlow {
                NumberField("Map file", matrix.map(cx, cy).let { if (it == GameMatrix.EMPTY) 0 else it }, 0..65534, { v -> editor.update { it.withMap(cx, cy, v) } }, Modifier.width(120.dp))
                OutlinedButton(onClick = { editor.update { it.withMap(cx, cy, GameMatrix.EMPTY) } }, enabled = matrix.map(cx, cy) != GameMatrix.EMPTY) { Text("Make empty") }
                if (matrix.hasHeaders) {
                    PickerField("Map header", matrix.header(cx, cy) ?: 0, model.headerNames, { v -> editor.update { it.withHeader(cx, cy, v) } }, Modifier.width(260.dp))
                }
                if (matrix.hasAltitudes) {
                    NumberField("Altitude", matrix.altitude(cx, cy) ?: 0, 0..255, { v -> editor.update { it.withAltitude(cx, cy, v) } }, Modifier.width(100.dp))
                }
            }
        }

        SectionCard("Matrix") {
            var newWidth by remember(index) { mutableStateOf(matrix.width) }
            var newHeight by remember(index) { mutableStateOf(matrix.height) }
            FieldFlow {
                NumberField("Width", newWidth, 1..255, { newWidth = it }, Modifier.width(90.dp))
                NumberField("Height", newHeight, 1..255, { newHeight = it }, Modifier.width(90.dp))
                Button(onClick = { editor.update { it.resized(newWidth, newHeight) } }, enabled = newWidth != matrix.width || newHeight != matrix.height) { Text("Resize") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleChip("Map headers", matrix.hasHeaders, { on -> editor.update { it.withSections(on, it.hasAltitudes) } })
                ToggleChip("Altitudes", matrix.hasAltitudes, { on -> editor.update { it.withSections(it.hasHeaders, on) } })
            }
        }
    }
}

private val CELL = 40.dp

@Composable
private fun Grid(matrix: GameMatrix, selectedX: Int, selectedY: Int, onSelect: (Int, Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (y in 0 until matrix.height) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (x in 0 until matrix.width) {
                        val id = matrix.map(x, y)
                        val empty = id == GameMatrix.EMPTY
                        val selected = x == selectedX && y == selectedY
                        Box(
                            Modifier.width(CELL).height(CELL)
                                .background(if (empty) scheme.surfaceVariant else scheme.primaryContainer, MaterialTheme.shapes.small)
                                .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, MaterialTheme.shapes.small)
                                .clickable { onSelect(x, y) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (empty) "·" else id.toString(),
                                style = Fox.mono,
                                color = if (empty) scheme.onSurfaceVariant else scheme.onPrimaryContainer,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
