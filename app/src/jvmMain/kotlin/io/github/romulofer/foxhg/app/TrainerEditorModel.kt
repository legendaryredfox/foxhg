package io.github.romulofer.foxhg.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.trainer.Trainer
import io.github.romulofer.foxhg.core.data.trainer.TrainerMon
import io.github.romulofer.foxhg.core.data.trainer.TrainerTable
import io.github.romulofer.foxhg.core.game.GameNames
import io.github.romulofer.foxhg.core.project.RomProject

/** State of the Trainer editor: list with search, selected trainer, and edits written to the project. */
class TrainerEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    private var table: TrainerTable? = null
    private val original: List<Trainer> = TrainerTable.original(project)

    var loadError: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(1)
        private set
    private var version by mutableIntStateOf(0)

    init {
        when (val loaded = TrainerTable.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> loadError = loaded.message
        }
    }

    val trainers: List<Trainer>
        get() {
            version // subscribe
            return table?.trainers ?: emptyList()
        }

    val selected: Trainer? get() = trainers.getOrNull(selectedIndex)

    fun name(index: Int): String = names?.trainerNames?.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Trainer $index"

    fun className(index: Int): String? = trainers.getOrNull(index)?.header?.trainerClass?.let { names?.trainerClasses?.getOrNull(it) }

    fun label(index: Int): String {
        return "%03d  %s".format(index, name(index))
    }

    fun rows(): List<Int> {
        val q = search.trim()
        return trainers.indices.filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) || (className(it)?.contains(q, ignoreCase = true) == true) || partyContains(it, q) }
    }

    /** Search also matches a Pokémon in the party, so "pidgey" finds who uses one. */
    private fun partyContains(index: Int, q: String): Boolean =
        trainers[index].party.any { names?.species?.getOrNull(it.species)?.contains(q, ignoreCase = true) == true }

    fun select(index: Int) {
        if (index in trainers.indices) selectedIndex = index
    }

    fun update(change: (Trainer) -> Trainer) {
        val current = table ?: return
        replace(current.with(selectedIndex, change(current.trainers[selectedIndex])))
    }

    fun updateMon(slot: Int, change: (TrainerMon) -> TrainerMon) = update { t ->
        t.withParty(t.party.toMutableList().also { it[slot] = change(it[slot]) })
    }

    fun addMon() = update { t ->
        if (t.party.size >= Trainer.MAX_PARTY) t else t.withParty(t.party + (t.party.lastOrNull() ?: TrainerMon(difficulty = 0, genderAbility = 0, level = 5, speciesAndForm = 1)))
    }

    fun removeMon(slot: Int) = update { t -> t.withParty(t.party.filterIndexed { i, _ -> i != slot }) }

    /** Replaces [from] by [to] (keeping level and everything else) in every party; returns how many Pokémon changed. */
    fun replaceSpeciesEverywhere(from: Int, to: Int): Int {
        val current = table ?: return 0
        var changed = 0
        val updated = current.withAll(
            current.trainers.map { t ->
                t.withParty(t.party.map { m -> if (m.species == from) m.withSpecies(to).also { changed++ } else m })
            },
        )
        if (changed > 0) replace(updated)
        return changed
    }

    private fun replace(updated: TrainerTable) {
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
        val current = table?.trainers?.getOrNull(index) ?: return false
        return original.getOrNull(index) != current
    }
}
