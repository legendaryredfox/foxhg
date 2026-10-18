package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.typechart.TypeChart
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The type effectiveness table of overlay 12: click a cell to cycle the multiplier, written straight into the project. */
class TypeChartEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    private val loaded = TypeChart.load(project)
    private val original: TypeChart? = (loaded as? FoxResult.Ok)?.value
    var chart: TypeChart? by mutableStateOf(original)
        private set

    /** Why the table could not be loaded; the screen shows only this. A failed save is a [message] instead, so the grid stays. */
    val error: String? = (loaded as? FoxResult.Err)?.message
    var message: String? by mutableStateOf(null)
        private set

    /** The types shown: every type but "???" (9). */
    val types: List<Int> = (0 until TypeChart.TYPES).filter { it != 9 }

    fun typeName(type: Int): String = names?.types?.getOrNull(type) ?: "Type $type"

    fun isEdited(attacker: Int, defender: Int): Boolean = chart?.multiplier(attacker, defender) != original?.multiplier(attacker, defender)

    /** Next multiplier in the cycle 1x, 2x, 1/2x, 0x. */
    fun cycle(attacker: Int, defender: Int) {
        val current = chart ?: return
        val next = when (current.multiplier(attacker, defender)) { 10 -> 20; 20 -> 5; 5 -> 0; else -> 10 }
        val updated = current.with(attacker, defender, next)
        if (updated == current) {
            message = "No free slot for ${typeName(attacker)} against ${typeName(defender)}: the table is full. Set another pair back to 1x first."
            return
        }
        message = null
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { chart = updated; onChanged() }
            is FoxResult.Err -> message = saved.message
        }
    }
}

@Composable
fun TypeChartEditor(model: TypeChartEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val chart = model.chart ?: return
    DetailPane(modifier.padding(16.dp)) {
        Text(stringResource(Res.string.editor_type_chart), style = MaterialTheme.typography.headlineSmall)
        SectionCard(
            stringResource(Res.string.type_chart_effectiveness),
            subtitle = stringResource(Res.string.type_chart_rows_attack_columns_defend_click_a, HgssLayout.TYPE_CHART_SLOTS - 1, chart.slotsUsed - 1),
        ) {
            Row(Modifier.padding(start = 96.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                model.types.forEach { d -> Text(model.typeName(d).take(3), Modifier.width(38.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            model.types.forEach { a ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.typeName(a), Modifier.width(94.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    model.types.forEach { d -> Cell(model, chart, a, d) }
                }
            }
            model.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(stringResource(Res.string.type_chart_2x_super_effective), emphasis = true)
                Chip(stringResource(Res.string.type_chart_1_2x_not_very_effective))
                Chip(stringResource(Res.string.type_chart_0x_no_effect))
            }
        }
    }
}

@Composable
private fun Cell(model: TypeChartEditorModel, chart: TypeChart, attacker: Int, defender: Int) {
    val scheme = MaterialTheme.colorScheme
    val multiplier = chart.multiplier(attacker, defender)
    val color = when (multiplier) {
        20 -> scheme.primaryContainer
        5 -> scheme.errorContainer
        0 -> scheme.onSurface.copy(alpha = 0.75f)
        else -> scheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val text = when (multiplier) { 20 -> "2"; 5 -> "½"; 0 -> "0"; else -> "" }
    Box(
        Modifier.width(38.dp).height(28.dp).background(color, MaterialTheme.shapes.small)
            .clickable(role = Role.Button) { model.cycle(attacker, defender) }.testTag("type:$attacker:$defender")
            .semantics { contentDescription = "${model.typeName(attacker)} against ${model.typeName(defender)}: ${if (multiplier == 5) "1/2" else (multiplier / 10.0).toString().removeSuffix(".0")}x" },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (model.isEdited(attacker, defender)) FontWeight.Bold else FontWeight.Normal, color = if (multiplier == 0) scheme.surface else scheme.onSurface)
    }
}
