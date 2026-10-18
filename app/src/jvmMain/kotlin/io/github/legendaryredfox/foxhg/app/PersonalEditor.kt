package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.PokemonPortraits
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.CryButton
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.PokemonIcon
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.StatBar
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalData

private val GROWTH_RATES = listOf("Medium Fast", "Erratic", "Fluctuating", "Medium Slow", "Fast", "Slow")
private val EGG_GROUPS = listOf(
    "-", "Monster", "Water 1", "Bug", "Flying", "Field", "Fairy", "Grass", "Human-Like",
    "Water 3", "Mineral", "Amorphous", "Water 2", "Ditto", "Dragon", "Undiscovered",
)

@Composable
fun PersonalEditor(model: PersonalEditorModel, modifier: Modifier = Modifier) {
    model.loadError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier)
        return
    }
    Row(modifier.fillMaxWidth()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedIndex,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = stringResource(Res.string.personal_search_species),
            label = model::label,
            onSelect = model::select,
            isEdited = model::isEdited,
            leading = { PokemonIcon(it) },
        )
        model.selected?.let { record -> DetailPane { Form(model, record) } }
    }
}

@Composable
private fun Form(model: PersonalEditorModel, p: PersonalData) {
    val names = model.names
    val types = names?.types ?: emptyList()
    val abilities = names?.abilities ?: emptyList()
    val items = names?.items ?: emptyList()
    EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = {
        CsvButtons(PersonalData.CSV, "personal data", { model.records }, { nameOf(model.label(it)) }) { change -> model.updateAll(change(model.records)) }
        CryButton(model.selectedIndex)
        PokemonPortraits(model.selectedIndex)
    })

    SectionCard(stringResource(Res.string.personal_base_stats), subtitle = stringResource(Res.string.personal_total_x, p.baseStatTotal)) {
        StatRow(stringResource(Res.string.personal_hp), p.baseHp) { v -> model.update { it.copy(baseHp = v) } }
        StatRow(stringResource(Res.string.personal_attack), p.baseAttack) { v -> model.update { it.copy(baseAttack = v) } }
        StatRow(stringResource(Res.string.personal_defense), p.baseDefense) { v -> model.update { it.copy(baseDefense = v) } }
        StatRow(stringResource(Res.string.personal_sp_atk), p.baseSpAttack) { v -> model.update { it.copy(baseSpAttack = v) } }
        StatRow(stringResource(Res.string.personal_sp_def), p.baseSpDefense) { v -> model.update { it.copy(baseSpDefense = v) } }
        StatRow(stringResource(Res.string.personal_speed), p.baseSpeed) { v -> model.update { it.copy(baseSpeed = v) } }
    }

    SectionCard(stringResource(Res.string.personal_types_and_abilities)) {
        FieldFlow {
            PickerField(stringResource(Res.string.personal_type_1), p.type1, types, { v -> model.update { it.copy(type1 = v) } })
            PickerField(stringResource(Res.string.personal_type_2), p.type2, types, { v -> model.update { it.copy(type2 = v) } })
            PickerField(stringResource(Res.string.personal_ability_1), p.ability1, abilities, { v -> model.update { it.copy(ability1 = v) } })
            PickerField(stringResource(Res.string.personal_ability_2), p.ability2, abilities, { v -> model.update { it.copy(ability2 = v) } })
        }
    }

    SectionCard(stringResource(Res.string.personal_catching_and_growth)) {
        FieldFlow {
            NumberField(stringResource(Res.string.personal_catch_rate), p.catchRate, 0..255, { v -> model.update { it.copy(catchRate = v) } })
            NumberField(stringResource(Res.string.personal_base_exp), p.baseExp, 0..255, { v -> model.update { it.copy(baseExp = v) } })
            NumberField(stringResource(Res.string.personal_friendship), p.baseFriendship, 0..255, { v -> model.update { it.copy(baseFriendship = v) } })
            NumberField(stringResource(Res.string.personal_safari_flee), p.safariFleeRate, 0..255, { v -> model.update { it.copy(safariFleeRate = v) } })
            NumberField(stringResource(Res.string.personal_hatch_cycles), p.hatchCycles, 0..255, { v -> model.update { it.copy(hatchCycles = v) } })
            NumberField(stringResource(Res.string.personal_gender_255_none), p.genderRatio, 0..255, { v -> model.update { it.copy(genderRatio = v) } }, Modifier.width(140.dp))
        }
        FieldFlow {
            PickerField(stringResource(Res.string.personal_growth_rate), p.growthRate, GROWTH_RATES, { v -> model.update { it.copy(growthRate = v) } })
            PickerField(stringResource(Res.string.personal_egg_group_1), p.eggGroup1, EGG_GROUPS, { v -> model.update { it.copy(eggGroup1 = v) } })
            PickerField(stringResource(Res.string.personal_egg_group_2), p.eggGroup2, EGG_GROUPS, { v -> model.update { it.copy(eggGroup2 = v) } })
        }
    }

    SectionCard(stringResource(Res.string.personal_ev_yield), subtitle = stringResource(Res.string.personal_effort_points_given_when_this_pok)) {
        FieldFlow {
            NumberField(stringResource(Res.string.personal_hp), p.evHp, 0..3, { v -> model.update { it.copy(evHp = v) } }, Modifier.width(70.dp))
            NumberField(stringResource(Res.string.personal_atk), p.evAttack, 0..3, { v -> model.update { it.copy(evAttack = v) } }, Modifier.width(70.dp))
            NumberField(stringResource(Res.string.personal_def), p.evDefense, 0..3, { v -> model.update { it.copy(evDefense = v) } }, Modifier.width(70.dp))
            NumberField(stringResource(Res.string.personal_spa), p.evSpAttack, 0..3, { v -> model.update { it.copy(evSpAttack = v) } }, Modifier.width(70.dp))
            NumberField(stringResource(Res.string.personal_spd), p.evSpDefense, 0..3, { v -> model.update { it.copy(evSpDefense = v) } }, Modifier.width(70.dp))
            NumberField(stringResource(Res.string.personal_spe), p.evSpeed, 0..3, { v -> model.update { it.copy(evSpeed = v) } }, Modifier.width(70.dp))
        }
    }

    SectionCard(stringResource(Res.string.personal_held_items_in_the_wild)) {
        FieldFlow {
            ItemPicker("Common (50%)", p.heldItemCommon, items, { v -> model.update { it.copy(heldItemCommon = v) } }, Modifier.width(240.dp))
            ItemPicker("Rare (5%)", p.heldItemRare, items, { v -> model.update { it.copy(heldItemRare = v) } }, Modifier.width(240.dp))
        }
    }

    SectionCard(stringResource(Res.string.personal_tm_hm_compatibility)) {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (index in 0 until 100) {
                val label = if (index < 92) "TM%02d".format(index + 1) else "HM%02d".format(index - 91)
                ToggleChip(label, p.learnsTmHm(index), { on -> model.update { it.withTmHm(index, on) } })
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NumberField("", value, 0..255, onChange, Modifier.width(72.dp), tag = "stat:$label")
        StatBar(value, modifier = Modifier.weight(1f))
    }
}
