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
import io.github.romulofer.foxhg.app.ui.ToggleChip
import io.github.romulofer.foxhg.core.data.item.ItemData

@Composable
fun ItemEditor(model: RecordEditorModel<ItemData>, modifier: Modifier = Modifier) {
    RecordListDetail(model, modifier) { m ->
        val types = model.names?.types ?: emptyList()
        EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = { CsvButtons(model, ItemData.CSV, "items") })
        SectionCard("General") {
            FieldFlow {
                NumberField("Price", m.price, 0..65535, { v -> model.update { it.copy(price = v) } })
                PickerField("Bag pocket", m.fieldPocket, ItemData.FIELD_POCKETS, { v -> model.update { it.copy(fieldPocket = v) } })
                ToggleChip("Cannot be tossed", m.preventToss, { v -> model.update { it.copy(preventToss = v) } })
                ToggleChip("Selectable", m.selectable, { v -> model.update { it.copy(selectable = v) } })
            }
        }
        SectionCard("Held and thrown") {
            FieldFlow {
                NumberField("Hold effect", m.holdEffect, 0..255, { v -> model.update { it.copy(holdEffect = v) } })
                NumberField("Hold param", m.holdEffectParam, 0..255, { v -> model.update { it.copy(holdEffectParam = v) } })
                NumberField("Fling effect", m.flingEffect, 0..255, { v -> model.update { it.copy(flingEffect = v) } })
                NumberField("Fling power", m.flingPower, 0..255, { v -> model.update { it.copy(flingPower = v) } })
                NumberField("Pluck effect", m.pluckEffect, 0..255, { v -> model.update { it.copy(pluckEffect = v) } })
                NumberField("Natural Gift power", m.naturalGiftPower, 0..255, { v -> model.update { it.copy(naturalGiftPower = v) } }, Modifier.width(150.dp))
                PickerField("Natural Gift type", m.naturalGiftType, types + List((31 - types.size).coerceAtLeast(0)) { "Type ${types.size + it}" } + "None", { v -> model.update { it.copy(naturalGiftType = v) } })
            }
        }
        SectionCard("Use") {
            FieldFlow {
                NumberField("Field use", m.fieldUseFunc, 0..255, { v -> model.update { it.copy(fieldUseFunc = v) } })
                PickerField("Battle use", m.battleUseFunc.coerceAtMost(ItemData.BATTLE_USE.lastIndex), ItemData.BATTLE_USE, { v -> model.update { it.copy(battleUseFunc = v) } })
                NumberField("Party use", m.partyUse, 0..255, { v -> model.update { it.copy(partyUse = v) } })
            }
            FieldFlow {
                ItemData.BATTLE_POCKETS.forEachIndexed { bit, name ->
                    ToggleChip("Battle: $name", (m.battlePocket shr bit) and 1 == 1, { on ->
                        model.update { it.copy(battlePocket = if (on) it.battlePocket or (1 shl bit) else it.battlePocket and (1 shl bit).inv()) }
                    })
                }
            }
        }
        SectionCard("Party effect bytes", subtitle = "Raw; meaning depends on the item's effect") {
            FieldFlow {
                m.partyParams.forEachIndexed { i, v ->
                    NumberField("[$i]", v, 0..255, { nv -> model.update { it.copy(partyParams = it.partyParams.toMutableList().also { l -> l[i] = nv }) } }, Modifier.width(80.dp))
                }
            }
        }
    }
}
