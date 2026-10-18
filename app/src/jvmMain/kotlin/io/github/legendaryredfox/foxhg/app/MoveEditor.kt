package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.data.move.MoveData

/** The flag bits of a move (byte 11), lowest bit first. */
private val FLAG_NAMES = listOf("Contact", "Protect", "Magic Coat", "Snatch", "Mirror Move", "King's Rock", "Keep HP bar", "Hide shadow")

private val CONTEST_TYPES = listOf("Cool", "Beauty", "Cute", "Smart", "Tough")

/** Who a move hits, from its range value (one flag bit, or 0). */
private fun rangeName(range: Int): String = when (range) {
    0 -> "One adjacent opponent or ally"
    1 -> "Varies with the effect"
    2 -> "One random opponent"
    4 -> "All adjacent opponents"
    8 -> "All adjacent Pokémon"
    16 -> "The user"
    32 -> "The user's side"
    64 -> "Every side"
    128 -> "The opponent's side"
    256 -> "One adjacent ally"
    512 -> "The user or one ally"
    1024 -> "One adjacent opponent"
    else -> "Several range bits"
}

@Composable
fun MoveEditor(model: RecordEditorModel<MoveData>, modifier: Modifier = Modifier) {
    RecordListDetail(model, modifier) { m ->
        val types = model.names?.types ?: emptyList()
        EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = { CsvButtons(model, MoveData.CSV, "moves") })
        SectionCard(stringResource(Res.string.move_battle)) {
            FieldFlow {
                PickerField(stringResource(Res.string.move_type), m.type, types, { v -> model.update { it.copy(type = v) } })
                PickerField(stringResource(Res.string.move_category), m.category, MoveData.CATEGORIES, { v -> model.update { it.copy(category = v) } }, Modifier.width(160.dp))
                NumberField(stringResource(Res.string.move_power), m.power, 0..255, { v -> model.update { it.copy(power = v) } })
                NumberField(stringResource(Res.string.move_accuracy_0_always_hits), m.accuracy, 0..255, { v -> model.update { it.copy(accuracy = v) } }, Modifier.width(190.dp))
                NumberField(stringResource(Res.string.move_pp), m.pp, 0..255, { v -> model.update { it.copy(pp = v) } })
                NumberField(stringResource(Res.string.move_priority), m.priority, -128..127, { v -> model.update { it.copy(priority = v) } })
            }
        }
        SectionCard(stringResource(Res.string.move_effect)) {
            FieldFlow {
                NumberField(stringResource(Res.string.move_effect_sequence), m.effect, 0..65535, { v -> model.update { it.copy(effect = v) } }, Modifier.width(140.dp))
                NumberField("Effect chance %", m.effectChance, 0..255, { v -> model.update { it.copy(effectChance = v) } }, Modifier.width(140.dp))
                NumberField(stringResource(Res.string.move_range), m.range, 0..65535, { v -> model.update { it.copy(range = v) } })
                Text(rangeName(m.range), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        SectionCard(stringResource(Res.string.move_flags), subtitle = stringResource(Res.string.move_interactions_with_abilities_items)) {
            FieldFlow {
                FLAG_NAMES.forEachIndexed { bit, name ->
                    ToggleChip(name, m.flags and (1 shl bit) != 0, { on -> model.update { it.copy(flags = if (on) it.flags or (1 shl bit) else it.flags and (1 shl bit).inv()) } })
                }
            }
        }
        SectionCard(stringResource(Res.string.move_contest_data), subtitle = stringResource(Res.string.move_super_contest_condition_and_appeal)) {
            FieldFlow {
                PickerField(stringResource(Res.string.move_contest_condition), m.contestType, CONTEST_TYPES, { v -> model.update { it.copy(contestType = v) } }, Modifier.width(180.dp))
                NumberField(stringResource(Res.string.move_contest_appeal), m.contestEffect, 0..255, { v -> model.update { it.copy(contestEffect = v) } }, Modifier.width(130.dp))
            }
        }
    }
}
