package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
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
            placeholder = stringResource(Res.string.town_map_search_towns_and_spots),
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
                SectionCard(stringResource(Res.string.fly_town), subtitle = stringResource(Res.string.town_map_drawn_red_once_visited_replaced_by)) {
                    FieldFlow {
                        PickerField(stringResource(Res.string.new_save_map), s.mapHeader, model.mapLabels, { v -> model.updateSpot { it.copy(mapHeader = v) } }, Modifier.width(260.dp))
                        PickerField(stringResource(Res.string.town_map_gate_map), s.gateHeader, model.mapLabels, { v -> model.updateSpot { it.copy(gateHeader = v) } }, Modifier.width(260.dp))
                        NumberField(stringResource(Res.string.town_map_entry), s.entry, 0..255, { v -> model.updateSpot { it.copy(entry = v) } }, Modifier.width(90.dp))
                        NumberField(stringResource(Res.string.town_map_visited_entry), s.visitedEntry, 0..255, { v -> model.updateSpot { it.copy(visitedEntry = v) } }, Modifier.width(120.dp))
                    }
                    FieldFlow {
                        NumberField(stringResource(Res.string.town_map_red_x), s.redX, 0..255, { v -> model.updateSpot { it.copy(redX = v) } }, Modifier.width(90.dp))
                        NumberField(stringResource(Res.string.town_map_red_y), s.redY, 0..255, { v -> model.updateSpot { it.copy(redY = v) } }, Modifier.width(90.dp))
                        NumberField(stringResource(Res.string.town_map_gray_x), s.grayX, 0..255, { v -> model.updateSpot { it.copy(grayX = v) } }, Modifier.width(90.dp))
                        NumberField(stringResource(Res.string.town_map_gray_y), s.grayY, 0..255, { v -> model.updateSpot { it.copy(grayY = v) } }, Modifier.width(90.dp))
                    }
                    FieldFlow {
                        NumberField(stringResource(Res.string.town_map_town_width), s.townDimX, 0..15, { v -> model.updateSpot { it.copy(townDimX = v) } }, Modifier.width(110.dp))
                        NumberField(stringResource(Res.string.town_map_town_height), s.townDimY, 0..15, { v -> model.updateSpot { it.copy(townDimY = v) } }, Modifier.width(110.dp))
                        NumberField(stringResource(Res.string.town_map_gray_block_width), s.replacementDimX, 0..15, { v -> model.updateSpot { it.copy(replacementDimX = v) } }, Modifier.width(140.dp))
                        NumberField(stringResource(Res.string.town_map_gray_block_height), s.replacementDimY, 0..15, { v -> model.updateSpot { it.copy(replacementDimY = v) } }, Modifier.width(140.dp))
                        NumberField(stringResource(Res.string.town_map_town_offset_x), s.offsetX, 0..15, { v -> model.updateSpot { it.copy(offsetX = v) } }, Modifier.width(120.dp))
                        NumberField(stringResource(Res.string.town_map_town_offset_y), s.offsetY, 0..15, { v -> model.updateSpot { it.copy(offsetY = v) } }, Modifier.width(120.dp))
                    }
                    Text(
                        stringResource(Res.string.town_map_the_red_position_is_stored_one_til, model.townCount),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val s = table.selections[position - model.townCount]
                EditorHeader("S${position - model.townCount}", model.label(position).drop(9))
                SectionCard(stringResource(Res.string.town_map_selection_block), subtitle = stringResource(Res.string.town_map_the_orange_block_shown_when_the_cu)) {
                    FieldFlow {
                        PickerField(stringResource(Res.string.new_save_map), s.mapHeader, model.mapLabels, { v -> model.updateSelection { it.copy(mapHeader = v) } }, Modifier.width(260.dp))
                        NumberField(stringResource(Res.string.town_map_text_entry), s.textEntry, 0..255, { v -> model.updateSelection { it.copy(textEntry = v) } }, Modifier.width(110.dp))
                        NumberField(stringResource(Res.string.town_map_fly_spot), s.flySpot, 0..255, { v -> model.updateSelection { it.copy(flySpot = v) } }, Modifier.width(100.dp))
                        NumberField(stringResource(Res.string.move_flags), s.flags, 0..255, { v -> model.updateSelection { it.copy(flags = v) } }, Modifier.width(90.dp))
                    }
                    FieldFlow {
                        NumberField(stringResource(Res.string.town_map_pok_gear_x), s.baseX, 0..255, { v -> model.updateSelection { it.copy(baseX = v) } }, Modifier.width(110.dp))
                        NumberField(stringResource(Res.string.town_map_pok_gear_y), s.baseY, 0..255, { v -> model.updateSelection { it.copy(baseY = v) } }, Modifier.width(110.dp))
                        NumberField(stringResource(Res.string.town_map_width), s.dimX, 0..15, { v -> model.updateSelection { it.copy(dimX = v) } }, Modifier.width(90.dp))
                        NumberField(stringResource(Res.string.town_map_height), s.dimY, 0..15, { v -> model.updateSelection { it.copy(dimY = v) } }, Modifier.width(90.dp))
                    }
                    FieldFlow {
                        NumberField(stringResource(Res.string.town_map_picture_x), s.orangeX, 0..255, { v -> model.updateSelection { it.copy(orangeX = v) } }, Modifier.width(100.dp))
                        NumberField(stringResource(Res.string.town_map_picture_y), s.orangeY, 0..255, { v -> model.updateSelection { it.copy(orangeY = v) } }, Modifier.width(100.dp))
                        NumberField(stringResource(Res.string.town_map_picture_width), s.orangeDimX, 0..255, { v -> model.updateSelection { it.copy(orangeDimX = v) } }, Modifier.width(120.dp))
                        NumberField(stringResource(Res.string.town_map_picture_height), s.orangeDimY, 0..255, { v -> model.updateSelection { it.copy(orangeDimY = v) } }, Modifier.width(120.dp))
                    }
                    Text(
                        stringResource(Res.string.town_map_the_text_entry_is_the_blurb_in_tex, table.selections.size),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
