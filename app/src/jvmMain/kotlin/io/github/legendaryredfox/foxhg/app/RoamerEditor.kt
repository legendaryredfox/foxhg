package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.padding
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PokemonFront
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamer
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamers
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The four roaming Pokémon: their species and level are in the game's code (`Save_CreateRoamerByID`), written straight into the ARM9 on each change. */
class RoamerEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var roamers: Roamers? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    /** Why the last change was refused, or null. */
    var message: String? by mutableStateOf(null)
        private set

    init {
        when (val loaded = Roamers.load(project)) {
            is FoxResult.Ok -> roamers = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    private fun apply(new: Roamers) {
        when (val r = Roamers.save(project, new)) {
            is FoxResult.Ok -> { roamers = new; message = null; onChanged() }
            is FoxResult.Err -> message = r.message
        }
    }

    private fun change(slot: Int, edit: (Roamer) -> Roamer) {
        val current = roamers ?: return
        apply(Roamers(current.roamers.mapIndexed { i, r -> if (i == slot) edit(r) else r }))
    }

    fun setSpecies(slot: Int, species: Int) = change(slot) { it.copy(species = species) }

    fun setLevel(slot: Int, level: Int) = change(slot) { it.copy(level = level) }

    fun restoreVanilla() { apply(Roamers.VANILLA) }
}

@Composable
fun RoamerEditor(model: RoamerEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val roamers = model.roamers ?: return
    val species = model.names?.species?.take(Roamers.MAX_SPECIES + 1) ?: emptyList()
    DetailPane(modifier) {
        Text(stringResource(Res.string.roamer_roaming_pok_mon), style = MaterialTheme.typography.headlineSmall)
        SectionCard(
            stringResource(Res.string.roamer_the_four_roamers),
            subtitle = "The script command CreateRoamer only says which of the four starts (0 to 3); the species and level are in the game's code. " +
                "Raikou and Entei take species 1 to 255, Latias a number that is a byte times a power of two (for example 380), Latios any species",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                for (slot in 0 until Roamers.COUNT) {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PokemonFront(roamers.roamers[slot].species, 72.dp)
                        SpeciesPicker(Roamers.NAMES[slot] + " (roamer $slot)", roamers.roamers[slot].species, species, { v -> if (v in 1..Roamers.MAX_SPECIES) model.setSpecies(slot, v) }, Modifier.width(210.dp))
                        NumberField(stringResource(Res.string.learnset_level), roamers.roamers[slot].level, 1..100, { v -> model.setLevel(slot, v) }, Modifier.width(100.dp), tag = "${Roamers.NAMES[slot]} level")
                    }
                }
            }
            model.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("roamer-message")) }
            Text(
                stringResource(Res.string.roamer_a_roamer_s_route_and_the_way_it_fl),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = model::restoreVanilla, modifier = Modifier.testTag("roamer-restore")) { Text(stringResource(Res.string.roamer_restore_the_original_four)) }
        }
    }
}
