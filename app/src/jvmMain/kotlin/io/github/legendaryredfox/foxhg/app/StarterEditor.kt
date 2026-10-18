package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.PokemonFront
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.starter.Starters
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The three starters with their level and held item. Each change also updates what depends on them (see [Starters.change]). */
class StarterEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var starters: Starters? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    /** What the last change reached besides the starters, for the line under the cards. */
    var message: String? by mutableStateOf(null)
        private set

    init {
        when (val loaded = Starters.load(project)) {
            is FoxResult.Ok -> starters = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    private fun apply(new: Starters) {
        when (val r = Starters.change(project, new)) {
            is FoxResult.Ok -> {
                starters = new
                message = describe(r.value)
                onChanged()
            }
            is FoxResult.Err -> message = r.message
        }
    }

    fun setSpecies(slot: Int, species: Int) {
        val s = starters ?: return
        apply(s.copy(species = s.species.toMutableList().also { it[slot] = species }))
    }

    fun setLevel(level: Int) { starters?.let { apply(it.copy(level = level)) } }

    fun setHeldItem(item: Int) { starters?.let { apply(it.copy(heldItem = item)) } }

    fun restoreVanilla() {
        val s = starters ?: return
        apply(Starters.VANILLA.copy(heldItem = s.heldItem?.let { 0 }, level = s.level?.let { 5 }))
    }

    private fun describe(r: Starters.Companion.Report): String =
        "Updated the starters" + (if (r.chooserPatched) ", the chooser cries" else "") +
            ", the rival's counter-pick in ${plural(r.rivalScriptsPatched, "script")} and " + (if (r.textPatched) "the professor's text" else "no text")
}

@Composable
fun StarterEditor(model: StarterEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val starters = model.starters ?: return
    val species = model.names?.species?.take(Starters.MAX_SPECIES + 1) ?: emptyList()
    DetailPane(modifier) {
        Text(stringResource(Res.string.editor_starters), style = MaterialTheme.typography.headlineSmall)
        SectionCard(stringResource(Res.string.starter_the_three_pok_balls), subtitle = stringResource(Res.string.starter_left_middle_and_right_ball_in_prof)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                for (slot in 0 until 3) {
                    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PokemonFront(starters.species[slot], 72.dp)
                        SpeciesPicker(stringResource(Res.string.starter_ball_x, slot + 1), starters.species[slot], species, { v -> if (v in 1..Starters.MAX_SPECIES) model.setSpecies(slot, v) }, Modifier.width(200.dp))
                    }
                }
            }
        }
        SectionCard(stringResource(Res.string.starter_given_with_the_pok_mon), subtitle = if (starters.level == null || starters.heldItem == null) stringResource(Res.string.starter_part_of_this_is_not_found_in_this) else null) {
            FieldFlow {
                starters.level?.let { NumberField(stringResource(Res.string.learnset_level), it, 1..100, { v -> model.setLevel(v) }, Modifier.width(100.dp)) }
                starters.heldItem?.let { PickerField(stringResource(Res.string.starter_held_item), it, model.names?.items?.take(256) ?: emptyList(), { v -> model.setHeldItem(v) }, Modifier.width(240.dp)) }
            }
            Text(stringResource(Res.string.starter_the_held_item_is_one_byte_in_the_g), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(Res.string.starter_what_follows), subtitle = stringResource(Res.string.starter_a_starter_is_not_data_in_a_table_i)) {
            Text(
                stringResource(Res.string.starter_changing_a_ball_also_updates_the_c),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            model.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("starter-message")) }
            OutlinedButton(onClick = model::restoreVanilla, modifier = Modifier.testTag("starter-restore")) { Text(stringResource(Res.string.starter_restore_the_original_three)) }
        }
    }
}
