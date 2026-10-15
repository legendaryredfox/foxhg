package io.github.romulofer.foxhg.app

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.core.data.move.MoveData

@Composable
fun MoveEditor(model: RecordEditorModel<MoveData>, modifier: Modifier = Modifier) {
    RecordListDetail(model, modifier) { m ->
        val types = model.names?.types ?: emptyList()
        EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = { CsvButtons(model, MoveData.CSV, "moves") })
        SectionCard("Battle") {
            FieldFlow {
                PickerField("Type", m.type, types, { v -> model.update { it.copy(type = v) } })
                PickerField("Category", m.category, MoveData.CATEGORIES, { v -> model.update { it.copy(category = v) } }, Modifier.width(160.dp))
                NumberField("Power", m.power, 0..255, { v -> model.update { it.copy(power = v) } })
                NumberField("Accuracy (0 = always hits)", m.accuracy, 0..255, { v -> model.update { it.copy(accuracy = v) } }, Modifier.width(190.dp))
                NumberField("PP", m.pp, 0..255, { v -> model.update { it.copy(pp = v) } })
                NumberField("Priority", m.priority, -128..127, { v -> model.update { it.copy(priority = v) } })
            }
        }
        SectionCard("Effect") {
            FieldFlow {
                NumberField("Effect sequence", m.effect, 0..65535, { v -> model.update { it.copy(effect = v) } }, Modifier.width(140.dp))
                NumberField("Effect chance %", m.effectChance, 0..255, { v -> model.update { it.copy(effectChance = v) } }, Modifier.width(140.dp))
                NumberField("Range", m.range, 0..65535, { v -> model.update { it.copy(range = v) } })
            }
        }
        SectionCard("Flags and contest data", subtitle = "Raw values; their individual meanings are not decoded yet") {
            FieldFlow {
                NumberField("Flags", m.flags, 0..255, { v -> model.update { it.copy(flags = v) } })
                NumberField("Contest effect", m.contestEffect, 0..255, { v -> model.update { it.copy(contestEffect = v) } }, Modifier.width(130.dp))
                NumberField("Contest type", m.contestType, 0..255, { v -> model.update { it.copy(contestType = v) } }, Modifier.width(130.dp))
            }
        }
    }
}
