package io.github.romulofer.foxhg.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.OverworldIcon
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.event.MAP_SIZE
import io.github.romulofer.foxhg.core.data.event.OverworldEvent
import io.github.romulofer.foxhg.core.data.event.Spawnable
import io.github.romulofer.foxhg.core.data.event.Trigger
import io.github.romulofer.foxhg.core.data.event.Warp
import io.github.romulofer.foxhg.core.data.mapheader.MapHeader
import io.github.romulofer.foxhg.core.data.mapheader.MapHeaderTable
import io.github.romulofer.foxhg.core.project.RomProject

/** Event files with the maps that use them, so a file reads as "Route 29" instead of a number. */
class EventLabels(project: RomProject) {
    private val headers: List<MapHeader> = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
    private val names = project.names

    fun mapName(header: Int): String {
        val h = headers.getOrNull(header) ?: return "Header $header (not a map)"
        return names?.locations?.getOrNull(h.locationName)?.takeIf { it.isNotBlank() } ?: "Map"
    }

    /** Header numbers that load event file [file]. */
    fun usedBy(file: Int): List<Int> = headers.indices.filter { headers[it].eventFile == file }

    fun label(file: Int): String {
        val users = usedBy(file)
        val name = if (users.isEmpty()) "(unused)" else mapName(users.first()) + if (users.size > 1) " +${users.size - 1}" else ""
        return "%03d  %s".format(file, name)
    }
}

@Composable
fun EventEditor(model: RecordEditorModel<EventFile>, labels: EventLabels, modifier: Modifier = Modifier) {
    RecordListDetail(model, modifier, rowDetail = { i ->
        val count = model.records.getOrNull(i)?.count?.takeIf { n -> n > 0 }?.toString()
        listOfNotNull(labels.usedBy(i).firstOrNull()?.let { "hdr $it" }, count).joinToString(" · ").ifEmpty { null }
    }) { file ->
        val users = labels.usedBy(model.selectedIndex)
        EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)))
        if (users.isNotEmpty()) {
            Text(
                "Loaded by map header " + summarize(users.map { "$it (${labels.mapName(it)})" }),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        key(model.selectedIndex) {
            EventSection("Overworld objects", "NPCs, trainers and item balls", file.overworlds, { model.update { f -> f.copy(overworlds = it) } },
                summary = { "${if (it.isAlias) "Alias of" else "Object"} ${it.id}, sprite ${it.spriteEntry}, script ${it.script}" },
                create = { file.overworlds.lastOrNull()?.copy(id = (file.overworlds.maxOfOrNull { o -> o.id } ?: 0) + 1) ?: OverworldEvent(1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0) },
                leading = { OverworldIcon(it.spriteEntry, it.orientation, 32.dp) },
            ) { o, set -> OverworldForm(o, set) }
            EventSection("Warps", "Doors and exits to another map header", file.warps, { model.update { f -> f.copy(warps = it) } },
                summary = { "To header ${it.header} (${labels.mapName(it.header)}), anchor ${it.anchor}" },
                create = { file.warps.lastOrNull() ?: Warp(0, 0, 0, 0, 0) },
            ) { w, set -> WarpForm(w, labels, set) }
            EventSection("Triggers", "Scripts that run when the player steps on an area", file.triggers, { model.update { f -> f.copy(triggers = it) } },
                summary = { "Script ${it.script}" + if (it.variable != 0) " when var ${it.variable} is ${it.expectedValue}" else "" },
                create = { file.triggers.lastOrNull() ?: Trigger(0, 0, 0, 1, 1, 0, 0, 0) },
            ) { t, set -> TriggerForm(t, set) }
            EventSection("Signs, boards and hidden items", null, file.spawnables, { model.update { f -> f.copy(spawnables = it) } },
                summary = { sp -> "${Spawnable.TYPES.getOrElse(sp.type) { n -> "Type $n" }}, script ${sp.script}" },
                create = { file.spawnables.lastOrNull() ?: Spawnable(0, 0, 0, 0, 0, 0, 0, 0, 0) },
            ) { s, set -> SpawnableForm(s, set) }
        }
    }
}

/** A list of events as collapsible rows with add, duplicate and remove. */
@Composable
private fun <T> EventSection(
    title: String,
    subtitle: String?,
    items: List<T>,
    onChange: (List<T>) -> Unit,
    summary: (T) -> String,
    create: () -> T,
    leading: (@Composable (T) -> Unit)? = null,
    form: @Composable (T, (T) -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf<Int?>(null) }
    SectionCard(title, subtitle = subtitle, actions = { Chip(items.size.toString()) }) {
        if (items.isEmpty()) Text("None.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.forEachIndexed { i, item ->
            Row(Modifier.fillMaxWidth().clickable { open = if (open == i) null else i }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (open == i) "▾" else "▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("%02d".format(i), style = io.github.romulofer.foxhg.app.ui.Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                leading?.invoke(item)
                Text(summary(item), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { onChange(items + item); open = items.size }) { Text("Duplicate") }
                TextButton(onClick = { onChange(items.filterIndexed { k, _ -> k != i }); open = null }) { Text("Remove") }
            }
            if (open == i) Column(Modifier.padding(start = 24.dp, bottom = 8.dp)) { form(item) { changed -> onChange(items.toMutableList().also { it[i] = changed }) } }
        }
        Button(onClick = { onChange(items + create()); open = items.size }) { Text("Add") }
    }
}

@Composable
internal fun PositionFields(x: Int, y: Int, onX: (Int) -> Unit, onY: (Int) -> Unit) {
    NumberField("X", x, 0..65535, onX, Modifier.width(100.dp))
    NumberField("Y", y, 0..65535, onY, Modifier.width(100.dp))
    Chip("cell ${x / MAP_SIZE},${y / MAP_SIZE} tile ${x % MAP_SIZE},${y % MAP_SIZE}")
}

@Composable
internal fun OverworldForm(o: OverworldEvent, set: (OverworldEvent) -> Unit) = FieldFlow {
    NumberField("Object id", o.id, 0..65535, { set(o.copy(id = it)) })
    OverworldIcon(o.spriteEntry, o.orientation, 40.dp)
    NumberField("Sprite entry", o.spriteEntry, 0..65535, { set(o.copy(spriteEntry = it)) })
    NumberField("Script (65535 = alias)", o.script, 0..65535, { set(o.copy(script = it)) }, Modifier.width(170.dp))
    NumberField("Type", o.type, 0..65535, { set(o.copy(type = it)) })
    Chip(OverworldEvent.TYPES[o.type] ?: "Other")
    NumberField("Flag", o.flag, 0..65535, { set(o.copy(flag = it)) })
    NumberField("Movement", o.movement, 0..65535, { set(o.copy(movement = it)) })
    NumberField("Facing", o.orientation, 0..65535, { set(o.copy(orientation = it)) })
    NumberField("Sight range", o.sightRange, 0..65535, { set(o.copy(sightRange = it)) })
    NumberField("X range", o.xRange, 0..65535, { set(o.copy(xRange = it)) })
    NumberField("Y range", o.yRange, 0..65535, { set(o.copy(yRange = it)) })
    PositionFields(o.x, o.y, { set(o.copy(x = it)) }, { set(o.copy(y = it)) })
    NumberField("Height", o.z, Int.MIN_VALUE..Int.MAX_VALUE, { set(o.copy(z = it)) }, Modifier.width(130.dp))
    NumberField("Unknown 1", o.unknown1, 0..65535, { set(o.copy(unknown1 = it)) })
    NumberField("Unknown 2", o.unknown2, 0..65535, { set(o.copy(unknown2 = it)) })
}

@Composable
internal fun WarpForm(w: Warp, labels: EventLabels, set: (Warp) -> Unit) = FieldFlow {
    NumberField("Target header", w.header, 0..65535, { set(w.copy(header = it)) }, Modifier.width(120.dp))
    Chip(labels.mapName(w.header))
    NumberField("Anchor", w.anchor, 0..65535, { set(w.copy(anchor = it)) })
    PositionFields(w.x, w.y, { set(w.copy(x = it)) }, { set(w.copy(y = it)) })
    NumberField("Height", w.height.toInt(), Int.MIN_VALUE..Int.MAX_VALUE, { set(w.copy(height = it.toLong() and 0xFFFFFFFFL)) }, Modifier.width(130.dp))
}

@Composable
internal fun TriggerForm(t: Trigger, set: (Trigger) -> Unit) = FieldFlow {
    NumberField("Script", t.script, 0..65535, { set(t.copy(script = it)) })
    NumberField("Variable (0 = always)", t.variable, 0..65535, { set(t.copy(variable = it)) }, Modifier.width(170.dp))
    NumberField("Expected value", t.expectedValue, 0..65535, { set(t.copy(expectedValue = it)) }, Modifier.width(130.dp))
    PositionFields(t.x, t.y, { set(t.copy(x = it)) }, { set(t.copy(y = it)) })
    NumberField("Width", t.width, 0..65535, { set(t.copy(width = it)) })
    NumberField("Height", t.height, 0..65535, { set(t.copy(height = it)) })
    NumberField("Z", t.z, 0..65535, { set(t.copy(z = it)) })
}

@Composable
internal fun SpawnableForm(s: Spawnable, set: (Spawnable) -> Unit) = FieldFlow {
    PickerField("Type", s.type.coerceAtMost(Spawnable.TYPES.lastIndex), Spawnable.TYPES, { set(s.copy(type = it)) })
    NumberField("Script", s.script, 0..65535, { set(s.copy(script = it)) })
    NumberField("Facing", s.direction, 0..65535, { set(s.copy(direction = it)) })
    PositionFields(s.x, s.y, { set(s.copy(x = it)) }, { set(s.copy(y = it)) })
    NumberField("Height", s.z, Int.MIN_VALUE..Int.MAX_VALUE, { set(s.copy(z = it)) }, Modifier.width(130.dp))
    NumberField("Unknown 2", s.unknown2, 0..65535, { set(s.copy(unknown2 = it)) })
    NumberField("Unknown 4", s.unknown4, 0..65535, { set(s.copy(unknown4 = it)) })
    NumberField("Unknown 5", s.unknown5, 0..65535, { set(s.copy(unknown5 = it)) })
}
