package io.github.romulofer.foxhg.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.ToggleChip
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.graphics.Camera3d
import io.github.romulofer.foxhg.core.graphics.Render3d
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val VIEW_WIDTH = 640
private const val VIEW_HEIGHT = 400

/**
 * The 3D view of a map: drag to turn and tilt, right drag to pan, Ctrl + wheel to zoom. A click (no drag) selects the building
 * under the pointer, or with "Move here" on puts the selected building on the ground there. The picture is drawn at half
 * size while the view moves, so it follows the pointer.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Map3dView(model: MapEditorModel, map: Int) {
    val camera = model.camera
    var moving by remember { mutableStateOf(false) }
    val buildings = model.maps.records.getOrNull(map)?.buildings ?: emptyList()
    val selected = model.selectedBuilding ?: Render3d.NOTHING
    val rendered by produceState<MapPictures.Render3dBitmap?>(null, map, camera, moving, buildings, selected) {
        val scale = if (moving) 2 else 1 // half size while moving
        value = withContext(Dispatchers.Default) { model.pictures.render3d(map, camera, VIEW_WIDTH / scale, VIEW_HEIGHT / scale, buildings, selected) }
    }
    val current by rememberUpdatedState(rendered)
    val surface = remember(model.maps.records.getOrNull(map)) { (model.maps.records.getOrNull(map)?.walkingSurface() as? io.github.romulofer.foxhg.core.FoxResult.Ok)?.value }
    val usage = model.usage(map)
    val events = model.eventsOf(usage)
    val mapFile = model.maps.records.getOrNull(map)
    val heights by produceState(FloatArray(MapFile.SIZE * MapFile.SIZE), map) { value = withContext(Dispatchers.Default) { model.pictures.heights(map) } }
    // The markers of the picture that is on screen; the click handler and the drawing use the same ones.
    val markers = remember(rendered, events, usage, model.show3dEvents) {
        val r = rendered
        if (r != null && events != null && usage != null && model.show3dEvents) eventMarkers(r.picture, events, usage, heights) else emptyList()
    }
    val currentMarkers by rememberUpdatedState(markers)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            Modifier.size(VIEW_WIDTH.dp, VIEW_HEIGHT.dp).clipToBounds().testTag("map3d")
                .semantics { stateDescription = "yaw %.2f pitch %.2f distance %.1f x %.1f z %.1f".format(camera.yaw, camera.pitch, camera.distance, camera.targetX, camera.targetZ) }
                .pointerInput(map) {
                    awaitPointerEventScope {
                        var start = Offset.Zero
                        var last = Offset.Zero
                        var dragged = false
                        var pan = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            when (event.type) {
                                PointerEventType.Press -> { start = change.position; last = change.position; dragged = false; pan = event.buttons.isSecondaryPressed }
                                PointerEventType.Move -> if (event.buttons.isPrimaryPressed || event.buttons.isSecondaryPressed) {
                                    if (!dragged && (change.position - start).getDistance() > 4f) { dragged = true; moving = true }
                                    if (dragged) {
                                        val d = change.position - last
                                        val now = model.camera // read again: the pointer block outlives recompositions
                                        model.camera = if (pan) {
                                            // One pixel of the picture is this many tiles at the target's distance.
                                            val tiles = 2 * now.distance * tan(now.fov / 2) / size.height
                                            now.panned(-d.x * tiles, d.y * tiles)
                                        } else now.with(yaw = now.yaw - d.x * 0.008, pitch = now.pitch + d.y * 0.008)
                                    }
                                    last = change.position
                                }
                                PointerEventType.Release -> {
                                    if (!dragged && !pan) current?.let { r ->
                                        val px = (change.position.x * r.picture.image.width / size.width).toInt()
                                        val py = (change.position.y * r.picture.image.height / size.height).toInt()
                                        val marker = markerAt(currentMarkers, change.position.x.toDouble() * r.picture.image.width / size.width, change.position.y.toDouble() * r.picture.image.height / size.height, 14.0 * r.picture.image.width / size.width)
                                        if (marker != null) {
                                            model.selectedEvent = marker.kind to marker.index
                                            model.tool = MapTool.EVENTS
                                            dragged = false; moving = false
                                            return@let
                                        }
                                        val tag = r.picture.tagAt(px, py)
                                        val ground = r.picture.pointAt(px, py)
                                        val chosen = model.selectedBuilding
                                        if (model.moveBuildings && chosen != null && ground != null) {
                                            model.updateBuilding(chosen) { b ->
                                                b.copy(x = io.github.romulofer.foxhg.core.data.map.FixedPos.of((ground[0] * 2).roundToInt() / 2.0), z = io.github.romulofer.foxhg.core.data.map.FixedPos.of((ground[2] * 2).roundToInt() / 2.0))
                                            }
                                        } else model.selectedBuilding = tag.takeIf { it >= 0 }
                                    }
                                    dragged = false
                                    moving = false
                                }
                                else -> {}
                            }
                        }
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    // Plain scrolling belongs to the page; only Ctrl + wheel zooms.
                    if (!event.keyboardModifiers.isCtrlPressed) return@onPointerEvent
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    model.camera = model.camera.let { it.with(distance = it.distance * (1 + delta * 0.08)) }
                    // Zooming used the wheel turn: without this the page around the view scrolls as well.
                    event.changes.forEach { it.consume() }
                },
        ) {
            val r = rendered ?: return@Canvas
            val b = r.bitmap
            drawImage(b, IntOffset.Zero, IntSize(b.width, b.height), IntOffset.Zero, IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Medium)
            val scale = size.width / r.picture.image.width
            if (model.show3dSurface && surface != null) drawSurface(r.picture, surface, scale)
            if (model.show3dCollision && mapFile != null) drawCollision(r.picture, mapFile, heights, scale)
            if (events != null) drawMarkers(markers, events, model.selectedEvent, scale)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { model.camera = Camera3d() }, modifier = Modifier.testTag("reset-view")) { Text("Reset view") }
            ToggleChip("Collision", model.show3dCollision, { model.show3dCollision = it }, Modifier.testTag("chip:3D collision"))
            ToggleChip("Walking surface", model.show3dSurface, { model.show3dSurface = it }, Modifier.testTag("chip:3D surface"))
            ToggleChip("Events", model.show3dEvents, { model.show3dEvents = it }, Modifier.testTag("chip:3D events"))
            val hint = if (model.moveBuildings) "Move here is on: click the ground to put the selected building there" else "Click a building to select it"
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
