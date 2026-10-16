package io.github.legendaryredfox.foxhg.app

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
        SectionCard("Trade", subtitle = if (m.giveSpecies == m.askSpecies) "A loan: the NPC lends this Pokémon and takes the same species back (vanilla Rusty, Shuckie, Kenya and Volty)" else "The NPC gives the first Pokémon for the second") {
            FieldFlow {
                SpeciesPicker("NPC gives", m.giveSpecies, species, { v -> model.update { it.copy(giveSpecies = v) } })
                SpeciesPicker("Player offers", m.askSpecies, species, { v -> model.update { it.copy(askSpecies = v) } })
                ItemPicker("Held item", m.heldItem, items, { v -> model.update { it.copy(heldItem = v) } })
                PickerField("Ability", m.ability, abilities, { v -> model.update { it.copy(ability = v) } })
            }
        }
        SectionCard("Individual values") {
            FieldFlow {
                NumberField("HP", m.hpIv, 0..31, { v -> model.update { it.copy(hpIv = v) } }, Modifier.width(80.dp))
                NumberField("Attack", m.atkIv, 0..31, { v -> model.update { it.copy(atkIv = v) } }, Modifier.width(80.dp))
                NumberField("Defense", m.defIv, 0..31, { v -> model.update { it.copy(defIv = v) } }, Modifier.width(80.dp))
                NumberField("Sp. Atk", m.spAtkIv, 0..31, { v -> model.update { it.copy(spAtkIv = v) } }, Modifier.width(80.dp))
                NumberField("Sp. Def", m.spDefIv, 0..31, { v -> model.update { it.copy(spDefIv = v) } }, Modifier.width(80.dp))
                NumberField("Speed", m.speedIv, 0..31, { v -> model.update { it.copy(speedIv = v) } }, Modifier.width(80.dp))
            }
        }
        SectionCard("Contest stats") {
            FieldFlow {
                NumberField("Cool", m.cool, 0..255, { v -> model.update { it.copy(cool = v) } }, Modifier.width(80.dp))
                NumberField("Beauty", m.beauty, 0..255, { v -> model.update { it.copy(beauty = v) } }, Modifier.width(80.dp))
                NumberField("Cute", m.cute, 0..255, { v -> model.update { it.copy(cute = v) } }, Modifier.width(80.dp))
                NumberField("Smart", m.smart, 0..255, { v -> model.update { it.copy(smart = v) } }, Modifier.width(80.dp))
                NumberField("Tough", m.tough, 0..255, { v -> model.update { it.copy(tough = v) } }, Modifier.width(80.dp))
                NumberField("Sheen", m.sheen, 0..255, { v -> model.update { it.copy(sheen = v) } }, Modifier.width(80.dp))
            }
        }
        SectionCard("Original trainer", subtitle = "Nickname and trainer name are message bank 200 (entry n and n + 13); edit them in the text editor") {
            FieldFlow {
                PickerField("Trainer gender", m.otGender.coerceIn(0, NpcTrade.GENDERS.lastIndex), NpcTrade.GENDERS, { v -> model.update { it.copy(otGender = v) } }, Modifier.width(160.dp))
                NumberField("Trainer ID", m.otId.toInt(), 0..Int.MAX_VALUE, { v -> model.update { it.copy(otId = v.toLong()) } }, Modifier.width(130.dp))
                NumberField("Personality value", m.pid.toInt(), 0..Int.MAX_VALUE, { v -> model.update { it.copy(pid = v.toLong()) } }, Modifier.width(150.dp))
                NumberField("Language id (2 = English)", m.language, 0..255, { v -> model.update { it.copy(language = v) } }, Modifier.width(180.dp))
            }
        }
    }
}
