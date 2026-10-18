package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PokemonFront
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.data.staticmon.StaticEncounter
import io.github.legendaryredfox.foxhg.core.data.staticmon.StaticEncounters
import io.github.legendaryredfox.foxhg.core.data.staticmon.StaticKind
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The fixed Pokémon of the game's scripts: scripted battles, gifts and eggs, each with where its script belongs, editable in place. */
class StaticEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var encounters: List<StaticEncounter> by mutableStateOf(emptyList())
        private set
    var error: String? by mutableStateOf(null)
        private set

    /** Map header numbers by the script file they use, to tell where a script is. */
    private val headers = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
    private val mapsOfScript: Map<Int, List<Int>> = headers.withIndex().groupBy({ it.value.script }, { it.index })

    init { reload() }

    private fun reload() {
        when (val r = StaticEncounters.find(project)) {
            is FoxResult.Ok -> { encounters = r.value; error = null }
            is FoxResult.Err -> error = r.message
        }
    }

    /** "Script 98 (New Bark Town, Route 29)" style text for where an encounter is. */
    fun where(e: StaticEncounter): String {
        val maps = mapsOfScript[e.file].orEmpty()
        val places = maps.mapNotNull { names?.locations?.getOrNull(headers.getOrNull(it)?.locationName ?: -1)?.takeIf { n -> n.isNotBlank() } }.distinct()
        return "Script file ${e.file}" + if (places.isNotEmpty()) " (${places.take(3).joinToString(", ")})" else ""
    }

    fun change(e: StaticEncounter, species: Int, level: Int?) {
        when (val r = StaticEncounters.change(project, e, species, level)) {
            is FoxResult.Ok -> { reload(); onChanged() }
            is FoxResult.Err -> error = r.message
        }
    }
}

@Composable
fun StaticEditor(model: StaticEditorModel, modifier: Modifier = Modifier) {
    DetailPane(modifier) {
        Text(stringResource(Res.string.editor_statics), style = MaterialTheme.typography.headlineSmall)
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val species = model.names?.species?.take(StaticEncounters.MAX_SPECIES + 1) ?: List(StaticEncounters.MAX_SPECIES + 1) { "#$it" }
        Text(
            stringResource(Res.string.static_found_by_reading_every_script_a_fi),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        for (kind in StaticKind.entries) {
            val rows = model.encounters.filter { it.kind == kind }
            SectionCard(
                when (kind) { StaticKind.BATTLE -> "Scripted battles"; StaticKind.GIFT -> "Gift Pokémon"; StaticKind.EGG -> "Eggs" },
                subtitle = stringResource(Res.string.static_x_in_the_scripts, rows.size),
            ) {
                rows.forEach { e ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!e.speciesIsVariable) PokemonFront(e.species, 40.dp)
                        if (e.speciesIsVariable) Chip(stringResource(Res.string.static_species_from_a_variable), Modifier.width(220.dp))
                        else SpeciesPicker(stringResource(Res.string.evolution_species), e.species, species, { v -> if (v in 1..StaticEncounters.MAX_SPECIES) model.change(e, v, e.level) }, Modifier.width(220.dp))
                        if (kind.hasLevel) {
                            if (e.levelIsVariable || e.level == null) Chip(stringResource(Res.string.static_level_from_a_variable))
                            else NumberField(stringResource(Res.string.learnset_level), e.level!!, 1..100, { v -> model.change(e, e.species, v) }, Modifier.width(90.dp), tag = "Level ${e.file}:${e.index}" + if (e.levelViaVariable) ":${e.levelIndex}" else "")
                        }
                        if (e.viaVariable) Chip(stringResource(Res.string.static_set_at_item_x_used_at_x, e.index, e.commandIndex))
                        if (e.levelViaVariable) Chip(stringResource(Res.string.static_level_set_at_item_x, e.levelIndex))
                        Text(model.where(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
