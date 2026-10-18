package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.data.townmap.TownMap
import io.github.legendaryredfox.foxhg.core.data.townmap.TownMapSelection
import io.github.legendaryredfox.foxhg.core.data.townmap.TownMapSpot
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * The two PokeGear map tables of overlay 101: the towns that are drawn red once visited (and a gray block before), and the orange selection
 * blocks with the fly spot flag and the text blurb. Both have a fixed length. The list shows the towns first, then the selections.
 */
class TownMapEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    private val original: TownMap? = (TownMap.load(project) as? FoxResult.Ok)?.value
    var table: TownMap? by mutableStateOf(original)
        private set
    var error: String? by mutableStateOf(if (original == null) (TownMap.load(project) as? FoxResult.Err)?.message else null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(0)
        private set

    val mapLabels: List<String> = run {
        val headers = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
        val locations = project.names?.locations ?: emptyList()
        headers.mapIndexed { i, h -> "%03d  %s".format(i, locations.getOrNull(h.locationName)?.takeIf { it.isNotBlank() } ?: "Map") }
    }

    private fun mapName(header: Int): String = mapLabels.getOrNull(header)?.drop(5) ?: "Header $header"

    val townCount: Int get() = table?.spots?.size ?: 0
    val size: Int get() = townCount + (table?.selections?.size ?: 0)

    fun label(position: Int): String {
        val t = table ?: return "%03d".format(position)
        return if (position < townCount) "Town %02d  %s".format(position, mapName(t.spots[position].mapHeader))
        else "Spot %02d  %s".format(position - townCount, mapName(t.selections[position - townCount].mapHeader))
    }

    fun rows(): List<Int> {
        val q = search.trim()
        return (0 until size).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    fun isEdited(position: Int): Boolean {
        val t = table ?: return false
        val o = original ?: return false
        return if (position < townCount) o.spots[position] != t.spots[position] else o.selections[position - townCount] != t.selections[position - townCount]
    }

    fun select(position: Int) { if (position in 0 until size) selectedIndex = position }

    private fun apply(updated: TownMap) {
        when (val saved = TownMap.save(project, updated)) {
            is FoxResult.Ok -> { table = updated; error = null; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }

    fun updateSpot(change: (TownMapSpot) -> TownMapSpot) {
        val t = table ?: return
        if (selectedIndex >= townCount) return
        apply(t.withSpot(selectedIndex, change(t.spots[selectedIndex])))
    }

    fun updateSelection(change: (TownMapSelection) -> TownMapSelection) {
        val t = table ?: return
        val i = selectedIndex - townCount
        if (i !in t.selections.indices) return
        apply(t.withSelection(i, change(t.selections[i])))
    }
}

@Composable
fun TownMapEditor(model: TownMapEditorModel, modifier: Modifier = Modifier) {
    val table = model.table
    if (table == null) {
        Text(model.error ?: "", color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    Row(modifier.fillMaxSize()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedIndex,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = "Search towns and spots…",
            label = model::label,
            onSelect = model::select,
            isEdited = model::isEdited,
            detail = { if (it < model.townCount) null else table.selections.getOrNull(it - model.townCount)?.let { s -> if (s.flySpot != 0) "fly" else null } },
        )
        DetailPane {
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val position = model.selectedIndex
            if (position < model.townCount) {
                val s = table.spots[position]
                EditorHeader("T$position", model.label(position).drop(9))
                SectionCard("Town", subtitle = "Drawn red once visited, replaced by a gray block before. Positions are 8x8 tiles in the map picture") {
                    FieldFlow {
                        PickerField("Map", s.mapHeader, model.mapLabels, { v -> model.updateSpot { it.copy(mapHeader = v) } }, Modifier.width(260.dp))
                        PickerField("Gate map", s.gateHeader, model.mapLabels, { v -> model.updateSpot { it.copy(gateHeader = v) } }, Modifier.width(260.dp))
                        NumberField("Entry", s.entry, 0..255, { v -> model.updateSpot { it.copy(entry = v) } }, Modifier.width(90.dp))
                        NumberField("Visited entry", s.visitedEntry, 0..255, { v -> model.updateSpot { it.copy(visitedEntry = v) } }, Modifier.width(120.dp))
                    }
                    FieldFlow {
                        NumberField("Red X", s.redX, 0..255, { v -> model.updateSpot { it.copy(redX = v) } }, Modifier.width(90.dp))
                        NumberField("Red Y", s.redY, 0..255, { v -> model.updateSpot { it.copy(redY = v) } }, Modifier.width(90.dp))
                        NumberField("Gray X", s.grayX, 0..255, { v -> model.updateSpot { it.copy(grayX = v) } }, Modifier.width(90.dp))
                        NumberField("Gray Y", s.grayY, 0..255, { v -> model.updateSpot { it.copy(grayY = v) } }, Modifier.width(90.dp))
                    }
                    FieldFlow {
                        NumberField("Town width", s.townDimX, 0..15, { v -> model.updateSpot { it.copy(townDimX = v) } }, Modifier.width(110.dp))
                        NumberField("Town height", s.townDimY, 0..15, { v -> model.updateSpot { it.copy(townDimY = v) } }, Modifier.width(110.dp))
                        NumberField("Gray block width", s.replacementDimX, 0..15, { v -> model.updateSpot { it.copy(replacementDimX = v) } }, Modifier.width(140.dp))
                        NumberField("Gray block height", s.replacementDimY, 0..15, { v -> model.updateSpot { it.copy(replacementDimY = v) } }, Modifier.width(140.dp))
                        NumberField("Town offset X", s.offsetX, 0..15, { v -> model.updateSpot { it.copy(offsetX = v) } }, Modifier.width(120.dp))
                        NumberField("Town offset Y", s.offsetY, 0..15, { v -> model.updateSpot { it.copy(offsetY = v) } }, Modifier.width(120.dp))
                    }
                    Text(
                        "The red position is stored one tile off: the picture position is X - 1 and Y + 1. The table has a fixed length of ${model.townCount} entries.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val s = table.selections[position - model.townCount]
                EditorHeader("S${position - model.townCount}", model.label(position).drop(9))
                SectionCard("Selection block", subtitle = "The orange block shown when the cursor is on a town or route") {
                    FieldFlow {
                        PickerField("Map", s.mapHeader, model.mapLabels, { v -> model.updateSelection { it.copy(mapHeader = v) } }, Modifier.width(260.dp))
                        NumberField("Text entry", s.textEntry, 0..255, { v -> model.updateSelection { it.copy(textEntry = v) } }, Modifier.width(110.dp))
                        NumberField("Fly spot", s.flySpot, 0..255, { v -> model.updateSelection { it.copy(flySpot = v) } }, Modifier.width(100.dp))
                        NumberField("Flags", s.flags, 0..255, { v -> model.updateSelection { it.copy(flags = v) } }, Modifier.width(90.dp))
                    }
                    FieldFlow {
                        NumberField("PokéGear X", s.baseX, 0..255, { v -> model.updateSelection { it.copy(baseX = v) } }, Modifier.width(110.dp))
                        NumberField("PokéGear Y", s.baseY, 0..255, { v -> model.updateSelection { it.copy(baseY = v) } }, Modifier.width(110.dp))
                        NumberField("Width", s.dimX, 0..15, { v -> model.updateSelection { it.copy(dimX = v) } }, Modifier.width(90.dp))
                        NumberField("Height", s.dimY, 0..15, { v -> model.updateSelection { it.copy(dimY = v) } }, Modifier.width(90.dp))
                    }
                    FieldFlow {
                        NumberField("Picture X", s.orangeX, 0..255, { v -> model.updateSelection { it.copy(orangeX = v) } }, Modifier.width(100.dp))
                        NumberField("Picture Y", s.orangeY, 0..255, { v -> model.updateSelection { it.copy(orangeY = v) } }, Modifier.width(100.dp))
                        NumberField("Picture width", s.orangeDimX, 0..255, { v -> model.updateSelection { it.copy(orangeDimX = v) } }, Modifier.width(120.dp))
                        NumberField("Picture height", s.orangeDimY, 0..255, { v -> model.updateSelection { it.copy(orangeDimY = v) } }, Modifier.width(120.dp))
                    }
                    Text(
                        "The text entry is the blurb in text bank 273. A non-zero fly spot marks a town the player can fly to. What the flags do is not documented, so they are kept as they are unless you change them. The table has a fixed length of ${table.selections.size} entries.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
