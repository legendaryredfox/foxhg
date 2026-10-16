package io.github.romulofer.foxhg.app

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
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.app.ui.ToggleChip
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.fly.FlyEntry
import io.github.romulofer.foxhg.core.data.fly.FlyTable
import io.github.romulofer.foxhg.core.data.mapheader.MapHeaderTable
import io.github.romulofer.foxhg.core.project.RomProject

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

    private fun mapName(header: Int): String = mapLabels.getOrNull(header)?.drop(5) ?: "Header $header"

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
            placeholder = "Search towns…",
            label = model::label,
            onSelect = model::select,
            isEdited = model::isEdited,
            detail = { table.entries.getOrNull(it)?.let { e -> if (e.isFlyPoint) "fly" else null } },
        )
        val town = model.selected
        DetailPane {
            if (town != null) {
                EditorHeader(model.selectedIndex.toString(), model.label(model.selectedIndex).drop(4))
                SectionCard("Town", subtitle = "How the game treats this entry") {
                    FieldFlow {
                        NumberField("Unlock flag index", town.flagIndex, 0..255, { v -> model.update { it.copy(flagIndex = v) } }, Modifier.width(150.dp))
                        ToggleChip("Fly point", town.isFlyPoint, { v -> model.update { it.copy(isFlyPoint = v) } })
                        ToggleChip("Blackout spawn", town.isBlackoutSpawn, { v -> model.update { it.copy(isBlackoutSpawn = v) } })
                    }
                    Text(
                        "The table has a fixed length of 30 towns, so towns can be changed but not added.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionCard("Fly landing", subtitle = "Where the player appears after using Fly (global position)") {
                    FieldFlow {
                        PickerField("Fly map", town.flyHeader, model.mapLabels, { v -> model.update { it.copy(flyHeader = v) } }, Modifier.width(260.dp))
                        NumberField("Fly X", town.flyX, 0..65535, { v -> model.update { it.copy(flyX = v) } }, Modifier.width(100.dp))
                        NumberField("Fly Y", town.flyY, 0..65535, { v -> model.update { it.copy(flyY = v) } }, Modifier.width(100.dp))
                    }
                }
                SectionCard("Blackout", subtitle = "Where the player wakes up after losing a battle (tile position inside the map)") {
                    FieldFlow {
                        PickerField("Blackout map", town.blackoutHeader, model.mapLabels, { v -> model.update { it.copy(blackoutHeader = v) } }, Modifier.width(260.dp))
                        NumberField("Blackout X", town.localX, 0..255, { v -> model.update { it.copy(localX = v) } }, Modifier.width(100.dp))
                        NumberField("Blackout Y", town.localY, 0..255, { v -> model.update { it.copy(localY = v) } }, Modifier.width(100.dp))
                    }
                }
                SectionCard("Unlock spot", subtitle = "The place that adds the town to the Fly list (global position)") {
                    FieldFlow {
                        PickerField("Unlock map", town.unlockHeader, model.mapLabels, { v -> model.update { it.copy(unlockHeader = v) } }, Modifier.width(260.dp))
                        NumberField("Unlock X", town.unlockX, 0..65535, { v -> model.update { it.copy(unlockX = v) } }, Modifier.width(100.dp))
                        NumberField("Unlock Y", town.unlockY, 0..65535, { v -> model.update { it.copy(unlockY = v) } }, Modifier.width(100.dp))
                    }
                }
            }
        }
    }
}
