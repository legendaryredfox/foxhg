package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.PokemonFront
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.fossil.Fossil
import io.github.legendaryredfox.foxhg.core.data.fossil.Fossils
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The seven fossils and the Pokémon the Pewter City scientist revives: the table is in overlay 21, written on each change. */
class FossilEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var fossils: Fossils? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    init {
        when (val loaded = Fossils.load(project)) {
            is FoxResult.Ok -> fossils = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    private fun apply(new: Fossils) {
        when (val r = Fossils.save(project, new)) {
            is FoxResult.Ok -> { fossils = new; error = null; onChanged() }
            is FoxResult.Err -> error = r.message
        }
    }

    fun setSpecies(slot: Int, species: Int) {
        val current = fossils ?: return
        apply(Fossils(current.fossils.mapIndexed { i, f -> if (i == slot) Fossil(f.item, species) else f }))
    }

    fun restoreVanilla() { apply(Fossils.VANILLA) }
}

@Composable
fun FossilEditor(model: FossilEditorModel, modifier: Modifier = Modifier) {
    val fossils = model.fossils
    if (fossils == null) {
        Text(model.error ?: "", color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val species = model.names?.species?.take(Fossils.MAX_SPECIES + 1) ?: emptyList()
    DetailPane(modifier) {
        Text("Fossils", style = MaterialTheme.typography.headlineSmall)
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("fossil-message")) }
        SectionCard(
            "Revived Pokémon",
            subtitle = "The scientist in the Pewter City museum asks this table which Pokémon a fossil gives. The level (20) is in his script, and the items are the ones his script and the bag know.",
        ) {
            for ((slot, fossil) in fossils.fossils.withIndex()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PokemonFront(fossil.species, 40.dp)
                    SpeciesPicker(Fossils.NAMES[slot], fossil.species, species, { v -> if (v in 1..Fossils.MAX_SPECIES) model.setSpecies(slot, v) }, Modifier.width(240.dp))
                }
            }
            OutlinedButton(onClick = model::restoreVanilla, modifier = Modifier.testTag("fossil-restore")) { Text("Restore the original seven") }
        }
    }
}
