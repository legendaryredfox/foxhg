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
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.patch.PatchStatus
import io.github.legendaryredfox.foxhg.core.patch.ShinyRates
import io.github.legendaryredfox.foxhg.core.patch.ShinyRatesPatch
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The chance of a shiny Pokémon for four kinds of Pokémon: needs the "Shiny rates" code in the ROM, which this screen installs. */
class ShinyRatesEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    var rates: ShinyRates? by mutableStateOf(null)
        private set
    var message: String? by mutableStateOf(null)
        private set

    /** Why the code can not be installed, or null. */
    var blocked: String? by mutableStateOf(null)
        private set

    init { refresh() }

    private fun refresh() {
        rates = (ShinyRatesPatch.load(project) as? FoxResult.Ok)?.value
        blocked = if (rates != null) null else when (val s = ShinyRatesPatch.status(project)) {
            is PatchStatus.Unavailable -> s.reason.takeUnless { it.startsWith("Apply \"") }
            else -> null
        }
    }

    /** Installs the ARM9 expansion if the ROM lacks it, then the shiny rate code, with every rate at 0. */
    fun install() {
        if (DsprePatches.arm9Expansion.status(project) == PatchStatus.Available) {
            val r = DsprePatches.arm9Expansion.apply(project)
            if (r is FoxResult.Err) { message = r.message; return }
        }
        when (val r = ShinyRatesPatch.apply(project)) {
            is FoxResult.Ok -> { message = null; refresh(); onChanged() }
            is FoxResult.Err -> message = r.message
        }
    }

    fun set(edit: (ShinyRates) -> ShinyRates) {
        val current = rates ?: return
        when (val r = ShinyRatesPatch.save(project, edit(current))) {
            is FoxResult.Ok -> { message = null; refresh(); onChanged() }
            is FoxResult.Err -> message = r.message
        }
    }
}

@Composable
fun ShinyRatesEditor(model: ShinyRatesEditorModel, modifier: Modifier = Modifier) {
    DetailPane(modifier) {
        Text(stringResource(Res.string.editor_shiny_rates), style = MaterialTheme.typography.headlineSmall)
        Text(
            "The game makes a Pokémon shiny only by chance, 8 in 65536. This screen sets the chance for four kinds of Pokémon, each on its own: wild encounters, static battles (the fixed battles of the Static Pokémon screen), gifts and eggs, and trainers. " +
                "Fields are in hundredths of a percent: 1000 is 10 %, 10000 is 100 %, 0 leaves the game as it is.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        model.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("shiny-message")) }
        val rates = model.rates
        if (rates == null) {
            SectionCard(stringResource(Res.string.shiny_rates_not_installed), subtitle = model.blocked ?: "Installs routines in the synthetic overlay (and the ARM9 expansion if the ROM lacks it) and hooks the places that make these Pokémon. All rates start at 0.") {
                OutlinedButton(onClick = model::install, enabled = model.blocked == null, modifier = Modifier.testTag("shiny-install")) { Text(stringResource(Res.string.shiny_rates_install_the_shiny_rate_code)) }
            }
            return@DetailPane
        }
        SectionCard(stringResource(Res.string.shiny_rates_chance_of_a_shiny), subtitle = stringResource(Res.string.shiny_rates_wild_encounters_keep_cute_charm_an)) {
            @Composable fun rateRow(label: String, tag: String, value: Int, put: (ShinyRates, Int) -> ShinyRates) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(label, ShinyRates.toBasisPoints(value), 0..10000, { v -> model.set { put(it, ShinyRates.fromBasisPoints(v)) } }, Modifier.width(240.dp), tag = tag)
                    Text("%.2f %%".format(ShinyRates.percent(value)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            rateRow(stringResource(Res.string.shiny_rates_wild_encounters_grass_water_rods_c), "Shiny wild", rates.wild) { r, v -> r.copy(wild = v) }
            rateRow(stringResource(Res.string.shiny_rates_static_battles_lugia_ho_oh_snorlax), "Shiny scripted", rates.scripted) { r, v -> r.copy(scripted = v) }
            rateRow(stringResource(Res.string.shiny_rates_gift_pok_mon_and_eggs), "Shiny gift", rates.gift) { r, v -> r.copy(gift = v) }
            rateRow(stringResource(Res.string.shiny_rates_trainers_pok_mon), "Shiny trainer", rates.trainer) { r, v -> r.copy(trainer = v) }
        }
    }
}
