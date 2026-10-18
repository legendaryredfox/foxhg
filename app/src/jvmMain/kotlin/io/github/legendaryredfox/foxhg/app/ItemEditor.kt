package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.ItemIcon
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.data.item.ItemData

@Composable
fun ItemEditor(model: RecordEditorModel<ItemData>, modifier: Modifier = Modifier) {
    RecordListDetail(model, modifier, leading = { ItemIcon(it) }) { m ->
        val types = model.names?.types ?: emptyList()
        EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = { CsvButtons(model, ItemData.CSV, "items") })
        SectionCard(stringResource(Res.string.item_general)) {
            FieldFlow {
                NumberField(stringResource(Res.string.item_price), m.price, 0..65535, { v -> model.update { it.copy(price = v) } })
                PickerField(stringResource(Res.string.item_bag_pocket), m.fieldPocket, ItemData.FIELD_POCKETS, { v -> model.update { it.copy(fieldPocket = v) } })
                ToggleChip(stringResource(Res.string.item_cannot_be_tossed), m.preventToss, { v -> model.update { it.copy(preventToss = v) } })
                ToggleChip(stringResource(Res.string.item_selectable), m.selectable, { v -> model.update { it.copy(selectable = v) } })
            }
        }
        SectionCard(stringResource(Res.string.item_held_and_thrown)) {
            FieldFlow {
                NumberField(stringResource(Res.string.item_hold_effect), m.holdEffect, 0..255, { v -> model.update { it.copy(holdEffect = v) } })
                NumberField(stringResource(Res.string.item_hold_param), m.holdEffectParam, 0..255, { v -> model.update { it.copy(holdEffectParam = v) } })
                NumberField(stringResource(Res.string.item_fling_effect), m.flingEffect, 0..255, { v -> model.update { it.copy(flingEffect = v) } })
                NumberField(stringResource(Res.string.item_fling_power), m.flingPower, 0..255, { v -> model.update { it.copy(flingPower = v) } })
                NumberField(stringResource(Res.string.item_pluck_effect), m.pluckEffect, 0..255, { v -> model.update { it.copy(pluckEffect = v) } })
                NumberField(stringResource(Res.string.item_natural_gift_power), m.naturalGiftPower, 0..255, { v -> model.update { it.copy(naturalGiftPower = v) } }, Modifier.width(150.dp))
                PickerField(stringResource(Res.string.item_natural_gift_type), m.naturalGiftType, types + List((31 - types.size).coerceAtLeast(0)) { "Type ${types.size + it}" } + "None", { v -> model.update { it.copy(naturalGiftType = v) } })
            }
        }
        SectionCard(stringResource(Res.string.item_use)) {
            FieldFlow {
                NumberField(stringResource(Res.string.item_field_use), m.fieldUseFunc, 0..255, { v -> model.update { it.copy(fieldUseFunc = v) } })
                PickerField(stringResource(Res.string.item_battle_use), m.battleUseFunc.coerceAtMost(ItemData.BATTLE_USE.lastIndex), ItemData.BATTLE_USE, { v -> model.update { it.copy(battleUseFunc = v) } })
                NumberField(stringResource(Res.string.item_party_use), m.partyUse, 0..255, { v -> model.update { it.copy(partyUse = v) } })
            }
            FieldFlow {
                ItemData.BATTLE_POCKETS.forEachIndexed { bit, name ->
                    ToggleChip(stringResource(Res.string.item_battle_x, name), (m.battlePocket shr bit) and 1 == 1, { on ->
                        model.update { it.copy(battlePocket = if (on) it.battlePocket or (1 shl bit) else it.battlePocket and (1 shl bit).inv()) }
                    })
                }
            }
        }
        SectionCard(stringResource(Res.string.item_party_effect_bytes), subtitle = stringResource(Res.string.item_raw_meaning_depends_on_the_item_s)) {
            FieldFlow {
                m.partyParams.forEachIndexed { i, v ->
                    NumberField("[$i]", v, 0..255, { nv -> model.update { it.copy(partyParams = it.partyParams.toMutableList().also { l -> l[i] = nv }) } }, Modifier.width(80.dp))
                }
            }
        }
    }
}
