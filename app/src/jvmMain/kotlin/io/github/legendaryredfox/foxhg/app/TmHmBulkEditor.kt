package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.PokemonIcon
import io.github.legendaryredfox.foxhg.app.ui.SearchBox
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionData
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalTable
import io.github.legendaryredfox.foxhg.core.data.personal.TmHmBulk
import io.github.legendaryredfox.foxhg.core.data.tmhm.TmHmTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** TM/HM compatibility of every species for one machine at a time, with bulk changes of the species shown, written straight into the project. */
class TmHmBulkEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    private var table: PersonalTable? = null
    private var families: List<List<Int>> = emptyList()
    private var version by mutableIntStateOf(0)
    var error: String? by mutableStateOf(null)
        private set
    var machine: Int by mutableIntStateOf(0)
    var search: String by mutableStateOf("")
    private val moves: TmHmTable? = (TmHmTable.load(project) as? FoxResult.Ok)?.value

    init {
        when (val loaded = PersonalTable.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
        (EvolutionData.load(project) as? FoxResult.Ok)?.let { families = TmHmBulk.families(it.value.records) }
    }

    val machineNames: List<String>
        get() {
            val moveNames = names?.moves ?: emptyList()
            val t = moves
            return List(TmHmBulk.MACHINES) { i -> (t?.name(i) ?: tr(Res.string.tm_hm_bulk_machine_x, i)) + (t?.moves?.getOrNull(i)?.let { m -> "  " + (moveNames.getOrNull(m) ?: "Move $m") } ?: "") }
        }

    fun label(species: Int): String = "%03d  %s".format(species, names?.species?.getOrNull(species) ?: "Form $species")

    /** Species matching the search text, by name or number. */
    fun rows(): List<Int> {
        version // subscribe
        val count = minOf(table?.records?.size ?: 0, names?.species?.size ?: 0)
        val q = search.trim()
        return (1 until count).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    fun learns(species: Int): Boolean {
        version // subscribe
        return table?.records?.getOrNull(species)?.learnsTmHm(machine) == true
    }

    fun learnerCount(): Int {
        version
        return table?.let { TmHmBulk.learners(it, machine).size } ?: 0
    }

    fun familyOf(species: Int): List<Int> = families.firstOrNull { species in it } ?: listOf(species)

    private fun change(edit: (PersonalTable) -> PersonalTable) {
        val current = table ?: return
        val updated = edit(current)
        if (updated.records == current.records) return
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { table = updated; version++; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }

    fun set(species: Collection<Int>, learns: Boolean) = change { TmHmBulk.set(it, machine, species, learns) }
}

@Composable
fun TmHmBulkEditor(model: TmHmBulkEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val rows = model.rows()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.editor_tmhm_bulk), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            PickerField(stringResource(Res.string.tm_hm_bulk_machine), model.machine, model.machineNames, { model.machine = it }, Modifier.width(280.dp))
            SearchBox(model.search, { model.search = it }, stringResource(Res.string.personal_search_species), Modifier.width(240.dp))
            Chip(stringResource(Res.string.tm_hm_bulk_x_species_can_learn_it, model.learnerCount()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.tm_hm_bulk_x_shown, rows.size), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { model.set(rows, true) }, modifier = Modifier.testTag("tmhm-all")) { Text(stringResource(Res.string.tm_hm_bulk_teach_to_all_shown)) }
            OutlinedButton(onClick = { model.set(rows, false) }, modifier = Modifier.testTag("tmhm-none")) { Text(stringResource(Res.string.tm_hm_bulk_remove_from_all_shown)) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it }) { species ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PokemonIcon(species)
                    Text(model.label(species), Modifier.width(200.dp), style = MaterialTheme.typography.bodyMedium)
                    val learns = model.learns(species)
                    ToggleChip(if (learns) stringResource(Res.string.tm_hm_bulk_learns) else stringResource(Res.string.tm_hm_bulk_cannot), learns, { on -> model.set(listOf(species), on) }, Modifier.testTag("tmhm:$species"))
                    val family = model.familyOf(species)
                    if (family.size > 1) {
                        OutlinedButton(onClick = { model.set(family, !learns) }, modifier = Modifier.testTag("tmhm-family:$species")) {
                            Text(if (learns) stringResource(Res.string.tm_hm_bulk_remove_from_family) else stringResource(Res.string.tm_hm_bulk_teach_to_family))
                        }
                    }
                }
            }
        }
    }
}
