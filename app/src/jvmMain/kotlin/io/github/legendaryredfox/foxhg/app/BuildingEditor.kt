package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.DecimalField
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.Fox
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.data.map.FixedPos
import io.github.legendaryredfox.foxhg.core.data.map.MapFile

/** The buildings of a map: the list, and the fields of the selected one. Positions are tiles from the map's centre; y is the height. */
@Composable
fun BuildingCard(model: MapEditorModel, map: MapFile) {
    val selected = model.selectedBuilding?.takeIf { it in map.buildings.indices }
    SectionCard(
        stringResource(Res.string.building_buildings),
        subtitle = stringResource(Res.string.building_3d_models_placed_on_the_map_select),
        actions = { Chip(map.buildings.size.toString()) },
    ) {
        FieldFlow {
            ToggleChip(stringResource(Res.string.building_move_here), model.moveBuildings, { model.moveBuildings = it }, Modifier.testTag("chip:Move here"))
            OutlinedButton(onClick = { model.addBuilding(map.buildings.getOrNull(selected ?: -1)?.modelId?.toInt() ?: map.buildings.lastOrNull()?.modelId?.toInt() ?: 0, 0.5, 0.5) }) { Text(stringResource(Res.string.building_add_building)) }
            OutlinedButton(onClick = { selected?.let(model::duplicateBuilding) }, enabled = selected != null) { Text(stringResource(Res.string.building_duplicate_building)) }
            OutlinedButton(onClick = { selected?.let(model::removeBuilding) }, enabled = selected != null) { Text(stringResource(Res.string.building_remove_building)) }
        }
        if (map.buildings.isEmpty()) Text(stringResource(Res.string.building_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        map.buildings.forEachIndexed { i, b ->
            Row(
                Modifier.fillMaxWidth().testTag("building:$i")
                    .background(if (i == selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, MaterialTheme.shapes.small)
                    .clickable { model.selectedBuilding = i }.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BuildingPreview(model, b.modelId.toInt(), 28)
                Text("%02d  model %d at (%.2f, %.2f, %.2f)".format(i, b.modelId, b.x.value, b.y.value, b.z.value), style = Fox.mono)
            }
        }
        if (selected != null) {
            val b = map.buildings[selected]
            var browsing by remember(selected) { mutableStateOf(false) }
            FieldFlow {
                BuildingPreview(model, b.modelId.toInt(), 72)
                OutlinedButton(onClick = { browsing = !browsing }, modifier = Modifier.testTag("browse-models")) { Text(if (browsing) stringResource(Res.string.building_close_models) else stringResource(Res.string.building_browse_models)) }
                NumberField(stringResource(Res.string.building_model), b.modelId.toInt(), 0..maxOf(1, model.pictures.buildingModelCount - 1), { v -> model.updateBuilding(selected) { it.copy(modelId = v.toLong()) } }, Modifier.width(100.dp))
                DecimalField(stringResource(Res.string.headbutt_x), b.x.value, -512.0..512.0, { v -> model.updateBuilding(selected) { it.copy(x = FixedPos.of(v)) } })
                DecimalField(stringResource(Res.string.town_map_height), b.y.value, -512.0..512.0, { v -> model.updateBuilding(selected) { it.copy(y = FixedPos.of(v)) } })
                DecimalField(stringResource(Res.string.building_z), b.z.value, -512.0..512.0, { v -> model.updateBuilding(selected) { it.copy(z = FixedPos.of(v)) } })
            }
            if (browsing) ModelBrowser(model, b.modelId.toInt()) { id -> model.updateBuilding(selected) { it.copy(modelId = id.toLong()) } }
            FieldFlow {
                NumberField(stringResource(Res.string.building_rotation_x), b.rotationX.toInt(), 0..Int.MAX_VALUE, { v -> model.updateBuilding(selected) { it.copy(rotationX = v.toLong()) } }, Modifier.width(110.dp))
                NumberField(stringResource(Res.string.building_rotation_y), b.rotationY.toInt(), 0..Int.MAX_VALUE, { v -> model.updateBuilding(selected) { it.copy(rotationY = v.toLong()) } }, Modifier.width(110.dp))
                NumberField(stringResource(Res.string.building_rotation_z), b.rotationZ.toInt(), 0..Int.MAX_VALUE, { v -> model.updateBuilding(selected) { it.copy(rotationZ = v.toLong()) } }, Modifier.width(110.dp))
                NumberField(stringResource(Res.string.building_scale_x), b.width.toInt(), 0..Int.MAX_VALUE, { v -> model.updateBuilding(selected) { it.copy(width = v.toLong()) } }, Modifier.width(100.dp))
                NumberField(stringResource(Res.string.building_scale_y), b.height.toInt(), 0..Int.MAX_VALUE, { v -> model.updateBuilding(selected) { it.copy(height = v.toLong()) } }, Modifier.width(100.dp))
                NumberField(stringResource(Res.string.building_scale_z), b.length.toInt(), 0..Int.MAX_VALUE, { v -> model.updateBuilding(selected) { it.copy(length = v.toLong()) } }, Modifier.width(100.dp))
            }
            Text(stringResource(Res.string.building_rotation_and_scale_are_the_raw_num), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Every building model of the ROM as a picture; click one to use it. Only the rows on screen are drawn. */
@Composable
private fun ModelBrowser(model: MapEditorModel, current: Int, onPick: (Int) -> Unit) {
    val count = model.pictures.buildingModelCount
    LazyVerticalGrid(GridCells.Adaptive(76.dp), Modifier.fillMaxWidth().height(260.dp).testTag("model-browser"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(count) { id ->
            Column(
                Modifier.testTag("model:$id")
                    .background(if (id == current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                    .clickable { onPick(id) }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BuildingPreview(model, id, 64)
                Text(id.toString(), style = Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BuildingPreview(model: MapEditorModel, modelId: Int, size: Int) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, modelId) { value = withContext(Dispatchers.Default) { model.pictures.buildingPreview(modelId) } }
    val ready = bitmap
    Canvas(Modifier.size(size.dp).testTag("building-preview")) {
        if (ready != null) drawImage(ready, IntOffset.Zero, IntSize(ready.width, ready.height), IntOffset.Zero, IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.Medium)
    }
}
