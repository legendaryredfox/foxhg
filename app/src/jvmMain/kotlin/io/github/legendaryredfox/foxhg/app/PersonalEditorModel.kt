package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalData
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** State of the Personal data editor: the list of species, the selection, and edits written to the project. */
class PersonalEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    private var table: PersonalTable? = null
    private val original: List<PersonalData> = project.rom.read(project.layout.personal)
        ?.let { (Narc.parse(it) as? FoxResult.Ok)?.value }
        ?.files?.mapNotNull { (PersonalData.parse(it) as? FoxResult.Ok)?.value }
        ?: emptyList()

    val names: GameNames? = project.names
    var loadError: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(1)
        private set
    private var version by mutableIntStateOf(0)

    init {
        when (val loaded = PersonalTable.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> loadError = loaded.message
        }
    }

    val recordCount: Int get() = table?.records?.size ?: 0

    val selected: PersonalData?
        get() {
            version // read to subscribe
            return table?.records?.getOrNull(selectedIndex)
        }

    fun label(index: Int): String =
        "%03d  %s".format(index, names?.species?.getOrNull(index) ?: if (index == 0) "-----" else "Form $index")

    /** Rows matching the search text (by name or number). */
    fun rows(): List<Int> {
        val q = search.trim()
        return (0 until recordCount).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    /** Every record, for the CSV buttons. */
    val records: List<PersonalData>
        get() {
            version // read to subscribe
            return table?.records ?: emptyList()
        }

    /** Replaces every record at once (CSV import). */
    fun updateAll(new: List<PersonalData>) {
        val current = table ?: return
        val updated = current.withAll(new)
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { table = updated; version++; onChanged() }
            is FoxResult.Err -> loadError = saved.message
        }
    }

    fun select(index: Int) {
        if (index in 0 until recordCount) selectedIndex = index
    }

    fun update(change: (PersonalData) -> PersonalData) {
        val current = table ?: return
        val updated = current.with(selectedIndex, change(current.records[selectedIndex]))
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> {
                table = updated
                version++
                onChanged()
            }
            is FoxResult.Err -> loadError = saved.message
        }
    }

    /** True when record [index] differs from what the ROM originally has. */
    fun isEdited(index: Int): Boolean {
        version // subscribe
        val current = table?.records?.getOrNull(index) ?: return false
        return original.getOrNull(index) != current
    }
}
