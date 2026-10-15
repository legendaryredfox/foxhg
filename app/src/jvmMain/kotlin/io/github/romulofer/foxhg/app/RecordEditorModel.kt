package io.github.romulofer.foxhg.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.RecordSpec
import io.github.romulofer.foxhg.core.game.GameNames
import io.github.romulofer.foxhg.core.project.RomProject

/**
 * List/detail state shared by the editors whose data is "one record per file of a NARC": the list with search, the
 * selected record, and edits written straight into the project.
 */
class RecordEditorModel<T>(
    private val project: RomProject,
    private val spec: RecordSpec<T>,
    private val label: (Int) -> String,
    private val onChanged: () -> Unit = {},
    firstIndex: Int = 0,
    private val onSelected: (Int) -> Unit = {},
) {
    val names: GameNames? = project.names
    private var table: NarcRecords<T>? = null
    private val original: List<T> = NarcRecords.original(project, spec)

    var loadError: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(firstIndex)
        private set
    private var version by mutableIntStateOf(0)

    init {
        when (val loaded = NarcRecords.load(project, spec)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> loadError = loaded.message
        }
    }

    val recordCount: Int get() = table?.records?.size ?: 0

    val records: List<T>
        get() {
            version // subscribe
            return table?.records ?: emptyList()
        }

    val selected: T? get() = records.getOrNull(selectedIndex)

    fun label(index: Int): String = label.invoke(index)

    fun rows(): List<Int> {
        val q = search.trim()
        return (0 until recordCount).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    fun select(index: Int) {
        if (index in 0 until recordCount) {
            selectedIndex = index
            onSelected(index)
        }
    }

    fun update(change: (T) -> T) {
        val current = table ?: return
        replace(current.with(selectedIndex, change(current.records[selectedIndex])))
    }

    /** Replaces every record at once (bulk tools). */
    fun updateAll(change: (List<T>) -> List<T>) {
        val current = table ?: return
        replace(current.withAll(change(current.records)))
    }

    private fun replace(updated: NarcRecords<T>) {
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> {
                table = updated
                version++
                onChanged()
            }
            is FoxResult.Err -> loadError = saved.message
        }
    }

    fun isEdited(index: Int): Boolean {
        version // subscribe
        val current = table?.records?.getOrNull(index) ?: return false
        return original.getOrNull(index) != current
    }

    companion object {
        fun speciesLabel(names: GameNames?): (Int) -> String = { i ->
            "%03d  %s".format(i, names?.species?.getOrNull(i) ?: if (i == 0) "-----" else "Form $i")
        }

        fun itemLabel(names: GameNames?): (Int) -> String = { i -> "%03d  %s".format(i, names?.items?.getOrNull(i) ?: "Item $i") }

        fun moveLabel(names: GameNames?): (Int) -> String = { i -> "%03d  %s".format(i, names?.moves?.getOrNull(i) ?: "Move $i") }
    }
}
