package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.growth.GrowthCurve
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The experience needed at each level for each growth rate (`a/0/0/3`), written straight into the project on each edit. */
class GrowthEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    var table: NarcRecords<GrowthCurve>? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var rate: Int by mutableIntStateOf(0)

    /** When on, editing a level moves every later level by the same amount, which keeps the experience per level (the guide's advice). */
    var shiftLater: Boolean by mutableStateOf(true)

    init {
        when (val loaded = NarcRecords.load(project, GrowthCurve.SPEC)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    fun setLevel(level: Int, total: Int) {
        val current = table ?: return
        val updated = current.with(rate, current.records[rate].withLevel(level, total, shiftLater))
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { table = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }
}

@Composable
fun GrowthEditor(model: GrowthEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val table = model.table ?: return
    val curve = table.records[model.rate]
    val problems = curve.problems()
    DetailPane(modifier.padding(16.dp)) {
        Text("Growth rates", style = MaterialTheme.typography.headlineSmall)
        SectionCard("Growth rate", subtitle = "Which species use which rate is set in Personal data. The last two rates are unused by the game and equal Medium Fast") {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GrowthCurve.NAMES.forEachIndexed { r, name -> ToggleChip(name, model.rate == r, { model.rate = r }) }
            }
            ToggleChip("Shift later levels", model.shiftLater, { model.shiftLater = it })
        }
        SectionCard(
            "Experience by level",
            subtitle = "Total experience needed to be at each level. Level 1 always needs 0. Changing a level moves the later ones too unless the switch above is off",
        ) {
            for (level in 2..GrowthCurve.LEVELS) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    NumberField("Level $level", curve.exp[level], 0..99_999_999, { v -> model.setLevel(level, v) }, Modifier.width(150.dp), tag = "Level $level")
                    if (level < GrowthCurve.LEVELS) Chip("+${curve.toNext(level)} to level ${level + 1}", emphasis = (level + 1) in problems)
                }
            }
            problems.forEach { Text("Level $it needs less experience than level ${it - 1}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
