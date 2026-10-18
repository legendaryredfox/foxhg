package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
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
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.fly.FlyEntry
import io.github.legendaryredfox.foxhg.core.data.fly.FlyTable
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The fly table of the ARM9: where Fly lands, where a blackout sends the player and which spot unlocks each town. The table has a fixed length. */
class FlyEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    private val original: FlyTable? = (FlyTable.load(project) as? FoxResult.Ok)?.value
    var table: FlyTable? by mutableStateOf(original)
        private set
    var error: String? by mutableStateOf(if (original == null) (FlyTable.load(project) as? FoxResult.Err)?.message else null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(0)
        private set

    /** One label per map header ("060  New Bark Town"), for the map pickers. */
    val mapLabels: List<String> = run {
        val headers = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
        val locations = project.names?.locations ?: emptyList()
        headers.mapIndexed { i, h -> "%03d  %s".format(i, locations.getOrNull(h.locationName)?.takeIf { it.isNotBlank() } ?: "Map") }
    }

    val selected: FlyEntry? get() = table?.entries?.getOrNull(selectedIndex)

    private fun mapName(header: Int): String = mapLabels.getOrNull(header)?.drop(5) ?: tr(Res.string.fly_header_x, header)

    fun label(position: Int): String {
        val e = table?.entries?.getOrNull(position) ?: return "%02d".format(position)
        return "%02d  %s".format(position, mapName(e.flyHeader))
    }

    fun rows(): List<Int> {
        val q = search.trim()
        return (table?.entries?.indices ?: IntRange.EMPTY).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    fun isEdited(position: Int): Boolean = original?.entries?.getOrNull(position) != table?.entries?.getOrNull(position)

    fun select(position: Int) {
        if (position in 0 until (table?.entries?.size ?: 0)) selectedIndex = position
    }

    fun update(change: (FlyEntry) -> FlyEntry) {
        val t = table ?: return
        val current = selected ?: return
        val updated = t.with(selectedIndex, change(current))
        when (val saved = FlyTable.save(project, updated)) {
            is FoxResult.Ok -> { table = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }
}

@Composable
fun FlyEditor(model: FlyEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val table = model.table ?: return
    Row(modifier.fillMaxSize()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedIndex,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = stringResource(Res.string.fly_search_towns),
            label = model::label,
            onSelect = model::select,
            isEdited = model::isEdited,
            detail = { table.entries.getOrNull(it)?.let { e -> if (e.isFlyPoint) "fly" else null } },
        )
        val town = model.selected
        DetailPane {
            if (town != null) {
                EditorHeader(model.selectedIndex.toString(), model.label(model.selectedIndex).drop(4))
                SectionCard(stringResource(Res.string.fly_town), subtitle = stringResource(Res.string.fly_how_the_game_treats_this_entry)) {
                    FieldFlow {
                        NumberField(stringResource(Res.string.fly_unlock_flag_index), town.flagIndex, 0..255, { v -> model.update { it.copy(flagIndex = v) } }, Modifier.width(150.dp))
                        ToggleChip(stringResource(Res.string.fly_fly_point), town.isFlyPoint, { v -> model.update { it.copy(isFlyPoint = v) } })
                        ToggleChip(stringResource(Res.string.fly_blackout_spawn), town.isBlackoutSpawn, { v -> model.update { it.copy(isBlackoutSpawn = v) } })
                    }
                    Text(
                        stringResource(Res.string.fly_the_table_has_a_fixed_length_of_30),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionCard(stringResource(Res.string.fly_fly_landing), subtitle = stringResource(Res.string.fly_where_the_player_appears_after_usi)) {
                    FieldFlow {
                        PickerField(stringResource(Res.string.fly_fly_map), town.flyHeader, model.mapLabels, { v -> model.update { it.copy(flyHeader = v) } }, Modifier.width(260.dp))
                        NumberField(stringResource(Res.string.fly_fly_x), town.flyX, 0..65535, { v -> model.update { it.copy(flyX = v) } }, Modifier.width(100.dp))
                        NumberField(stringResource(Res.string.fly_fly_y), town.flyY, 0..65535, { v -> model.update { it.copy(flyY = v) } }, Modifier.width(100.dp))
                    }
                }
                SectionCard(stringResource(Res.string.fly_blackout), subtitle = stringResource(Res.string.fly_where_the_player_wakes_up_after_lo)) {
                    FieldFlow {
                        PickerField(stringResource(Res.string.fly_blackout_map), town.blackoutHeader, model.mapLabels, { v -> model.update { it.copy(blackoutHeader = v) } }, Modifier.width(260.dp))
                        NumberField(stringResource(Res.string.fly_blackout_x), town.localX, 0..255, { v -> model.update { it.copy(localX = v) } }, Modifier.width(100.dp))
                        NumberField(stringResource(Res.string.fly_blackout_y), town.localY, 0..255, { v -> model.update { it.copy(localY = v) } }, Modifier.width(100.dp))
                    }
                }
                SectionCard(stringResource(Res.string.fly_unlock_spot), subtitle = stringResource(Res.string.fly_the_place_that_adds_the_town_to_th)) {
                    FieldFlow {
                        PickerField(stringResource(Res.string.fly_unlock_map), town.unlockHeader, model.mapLabels, { v -> model.update { it.copy(unlockHeader = v) } }, Modifier.width(260.dp))
                        NumberField(stringResource(Res.string.fly_unlock_x), town.unlockX, 0..65535, { v -> model.update { it.copy(unlockX = v) } }, Modifier.width(100.dp))
                        NumberField(stringResource(Res.string.fly_unlock_y), town.unlockY, 0..65535, { v -> model.update { it.copy(unlockY = v) } }, Modifier.width(100.dp))
                    }
                }
            }
        }
    }
}
