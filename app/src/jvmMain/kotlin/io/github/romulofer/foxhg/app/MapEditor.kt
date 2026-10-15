package io.github.romulofer.foxhg.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.app.ui.Swatch
import io.github.romulofer.foxhg.app.ui.ToggleChip
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.map.Collision
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.data.map.MapUsage
import io.github.romulofer.foxhg.core.data.map.MapUsages
import io.github.romulofer.foxhg.core.data.map.TileTypes
import io.github.romulofer.foxhg.core.project.RomProject

enum class MapLayer(val label: String) { TYPES("Tile types"), COLLISION("Collision"), BOTH("Both") }
enum class MapTool(val label: String) { PAINT("Paint"), FILL("Fill"), PICK("Pick") }

/** State of the Maps editor: the map files are a [RecordEditorModel], the rest is how they are shown and painted. */
class MapEditorModel(project: RomProject, onChanged: () -> Unit = {}) {
    private val usages = MapUsages(project)
    private val names = project.names
    private val events: List<EventFile> = (EventFile.load(project) as? FoxResult.Ok)?.value?.records ?: emptyList()

    val maps = RecordEditorModel(project, MapFile.SPEC, ::label, onChanged)

    var layer by mutableStateOf(MapLayer.BOTH)
    var tool by mutableStateOf(MapTool.PAINT)
    var brushType by mutableStateOf(2)
    var brushCollision by mutableStateOf(0)
    var paintType by mutableStateOf(true)
    var paintCollision by mutableStateOf(false)
    var showEvents by mutableStateOf(true)
    var showBuildings by mutableStateOf(true)
    var usageIndex by mutableStateOf(0)
    var hover: Pair<Int, Int>? by mutableStateOf(null)

    fun usagesOf(map: Int): List<MapUsage> = usages.of(map)

    fun usage(map: Int): MapUsage? = usagesOf(map).getOrNull(usageIndex.coerceAtMost((usagesOf(map).size - 1).coerceAtLeast(0)))

    fun headerName(header: Int): String =
        usages.headers.getOrNull(header)?.let { names?.locations?.getOrNull(it.locationName) }?.takeIf { it.isNotBlank() } ?: "Header $header"

    fun label(map: Int): String {
        val first = usages.of(map).firstOrNull()
        return "%03d  %s".format(map, first?.let { headerName(it.header) } ?: "(not in a matrix)")
    }

    fun eventsOf(usage: MapUsage?): EventFile? = usage?.let { events.getOrNull(usages.headers.getOrNull(it.header)?.eventFile ?: -1) }

    fun select(map: Int) {
        usageIndex = 0
        maps.select(map)
    }

    fun apply(x: Int, y: Int) {
        val type = if (paintType) brushType else null
        val collision = if (paintCollision) brushCollision else null
        when (tool) {
            MapTool.PICK -> maps.selected?.let { m -> brushType = m.type(x, y); brushCollision = m.collision(x, y) }
            MapTool.PAINT -> maps.selected?.let { m ->
                if ((type == null || m.type(x, y) == type) && (collision == null || m.collision(x, y) == collision)) return
                maps.update { it.withTile(x, y, type, collision) }
            }
            MapTool.FILL -> maps.update { it.filled(x, y, type, collision) }
        }
    }
}

@Composable
fun MapEditor(model: MapEditorModel, modifier: Modifier = Modifier) {
    RecordListDetail(model.maps, modifier, rowDetail = { model.usagesOf(it).size.takeIf { n -> n > 1 }?.let { n -> "×$n" } }, onSelect = model::select) { map ->
        val index = model.maps.selectedIndex
        EditorHeader(index.toString(), nameOf(model.maps.label(index)))
        val usages = model.usagesOf(index)
        val usage = model.usage(index)
        if (usages.size > 1) {
            PickerField("Used as", model.usageIndex.coerceAtMost(usages.size - 1), usages.map { "matrix ${it.matrix} cell ${it.x},${it.y} (${model.headerName(it.header)})" }, { model.usageIndex = it }, Modifier.width(420.dp))
        } else if (usage != null) {
            Text("Matrix ${usage.matrix}, cell ${usage.x},${usage.y}, map header ${usage.header}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard("Tiles", subtitle = "Left click paints, right click picks. The tile type is what the game calls the tile behaviour; collision 0x80 blocks.") {
            FieldFlow {
                MapLayer.entries.forEach { l -> ToggleChip(l.label, model.layer == l, { model.layer = l }) }
                MapTool.entries.forEach { t -> ToggleChip(t.label, model.tool == t, { model.tool = t }) }
                ToggleChip("Events", model.showEvents, { model.showEvents = it })
                ToggleChip("Buildings", model.showBuildings, { model.showBuildings = it })
            }
            FieldFlow {
                ToggleChip("Paint type", model.paintType, { model.paintType = it })
                PickerField("Type", model.brushType, TileTypes.all.map { "${it.id}  ${it.label}" }, { model.brushType = it }, Modifier.width(280.dp), leading = { Swatch(typeColor(it)) })
                ToggleChip("Paint collision", model.paintCollision, { model.paintCollision = it })
                NumberField("Collision", model.brushCollision, 0..255, { model.brushCollision = it }, Modifier.width(110.dp))
                ToggleChip("Walk", model.brushCollision == 0, { model.brushCollision = 0 })
                ToggleChip("Block", model.brushCollision == Collision.BLOCKED, { model.brushCollision = Collision.BLOCKED })
            }
            MapCanvas(model, map, usage)
            MapLegend(model, map)
        }
        SectionCard("Buildings", subtitle = "3D models placed on the map; they are only listed here", actions = { Chip(map.buildings.size.toString()) }) {
            if (map.buildings.isEmpty()) Text("None.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            map.buildings.forEachIndexed { i, b ->
                Text("%02d  model %d at (%.2f, %.2f, %.2f)".format(i, b.modelId, b.x.value, b.y.value, b.z.value), style = io.github.romulofer.foxhg.app.ui.Fox.mono)
            }
        }
    }
}

private const val CELL = 18f

internal fun typeColor(type: Int): Color {
    val info = TileTypes.info(type)
    val name = info.name ?: ""
    return when {
        type == 0 -> Color(0xFFEFEAE0)
        name.startsWith("WARP") || name.startsWith("DOOR") || name.startsWith("LADDER") || name.contains("STAIRS") || name.startsWith("ESCALATOR") -> Color(0xFFB794F4)
        info.surfable -> Color(0xFF63B3ED)
        info.encounters -> Color(0xFF68D391)
        name.startsWith("JUMP") || name.startsWith("SLIDE") || name == "ICE" -> Color(0xFFF6AD55)
        else -> Color.hsv(((type * 47) % 360).toFloat(), 0.35f, 0.9f)
    }
}

@Composable
private fun MapCanvas(model: MapEditorModel, map: MapFile, usage: MapUsage?) {
    val events = model.eventsOf(usage)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(
        Modifier.size((CELL * MapFile.SIZE).dp).pointerInput(model, map) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val p = event.changes.first().position
                    val cell = (p.x / (size.width / MapFile.SIZE.toFloat())).toInt() to (p.y / (size.height / MapFile.SIZE.toFloat())).toInt()
                    val inside = cell.first in 0 until MapFile.SIZE && cell.second in 0 until MapFile.SIZE
                    model.hover = if (inside) cell else null
                    if (!inside) continue
                    when (event.type) {
                        PointerEventType.Press -> if (event.buttons.isSecondaryPressed) { val keep = model.tool; model.tool = MapTool.PICK; model.apply(cell.first, cell.second); model.tool = keep } else if (event.buttons.isPrimaryPressed) model.apply(cell.first, cell.second)
                        PointerEventType.Move -> if (event.buttons.isPrimaryPressed && model.tool == MapTool.PAINT) model.apply(cell.first, cell.second)
                        else -> {}
                    }
                }
            }
        },
    ) {
        val cell = size.width / MapFile.SIZE
        for (y in 0 until MapFile.SIZE) for (x in 0 until MapFile.SIZE) {
            val type = map.type(x, y)
            val collision = map.collision(x, y)
            val topLeft = Offset(x * cell, y * cell)
            val base = if (model.layer == MapLayer.COLLISION) Color(0xFFEFEAE0) else typeColor(type)
            drawRect(base, topLeft, Size(cell, cell))
            if (model.layer != MapLayer.TYPES) {
                if (Collision.isBlocked(collision)) drawRect(Color(0xAA7F1D1D), topLeft, Size(cell, cell))
                else if (collision != 0) drawCircle(Color(0x667F1D1D), cell * 0.1f, topLeft + Offset(cell / 2, cell / 2))
            }
        }
        for (i in 0..MapFile.SIZE) {
            val c = i * cell
            val w = if (i % 8 == 0) 1.5f else 0.5f
            drawLine(outline.copy(alpha = if (i % 8 == 0) 0.7f else 0.3f), Offset(c, 0f), Offset(c, size.height), w)
            drawLine(outline.copy(alpha = if (i % 8 == 0) 0.7f else 0.3f), Offset(0f, c), Offset(size.width, c), w)
        }
        if (model.showBuildings) {
            for (b in map.buildings) {
                val bx = (b.x.value + 16).toFloat() * cell
                val by = (b.z.value + 16).toFloat() * cell
                drawRect(Color(0xFFDD6B20), Offset(bx - cell / 2, by - cell / 2), Size(cell, cell), style = Stroke(2f))
            }
        }
        if (model.showEvents && events != null && usage != null) {
            fun cellOf(x: Int, y: Int): Offset? = if (x / MapFile.SIZE == usage.x && y / MapFile.SIZE == usage.y) Offset((x % MapFile.SIZE) * cell, (y % MapFile.SIZE) * cell) else null
            for (o in events.overworlds) cellOf(o.x, o.y)?.let { drawCircle(if (o.type == 1) Color(0xFFE53E3E) else Color(0xFF2B6CB0), cell * 0.32f, it + Offset(cell / 2, cell / 2)) }
            for (w in events.warps) cellOf(w.x, w.y)?.let { drawRect(Color(0xFF6B46C1), it + Offset(cell * 0.15f, cell * 0.15f), Size(cell * 0.7f, cell * 0.7f)) }
            for (t in events.triggers) cellOf(t.x, t.y)?.let { drawRect(Color(0xFF2F855A), it, Size(cell * t.width.coerceAtLeast(1), cell * t.height.coerceAtLeast(1)), style = Stroke(2f)) }
            for (s in events.spawnables) cellOf(s.x, s.y)?.let { drawCircle(Color(0xFF975A16), cell * 0.18f, it + Offset(cell / 2, cell / 2), style = Stroke(2f)) }
        }
        model.hover?.let { (x, y) -> drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell), style = Stroke(2f)) }
    }
}

@Composable
private fun MapLegend(model: MapEditorModel, map: MapFile) {
    val hover = model.hover
    androidx.compose.foundation.layout.Column(Modifier.width(576.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hover != null) {
            val (x, y) = hover
            val info = TileTypes.info(map.type(x, y))
            Text("Tile $x, $y", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(typeColor(info.id))
                Text("Type ${info.id}: ${info.label}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Collision 0x%02X%s".format(map.collision(x, y), if (Collision.isBlocked(map.collision(x, y))) " (blocked)" else ""), style = MaterialTheme.typography.bodySmall)
            if (info.surfable || info.encounters) Text(listOfNotNull("surfable".takeIf { info.surfable }, "wild encounters".takeIf { info.encounters }).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text("Point at a tile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val used = (0 until MapFile.SIZE * MapFile.SIZE).map { map.type(it % MapFile.SIZE, it / MapFile.SIZE) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8)
        Text("Most used types", style = MaterialTheme.typography.titleSmall)
        used.forEach { (t, n) ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(typeColor(t), 14.dp)
                Text("$n × ${TileTypes.info(t).label}", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Markers: blue NPC, red trainer, purple warp, green trigger, brown sign, orange building.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
