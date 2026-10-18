package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.Swatch
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.data.event.EventKind
import io.github.legendaryredfox.foxhg.core.data.event.OverworldEvent
import io.github.legendaryredfox.foxhg.core.data.event.Spawnable
import io.github.legendaryredfox.foxhg.core.data.event.Trigger
import io.github.legendaryredfox.foxhg.core.data.event.Warp
import io.github.legendaryredfox.foxhg.core.data.map.Building
import io.github.legendaryredfox.foxhg.core.graphics.Camera3d
import io.github.legendaryredfox.foxhg.core.data.map.Collision
import io.github.legendaryredfox.foxhg.core.data.map.FixedPos
import io.github.legendaryredfox.foxhg.core.data.map.MapFile
import io.github.legendaryredfox.foxhg.core.data.map.MapUsage
import io.github.legendaryredfox.foxhg.core.data.map.MapUsages
import io.github.legendaryredfox.foxhg.core.data.map.TileTypes
import io.github.legendaryredfox.foxhg.core.project.RomProject

enum class MapLayer(val label: String) { TYPES("Tile types"), COLLISION("Collision"), BOTH("Types and collision") }
enum class MapTool(val label: String) { PAINT("Paint"), FILL("Fill"), PICK("Pick"), EVENTS("Edit events") }

/** State of the Maps editor: the map files are a [RecordEditorModel], the rest is how they are shown and painted. */
class MapEditorModel(project: RomProject, onChanged: () -> Unit = {}) {
    private val usages = MapUsages(project)
    private val names = project.names
    private val events = RecordEditorModel(project, EventFile.SPEC, { "%03d".format(it) }, onChanged)
    val eventLabels = EventLabels(project)
    val pictures = MapPictures(project)

    val maps = RecordEditorModel(project, MapFile.SPEC, ::label, onChanged)

    var layer by mutableStateOf(MapLayer.BOTH)
    /** FOXHG_MAP_TOOL=EVENTS starts with that tool (for screenshots). */
    var tool by mutableStateOf(System.getenv("FOXHG_MAP_TOOL")?.let { name -> MapTool.entries.firstOrNull { it.name == name } } ?: MapTool.PAINT)
    var brushType by mutableStateOf(2)
    var brushCollision by mutableStateOf(0)
    var paintType by mutableStateOf(true)
    var paintCollision by mutableStateOf(false)
    var showEvents by mutableStateOf(true)
    var showBuildings by mutableStateOf(true)
    var usageIndex by mutableStateOf(0)
    var hover: Pair<Int, Int>? by mutableStateOf(null)

    /** The building selected in the 3D view or the list, by its place in the map's building list. */
    var selectedBuilding: Int? by mutableStateOf(null)

    /** "Select" picks buildings by clicking them in the 3D view; "Move here" puts the selected one where you click. */
    var moveBuildings by mutableStateOf(false)

    /** What the 3D view draws over the scene. */
    /** FOXHG_MAP_3D=collision starts with the collision overlay on (for screenshots). */
    var show3dCollision by mutableStateOf(System.getenv("FOXHG_MAP_3D")?.contains("collision") == true)
    var show3dEvents by mutableStateOf(true)
    var show3dSurface by mutableStateOf(System.getenv("FOXHG_MAP_3D")?.contains("surface") == true)

    /** Where the 3D view looks from; back to the default when another map is selected. */
    var camera by mutableStateOf(Camera3d())

    private fun buildings(): List<Building> = maps.selected?.buildings ?: emptyList()

    private fun setBuildings(list: List<Building>) = maps.update { it.withBuildings(list) }

    fun updateBuilding(index: Int, change: (Building) -> Building) {
        val list = buildings()
        if (index in list.indices) setBuildings(list.toMutableList().also { it[index] = change(list[index]) })
    }

    /** A new building of [modelId] at ([x], [z]) tiles from the map's centre, standing on the ground (y = 0); it is selected. */
    fun addBuilding(modelId: Int, x: Double, z: Double) {
        val list = buildings()
        val template = list.lastOrNull()
        val added = Building(
            modelId.toLong(), FixedPos.of(x), FixedPos.of(template?.y?.value ?: 0.0), FixedPos.of(z),
            0, 0, 0, 16, 16, 16, List(8) { 0 },
        )
        setBuildings(list + added)
        selectedBuilding = list.size
    }

    fun duplicateBuilding(index: Int) {
        val list = buildings()
        val original = list.getOrNull(index) ?: return
        setBuildings(list + original.copy(x = FixedPos.of(original.x.value + 1), z = FixedPos.of(original.z.value + 1)))
        selectedBuilding = list.size
    }

    fun removeBuilding(index: Int) {
        val list = buildings()
        if (index !in list.indices) return
        setBuildings(list.filterIndexed { i, _ -> i != index })
        selectedBuilding = null
    }

    fun usagesOf(map: Int): List<MapUsage> = usages.of(map)

    fun usage(map: Int): MapUsage? = usagesOf(map).getOrNull(usageIndex.coerceAtMost((usagesOf(map).size - 1).coerceAtLeast(0)))

    fun headerName(header: Int): String =
        usages.headers.getOrNull(header)?.let { names?.locations?.getOrNull(it.locationName) }?.takeIf { it.isNotBlank() } ?: "Header $header"

    fun label(map: Int): String {
        val first = usages.of(map).firstOrNull()
        return "%03d  %s".format(map, first?.let { headerName(it.header) } ?: "(not in a matrix)")
    }

    private fun eventFileOf(usage: MapUsage?): Int? = usage?.let { usages.headers.getOrNull(it.header)?.eventFile }

    fun eventsOf(usage: MapUsage?): EventFile? = eventFileOf(usage)?.let { events.records.getOrNull(it) }

    /** The event being edited: its kind and its place in the list of that kind. */
    var selectedEvent: Pair<EventKind, Int>? by mutableStateOf(null)
    private var dragging by mutableStateOf(false)

    private fun change(usage: MapUsage?, transform: (EventFile) -> EventFile) {
        val file = eventFileOf(usage) ?: return
        if (file !in 0 until events.recordCount) return
        events.select(file)
        events.update(transform)
    }

    private fun global(usage: MapUsage, tx: Int, ty: Int) = usage.x * MapFile.SIZE + tx to usage.y * MapFile.SIZE + ty

    /** Selects the event on the tile, or the next one when several share it and one is selected already; starts a drag. */
    fun pressEvent(usage: MapUsage?, tx: Int, ty: Int) {
        val file = eventsOf(usage) ?: return
        val (gx, gy) = global(usage ?: return, tx, ty)
        val here = file.at(gx, gy)
        val current = here.indexOf(selectedEvent)
        selectedEvent = when {
            here.isEmpty() -> null
            current >= 0 && here.size > 1 -> here[(current + 1) % here.size]
            else -> here.first()
        }
        dragging = selectedEvent != null
    }

    fun dragEvent(usage: MapUsage?, tx: Int, ty: Int) {
        val (kind, index) = selectedEvent ?: return
        if (!dragging || usage == null) return
        val (gx, gy) = global(usage, tx, ty)
        if (eventsOf(usage)?.positionOf(kind, index) == gx to gy) return
        change(usage) { it.moved(kind, index, gx, gy) }
    }

    fun endDrag() { dragging = false }

    /** Adds an event of [kind] on the tile (or in the middle of the cell) and selects it. */
    fun addEvent(usage: MapUsage?, kind: EventKind, tile: Pair<Int, Int>?) {
        if (usage == null) return
        val (tx, ty) = tile ?: (16 to 16)
        val (gx, gy) = global(usage, tx, ty)
        change(usage) { it.added(kind, gx, gy) }
        selectedEvent = kind to ((eventsOf(usage)?.count(kind) ?: 1) - 1)
    }

    fun removeSelected(usage: MapUsage?) {
        val (kind, index) = selectedEvent ?: return
        change(usage) { it.removed(kind, index) }
        selectedEvent = null
    }

    fun duplicateSelected(usage: MapUsage?) {
        val (kind, index) = selectedEvent ?: return
        change(usage) { it.duplicated(kind, index) }
        selectedEvent = kind to ((eventsOf(usage)?.count(kind) ?: 1) - 1)
    }

    fun setObject(usage: MapUsage?, o: OverworldEvent) = selectedEvent?.let { (_, i) -> change(usage) { f -> f.copy(overworlds = f.overworlds.mapIndexed { k, e -> if (k == i) o else e }) } }
    fun setWarp(usage: MapUsage?, w: Warp) = selectedEvent?.let { (_, i) -> change(usage) { f -> f.copy(warps = f.warps.mapIndexed { k, e -> if (k == i) w else e }) } }
    fun setTrigger(usage: MapUsage?, t: Trigger) = selectedEvent?.let { (_, i) -> change(usage) { f -> f.copy(triggers = f.triggers.mapIndexed { k, e -> if (k == i) t else e }) } }
    fun setSign(usage: MapUsage?, s: Spawnable) = selectedEvent?.let { (_, i) -> change(usage) { f -> f.copy(spawnables = f.spawnables.mapIndexed { k, e -> if (k == i) s else e }) } }

    fun isEventFileEdited(usage: MapUsage?): Boolean = eventFileOf(usage)?.let { events.isEdited(it) } == true

    fun select(map: Int) {
        usageIndex = 0
        selectedBuilding = null
        camera = Camera3d()
        selectedEvent = null
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
            MapTool.EVENTS -> {}
        }
    }
}

@Composable
fun MapEditor(model: MapEditorModel, modifier: Modifier = Modifier) {
    RecordListDetail(
        model.maps, modifier,
        rowDetail = { model.usagesOf(it).size.takeIf { n -> n > 1 }?.let { n -> "×$n" } },
        onSelect = model::select,
        leading = { i -> AsyncMapPicture(i) { model.pictures.ofMap(i) } },
    ) { map ->
        val index = model.maps.selectedIndex
        EditorHeader(index.toString(), nameOf(model.maps.label(index)), trailing = { AsyncMapPicture(index, 96.dp) { model.pictures.ofMap(index) } })
        val usages = model.usagesOf(index)
        val usage = model.usage(index)
        if (usages.size > 1) {
            PickerField(stringResource(Res.string.map_used_as), model.usageIndex.coerceAtMost(usages.size - 1), usages.map { "matrix ${it.matrix} cell ${it.x},${it.y} (${model.headerName(it.header)})" }, { model.usageIndex = it }, Modifier.width(420.dp))
        } else if (usage != null) {
            Text(stringResource(Res.string.map_matrix_x_cell_x_x_map_header_x, usage.matrix, usage.x, usage.y, usage.header), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(Res.string.map_tiles), subtitle = stringResource(Res.string.map_left_click_paints_right_click_pick)) {
            ToolbarRow(stringResource(Res.string.trainer_show)) { MapLayer.entries.forEach { l -> ToggleChip(l.label, model.layer == l, { model.layer = l }) } }
            ToolbarRow(stringResource(Res.string.map_tool)) { MapTool.entries.forEach { t -> ToggleChip(t.label, model.tool == t, { model.tool = t }) } }
            ToolbarRow(stringResource(Res.string.editor_overlays)) {
                ToggleChip(stringResource(Res.string.map_show_events), model.showEvents, { model.showEvents = it })
                ToggleChip(stringResource(Res.string.map_show_buildings), model.showBuildings, { model.showBuildings = it })
            }
            ToolbarRow(stringResource(Res.string.map_brush_tile_type)) {
                ToggleChip(stringResource(Res.string.map_paint_type), model.paintType, { model.paintType = it })
                PickerField(stringResource(Res.string.move_type), model.brushType, TileTypes.all.map { "${it.id}  ${it.label}" }, { model.brushType = it }, Modifier.width(280.dp), leading = { Swatch(typeColor(it)) })
            }
            ToolbarRow(stringResource(Res.string.map_brush_collision)) {
                ToggleChip(stringResource(Res.string.map_paint_collision), model.paintCollision, { model.paintCollision = it })
                NumberField(stringResource(Res.string.map_collision), model.brushCollision, 0..255, { model.brushCollision = it }, Modifier.width(110.dp))
                ToggleChip(stringResource(Res.string.map_walk), model.brushCollision == 0, { model.brushCollision = 0 })
                ToggleChip(stringResource(Res.string.map_block), model.brushCollision == Collision.BLOCKED, { model.brushCollision = Collision.BLOCKED })
            }
            MapCanvas(model, map, usage)
            MapLegend(model, map)
        }
        if (model.tool == MapTool.EVENTS) EventPanel(model, usage)
        // Whether the map has a scene is found out in the background: it decodes the terrain model the first time.
        val hasScene by androidx.compose.runtime.produceState(false, index) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { model.pictures.hasScene(index) } }
        if (hasScene) {
            SectionCard(stringResource(Res.string.map_3d_view), subtitle = stringResource(Res.string.map_the_map_as_the_game_draws_it_witho)) { Map3dView(model, index) }
        }
        BuildingCard(model, map)
    }
}

/** One captioned row of the map toolbar, so choices of the same kind sit together. */
@Composable
private fun ToolbarRow(caption: String, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        Text(caption, Modifier.width(120.dp).padding(bottom = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FieldFlow(Modifier.weight(1f)) { content() }
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
    val icons = io.github.legendaryredfox.foxhg.app.ui.LocalIcons.current
    Canvas(
        Modifier.size((CELL * MapFile.SIZE).dp).clipToBounds().testTag("map-canvas").pointerInput(model, map, usage) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val p = event.changes.first().position
                    val cell = (p.x / (size.width / MapFile.SIZE.toFloat())).toInt() to (p.y / (size.height / MapFile.SIZE.toFloat())).toInt()
                    val inside = cell.first in 0 until MapFile.SIZE && cell.second in 0 until MapFile.SIZE
                    model.hover = if (inside) cell else null
                    if (!inside) continue
                    when (event.type) {
                        PointerEventType.Press -> when {
                            model.tool == MapTool.EVENTS -> if (event.buttons.isPrimaryPressed) model.pressEvent(usage, cell.first, cell.second)
                            event.buttons.isSecondaryPressed -> { val keep = model.tool; model.tool = MapTool.PICK; model.apply(cell.first, cell.second); model.tool = keep }
                            event.buttons.isPrimaryPressed -> model.apply(cell.first, cell.second)
                        }
                        PointerEventType.Move -> if (event.buttons.isPrimaryPressed) {
                            if (model.tool == MapTool.EVENTS) model.dragEvent(usage, cell.first, cell.second) else if (model.tool == MapTool.PAINT) model.apply(cell.first, cell.second)
                        }
                        PointerEventType.Release -> model.endDrag()
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
            for (o in events.overworlds) cellOf(o.x, o.y)?.let {
                val sprite = icons?.overworld(o.spriteEntry, o.orientation)
                val tint = if (o.type == 1) Color(0xFFE53E3E) else Color(0xFF2B6CB0)
                if (sprite == null) drawCircle(tint, cell * 0.32f, it + Offset(cell / 2, cell / 2))
                else {
                    // A 32x32 sprite spans two cells, with the feet on the cell's lower middle; a ring keeps trainers recognisable.
                    drawCircle(tint, cell * 0.42f, it + Offset(cell / 2, cell / 2), style = Stroke(1.5f))
                    drawImage(sprite, IntOffset.Zero, IntSize(sprite.width, sprite.height), IntOffset((it.x - cell / 2).toInt(), (it.y - cell * 1.1f).toInt()), IntSize((cell * 2).toInt(), (cell * 2).toInt()), filterQuality = FilterQuality.None)
                }
            }
            for (w in events.warps) cellOf(w.x, w.y)?.let { drawRect(Color(0xFF6B46C1), it + Offset(cell * 0.15f, cell * 0.15f), Size(cell * 0.7f, cell * 0.7f)) }
            for (t in events.triggers) cellOf(t.x, t.y)?.let { drawRect(Color(0xFF2F855A), it, Size(cell * t.width.coerceAtLeast(1), cell * t.height.coerceAtLeast(1)), style = Stroke(2f)) }
            for (s in events.spawnables) cellOf(s.x, s.y)?.let { drawCircle(Color(0xFF975A16), cell * 0.18f, it + Offset(cell / 2, cell / 2), style = Stroke(2f)) }
        }
        model.selectedEvent?.let { (kind, index) ->
            if (events != null && usage != null) events.positionOf(kind, index)?.let { (ex, ey) ->
                if (ex / MapFile.SIZE == usage.x && ey / MapFile.SIZE == usage.y) {
                    val w = if (kind == EventKind.TRIGGER) events.triggers[index].width.coerceAtLeast(1) else 1
                    val h = if (kind == EventKind.TRIGGER) events.triggers[index].height.coerceAtLeast(1) else 1
                    drawRect(Color(0xFFFFC107), Offset((ex % MapFile.SIZE) * cell, (ey % MapFile.SIZE) * cell), Size(cell * w, cell * h), style = Stroke(3f))
                }
            }
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
            Text(stringResource(Res.string.map_tile_x_x, x, y), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(typeColor(info.id))
                Text(stringResource(Res.string.map_type_x_x, info.id, info.label), style = MaterialTheme.typography.bodySmall)
            }
            Text("Collision 0x%02X%s".format(map.collision(x, y), if (Collision.isBlocked(map.collision(x, y))) " (blocked)" else ""), style = MaterialTheme.typography.bodySmall)
            if (info.surfable || info.encounters) Text(listOfNotNull("surfable".takeIf { info.surfable }, "wild encounters".takeIf { info.encounters }).joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Text(stringResource(Res.string.map_point_at_a_tile), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val used = (0 until MapFile.SIZE * MapFile.SIZE).map { map.type(it % MapFile.SIZE, it / MapFile.SIZE) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8)
        Text(stringResource(Res.string.map_most_used_types), style = MaterialTheme.typography.titleSmall)
        used.forEach { (t, n) ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(typeColor(t), 14.dp)
                Text(stringResource(Res.string.matrix_x_x, n, TileTypes.info(t).label), style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(stringResource(Res.string.map_markers_blue_npc_red_trainer_purpl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** Adding, editing, copying and removing the events of the map's cell; shown while the Events tool is on. */
@Composable
private fun EventPanel(model: MapEditorModel, usage: MapUsage?) {
    val file = model.eventsOf(usage)
    SectionCard(
        stringResource(Res.string.editor_events),
        subtitle = if (file == null) stringResource(Res.string.map_this_map_has_no_event_file) else stringResource(Res.string.map_click_a_marker_to_select_it_and_dr),
        actions = { if (model.isEventFileEdited(usage)) Chip(stringResource(Res.string.map_edited), emphasis = true) },
    ) {
        if (file == null || usage == null) return@SectionCard
        FieldFlow {
            EventKind.entries.forEach { kind -> OutlinedButton(onClick = { model.addEvent(usage, kind, model.hover) }) { Text(stringResource(Res.string.map_add_x, kind.label.lowercase())) } }
        }
        val selected = model.selectedEvent
        if (selected == null) {
            Text(stringResource(Res.string.map_no_event_selected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val (kind, index) = selected
        val (gx, gy) = file.positionOf(kind, index) ?: return@SectionCard
        FieldFlow {
            Chip(stringResource(Res.string.map_x_x_of_x, kind.label, index + 1, file.count(kind)), emphasis = true)
            Text(stringResource(Res.string.map_tile_x_x_2, gx % MapFile.SIZE, gy % MapFile.SIZE), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { model.duplicateSelected(usage) }) { Text(stringResource(Res.string.map_duplicate)) }
            OutlinedButton(onClick = { model.removeSelected(usage) }) { Text(stringResource(Res.string.learnset_remove)) }
        }
        when (kind) {
            EventKind.OVERWORLD -> file.overworlds.getOrNull(index)?.let { OverworldForm(it) { o -> model.setObject(usage, o) } }
            EventKind.WARP -> file.warps.getOrNull(index)?.let { WarpForm(it, model.eventLabels) { w -> model.setWarp(usage, w) } }
            EventKind.TRIGGER -> file.triggers.getOrNull(index)?.let { TriggerForm(it) { t -> model.setTrigger(usage, t) } }
            EventKind.SIGN -> file.spawnables.getOrNull(index)?.let { SpawnableForm(it) { s -> model.setSign(usage, s) } }
        }
    }
}
