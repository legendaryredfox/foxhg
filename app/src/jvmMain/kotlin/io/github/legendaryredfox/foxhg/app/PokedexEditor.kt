package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.save.PokedexData

private const val SHOWN = 60

/** The Pokédex of the save: seen and caught per species, the Pokédex and National Dex switches. Written to the file only by the Write button. */
@Composable
fun SavePokedexScreen(model: SaveEditorModel, names: GameNames?, modifier: Modifier = Modifier) {
    val dex = model.pokedex
    DetailPane(modifier.padding(16.dp)) {
        EditorHeader("", model.path.fileName.toString(), trailing = { SaveStatus(model) })
        if (dex == null) {
            Text(stringResource(Res.string.pokedex_this_save_has_no_sound_general_blo), color = MaterialTheme.colorScheme.error)
            return@DetailPane
        }
        val speciesNames = remember(names) { names?.species ?: List(PokedexData.SPECIES + 1) { "#$it" } }
        SectionCard(stringResource(Res.string.editor_save_pokedex), subtitle = stringResource(Res.string.pokedex_x_seen_x_caught_of_x_a_species_mus, dex.seenCount, dex.caughtCount, PokedexData.SPECIES)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ToggleChip(stringResource(Res.string.pokedex_pok_dex_received), dex.dexEnabled, { on -> model.updatePokedex { it.withDexEnabled(on) } })
                ToggleChip(stringResource(Res.string.save_national_dex), dex.nationalDex, { on ->
                    model.updatePokedex { it.withNationalDex(on) }
                    // the player data keeps its own National Dex flag; the game reads both
                    model.updatePlayer { it.copy(flags = if (on) it.flags or 2 else it.flags and 2.inv()) }
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { model.updatePokedex { it.withAll(false) } }, modifier = Modifier.testTag("dex-all-seen")) { Text(stringResource(Res.string.pokedex_mark_all_seen)) }
                OutlinedButton(onClick = { model.updatePokedex { it.withAll(true) } }, modifier = Modifier.testTag("dex-all-caught")) { Text(stringResource(Res.string.pokedex_mark_all_caught)) }
                OutlinedButton(onClick = { model.updatePokedex { it.cleared() } }, modifier = Modifier.testTag("dex-clear")) { Text(stringResource(Res.string.pokedex_clear_the_pok_dex)) }
            }
        }
        var query by remember { mutableStateOf("") }
        val q = query.trim()
        val shown = (1..PokedexData.SPECIES).filter { q.isEmpty() || speciesNames.getOrElse(it) { "" }.contains(q, ignoreCase = true) || it == q.toIntOrNull() }
        SectionCard(stringResource(Res.string.evolution_species), subtitle = "${shown.size} match${if (shown.size > SHOWN) ", the first $SHOWN shown" else ""}") {
            LabeledText(stringResource(Res.string.pokedex_number_or_name), query) { query = it }
            shown.take(SHOWN).forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("%03d  %s".format(n, speciesNames.getOrElse(n) { "#$n" }), Modifier.width(200.dp), style = MaterialTheme.typography.bodyMedium)
                    ToggleChip(stringResource(Res.string.pokedex_seen), dex.isSeen(n), { on -> model.updatePokedex { it.withSeen(n, on) } }, Modifier.testTag("dex-seen:$n"))
                    ToggleChip(stringResource(Res.string.pokedex_caught), dex.isCaught(n), { on -> model.updatePokedex { it.withCaught(n, on) } }, Modifier.testTag("dex-caught:$n"))
                }
            }
        }
    }
}
