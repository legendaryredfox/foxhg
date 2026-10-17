package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.script.ScriptSymbols
import io.github.legendaryredfox.foxhg.core.save.SaveVarsFlags

private const val SHOWN = 60

/** What the user typed as a search: a number (decimal or `0x` hex) or a piece of a name. */
private fun matches(text: String, id: Int, name: String?): Boolean {
    val query = text.trim()
    if (query.isEmpty()) return true
    val number = query.removePrefix("0x").removePrefix("0X").let { q -> q.toIntOrNull(if (query.startsWith("0x", true)) 16 else 10) }
    return number == id || (name != null && name.contains(query, ignoreCase = true))
}

/** The event flags and script variables of the save: search by number or name, switch a flag, set a variable. Written to the file only by the Write button. */
@Composable
fun SaveFlagsScreen(model: SaveEditorModel, modifier: Modifier = Modifier) {
    val chunk = model.flags
    DetailPane(modifier.padding(16.dp)) {
        EditorHeader("", model.path.fileName.toString(), trailing = { SaveStatus(model) })
        if (chunk == null) {
            Text("This save has no sound general block, so there are no flags to show.", color = MaterialTheme.colorScheme.error)
            return@DetailPane
        }
        var query by remember { mutableStateOf("") }
        var onlySet by remember { mutableStateOf(false) }
        SectionCard("Search", subtitle = "A flag or variable by number (12, or 0x6A for hex) or by part of its name. Flags are events the game remembers (a badge won, an item picked up, a door opened); variables are numbers scripts keep. Changing them can break the story: the game does not check.") {
            LabeledText("Number or name", query) { query = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("Only flags that are set / variables that are not 0", onlySet, { onlySet = it })
            }
        }
        val flags = (1 until SaveVarsFlags.FLAG_COUNT).filter { matches(query, it, ScriptSymbols.flagName(it)) && (!onlySet || SaveVarsFlags.isSet(chunk, it)) }
        SectionCard("Flags", subtitle = "${SaveVarsFlags.setFlags(chunk).size} of ${SaveVarsFlags.FLAG_COUNT - 1} set; ${flags.size} match${if (flags.size > SHOWN) ", the first $SHOWN shown" else ""}") {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                flags.take(SHOWN).forEach { id ->
                    ToggleChip("0x%X %s".format(id, ScriptSymbols.flagName(id)?.removePrefix("FLAG_") ?: ""), SaveVarsFlags.isSet(chunk, id), { on -> model.updateFlags { SaveVarsFlags.with(it, id, on) } })
                }
            }
        }
        val variables = (0 until SaveVarsFlags.VARS).filter { matches(query, it, ScriptSymbols.variableName(ScriptSymbols.FIRST_VARIABLE + it)) && (!onlySet || SaveVarsFlags.variable(chunk, it) != 0) }
        SectionCard("Variables", subtitle = "${SaveVarsFlags.usedVariables(chunk).size} of ${SaveVarsFlags.VARS} not 0; ${variables.size} match${if (variables.size > SHOWN) ", the first $SHOWN shown" else ""}") {
            variables.take(SHOWN).forEach { n ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField("Value", SaveVarsFlags.variable(chunk, n), 0..65535, { v -> model.updateFlags { SaveVarsFlags.withVariable(it, n, v) } }, Modifier.width(110.dp), tag = "Variable $n")
                    Text("0x%X %s".format(ScriptSymbols.FIRST_VARIABLE + n, ScriptSymbols.variableName(ScriptSymbols.FIRST_VARIABLE + n)?.removePrefix("VAR_") ?: ""), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
