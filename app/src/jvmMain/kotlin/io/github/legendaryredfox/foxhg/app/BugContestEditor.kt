package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.bugcontest.BugContestEncounter
import io.github.legendaryredfox.foxhg.core.data.bugcontest.BugContestTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The Bug Catching Contest tables (`data/mushi/mushi_encount.bin`): four sets of ten Pokémon, written straight into the project on each edit. */
class BugContestEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var table: BugContestTable? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var set: Int by mutableIntStateOf(0)

    init {
        when (val loaded = BugContestTable.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    fun update(slot: Int, change: (BugContestEncounter) -> BugContestEncounter) {
        val current = table ?: return
        val updated = current.with(set, slot, change(current.sets[set][slot]))
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { table = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }
}

@Composable
fun BugContestEditor(model: BugContestEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val table = model.table ?: return
    val species = model.names?.species ?: emptyList()
    val entries = table.sets[model.set]
    val chances = BugContestTable.effectiveRates(entries)
    val problems = BugContestTable.problems(entries)
    DetailPane(modifier.padding(16.dp)) {
        Text("Bug Catching Contest", style = MaterialTheme.typography.headlineSmall)
        SectionCard("Set", subtitle = "Which set the National Park uses: the first without the National Dex, then by the day of the week on the DS clock") {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BugContestTable.SET_NAMES.forEachIndexed { s, name -> ToggleChip("Set $s", model.set == s, { model.set = s }) }
            }
            Text(BugContestTable.SET_NAMES[model.set], style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(
            "Pokémon",
            subtitle = "The game rolls 0 to 99 and takes the first slot whose rate is at or below the roll, so rates must fall from top to bottom and end at 0. " +
                "Chance = the gap to the rate above (100 for the first slot). Score is the rarity part of the judge's score",
        ) {
            entries.forEachIndexed { slot, e ->
                FieldFlow {
                    SpeciesPicker("Slot ${slot + 1}", e.species, species, { v -> model.update(slot) { it.copy(species = v) } }, Modifier.width(240.dp))
                    NumberField("Min level", e.minLevel, 1..100, { v -> model.update(slot) { it.copy(minLevel = v, maxLevel = maxOf(it.maxLevel, v)) } }, Modifier.width(90.dp), tag = "Slot ${slot + 1} min")
                    NumberField("Max level", e.maxLevel, 1..100, { v -> model.update(slot) { it.copy(maxLevel = v, minLevel = minOf(it.minLevel, v)) } }, Modifier.width(90.dp), tag = "Slot ${slot + 1} max")
                    NumberField("Rate", e.rate, 0..99, { v -> model.update(slot) { it.copy(rate = v) } }, Modifier.width(80.dp), tag = "Slot ${slot + 1} rate")
                    NumberField("Score", e.score, 0..255, { v -> model.update(slot) { it.copy(score = v) } }, Modifier.width(80.dp), tag = "Slot ${slot + 1} score")
                    Chip("${chances[slot]}%", emphasis = chances[slot] == 0)
                }
            }
            problems.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
