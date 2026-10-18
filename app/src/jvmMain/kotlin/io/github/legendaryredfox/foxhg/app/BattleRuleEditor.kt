package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.battlerule.BattleRule
import io.github.legendaryredfox.foxhg.core.data.battlerule.BattleRules
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The simple battle tweaks of the wiki guide "Battle Logic Edits": one number each, written into the overlay or battle script that holds it. */
class BattleRuleEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val rules: List<BattleRule> = BattleRules.all
    var error: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(0)
        private set
    private var version by mutableIntStateOf(0)

    /** The value of rule [index]; the value of its first place when the places disagree. */
    fun value(index: Int): Int {
        version // subscribe
        val rule = rules[index]
        return ((BattleRules.read(project, rule) as? FoxResult.Ok) ?: BattleRules.readFirst(project, rule) as? FoxResult.Ok)?.value ?: rule.vanilla
    }

    /** The error of rule [index] when its places hold different values, or null. */
    fun disagreement(index: Int): String? { version; return (BattleRules.read(project, rules[index]) as? FoxResult.Err)?.message }

    fun label(index: Int): String = "${rules[index].group}: ${rules[index].name}"

    fun rows(): List<Int> {
        val q = search.trim()
        return rules.indices.filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    fun isEdited(index: Int): Boolean = value(index) != rules[index].vanilla || disagreement(index) != null

    fun select(index: Int) { if (index in rules.indices) selectedIndex = index }

    fun set(index: Int, value: Int) {
        when (val r = BattleRules.write(project, rules[index], value)) {
            is FoxResult.Ok -> { error = null; version++; onChanged() }
            is FoxResult.Err -> error = r.message
        }
    }
}

@Composable
fun BattleRuleEditor(model: BattleRuleEditorModel, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedIndex,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = stringResource(Res.string.battle_rule_search_battle_rules),
            label = model::label,
            onSelect = model::select,
            isEdited = model::isEdited,
            detail = { model.value(it).toString() },
        )
        val index = model.selectedIndex
        val rule = model.rules[index]
        DetailPane {
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("battle-rule-message")) }
            EditorHeader(rule.group, rule.name)
            SectionCard(rule.name, subtitle = rule.description) {
                FieldFlow {
                    NumberField(stringResource(Res.string.battle_rule_value), model.value(index), rule.range, { v -> model.set(index, v) }, Modifier.width(110.dp), tag = "Battle rule")
                    Chip("${rule.explain(model.value(index))}")
                    Chip(stringResource(Res.string.battle_rule_game_x_x, rule.vanilla, rule.explain(rule.vanilla)))
                }
                model.disagreement(index)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "${rule.sites.size} ${if (rule.sites.size == 1) "place" else "places"} in the game's code or battle scripts hold this number; all are written together. Edits are not checked in game unless noted in the plan.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { model.set(index, rule.vanilla) }, modifier = Modifier.testTag("battle-rule-restore")) { Text(stringResource(Res.string.battle_rule_restore_the_game_s_value)) }
            }
        }
    }
}
