package io.github.legendaryredfox.foxhg.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.encounter.EncounterAreas
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeader
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** State of the Map headers editor. Edits go into the project's ARM9 (where the header table lives). */
class MapHeaderEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    val pictures = MapPictures(project)
    private var headers: List<MapHeader> = emptyList()
    private val original: List<MapHeader> = MapHeaderTable.original(project)

    var loadError: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(2)
        private set
    private var version by mutableIntStateOf(0)

    /** Encounter file labels, to show "Route 29" next to a header's wild-encounter number. */
    val encounterAreas: EncounterAreas = EncounterAreas.load(project, names)

    init {
        when (val loaded = MapHeaderTable.load(project)) {
            is FoxResult.Ok -> headers = loaded.value
            is FoxResult.Err -> loadError = loaded.message
        }
    }

    val all: List<MapHeader>
        get() {
            version // subscribe
            return headers
        }

    val selected: MapHeader? get() = all.getOrNull(selectedIndex)

    fun locationName(index: Int): String {
        val h = headers.getOrNull(index) ?: return "Map $index"
        return names?.locations?.getOrNull(h.locationName)?.takeIf { it.isNotBlank() } ?: "Map $index"
    }

    fun label(index: Int): String = "%03d  %s".format(index, locationName(index))

    fun rows(): List<Int> {
        val q = search.trim()
        return headers.indices.filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    fun select(index: Int) {
        if (index in headers.indices) selectedIndex = index
    }

    fun update(change: (MapHeader) -> MapHeader) {
        val updated = headers.toMutableList().also { it[selectedIndex] = change(it[selectedIndex]) }
        when (val saved = MapHeaderTable.write(project, updated)) {
            is FoxResult.Ok -> {
                headers = updated
                version++
                onChanged()
            }
            is FoxResult.Err -> loadError = saved.message
        }
    }

    /** Replaces the whole table (a CSV import) with one ARM9 write; a table that does not fit leaves everything as it was. */
    fun updateAll(change: (List<MapHeader>) -> List<MapHeader>) {
        val updated = change(headers)
        if (updated == headers) return
        when (val saved = MapHeaderTable.write(project, updated)) {
            is FoxResult.Ok -> {
                headers = updated
                version++
                onChanged()
            }
            is FoxResult.Err -> loadError = saved.message
        }
    }

    fun isEdited(index: Int): Boolean {
        version // subscribe
        return original.getOrNull(index)?.let { it != headers.getOrNull(index) } ?: false
    }
}
