package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.core.data.trade.NpcTrade

@Composable
fun TradeEditor(model: RecordEditorModel<NpcTrade>, modifier: Modifier = Modifier) {
    RecordListDetail(model, modifier) { m ->
        val species = model.names?.species ?: emptyList()
        val items = model.names?.items ?: emptyList()
        val abilities = model.names?.abilities ?: emptyList()
        EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)))
        SectionCard(stringResource(Res.string.trade_trade), subtitle = if (m.giveSpecies == m.askSpecies) "A loan: the NPC lends this Pokémon and takes the same species back (vanilla Rusty, Shuckie, Kenya and Volty)" else "The NPC gives the first Pokémon for the second") {
            FieldFlow {
                SpeciesPicker(stringResource(Res.string.trade_npc_gives), m.giveSpecies, species, { v -> model.update { it.copy(giveSpecies = v) } })
                SpeciesPicker(stringResource(Res.string.trade_player_offers), m.askSpecies, species, { v -> model.update { it.copy(askSpecies = v) } })
                ItemPicker(stringResource(Res.string.starter_held_item), m.heldItem, items, { v -> model.update { it.copy(heldItem = v) } })
                PickerField(stringResource(Res.string.trade_ability), m.ability, abilities, { v -> model.update { it.copy(ability = v) } })
            }
        }
        SectionCard(stringResource(Res.string.trade_individual_values)) {
            FieldFlow {
                NumberField(stringResource(Res.string.personal_hp), m.hpIv, 0..31, { v -> model.update { it.copy(hpIv = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.personal_attack), m.atkIv, 0..31, { v -> model.update { it.copy(atkIv = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.personal_defense), m.defIv, 0..31, { v -> model.update { it.copy(defIv = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.personal_sp_atk), m.spAtkIv, 0..31, { v -> model.update { it.copy(spAtkIv = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.personal_sp_def), m.spDefIv, 0..31, { v -> model.update { it.copy(spDefIv = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.personal_speed), m.speedIv, 0..31, { v -> model.update { it.copy(speedIv = v) } }, Modifier.width(80.dp))
            }
        }
        SectionCard(stringResource(Res.string.trade_contest_stats)) {
            FieldFlow {
                NumberField(stringResource(Res.string.trade_cool), m.cool, 0..255, { v -> model.update { it.copy(cool = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.evolution_beauty), m.beauty, 0..255, { v -> model.update { it.copy(beauty = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.trade_cute), m.cute, 0..255, { v -> model.update { it.copy(cute = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.trade_smart), m.smart, 0..255, { v -> model.update { it.copy(smart = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.trade_tough), m.tough, 0..255, { v -> model.update { it.copy(tough = v) } }, Modifier.width(80.dp))
                NumberField(stringResource(Res.string.trade_sheen), m.sheen, 0..255, { v -> model.update { it.copy(sheen = v) } }, Modifier.width(80.dp))
            }
        }
        SectionCard(stringResource(Res.string.trade_original_trainer), subtitle = stringResource(Res.string.trade_nickname_and_trainer_name_are_mess)) {
            FieldFlow {
                PickerField(stringResource(Res.string.trade_trainer_gender), m.otGender.coerceIn(0, NpcTrade.GENDERS.lastIndex), NpcTrade.GENDERS, { v -> model.update { it.copy(otGender = v) } }, Modifier.width(160.dp))
                NumberField(stringResource(Res.string.trade_trainer_id), m.otId.toInt(), 0..Int.MAX_VALUE, { v -> model.update { it.copy(otId = v.toLong()) } }, Modifier.width(130.dp))
                NumberField(stringResource(Res.string.trade_personality_value), m.pid.toInt(), 0..Int.MAX_VALUE, { v -> model.update { it.copy(pid = v.toLong()) } }, Modifier.width(150.dp))
                NumberField(stringResource(Res.string.trade_language_id_2_english), m.language, 0..255, { v -> model.update { it.copy(language = v) } }, Modifier.width(180.dp))
            }
        }
    }
}
