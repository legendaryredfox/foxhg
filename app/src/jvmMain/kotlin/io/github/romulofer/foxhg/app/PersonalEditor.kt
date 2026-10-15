package io.github.romulofer.foxhg.app

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
import io.github.romulofer.foxhg.app.ui.PokemonPortraits
import io.github.romulofer.foxhg.app.ui.ItemPicker
import io.github.romulofer.foxhg.app.ui.CryButton
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.PokemonIcon
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.app.ui.StatBar
import io.github.romulofer.foxhg.app.ui.ToggleChip
import io.github.romulofer.foxhg.core.data.personal.PersonalData

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
            placeholder = "Search species…",
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
    EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = { CryButton(model.selectedIndex); PokemonPortraits(model.selectedIndex) })

    SectionCard("Base stats", subtitle = "Total ${p.baseStatTotal}") {
        StatRow("HP", p.baseHp) { v -> model.update { it.copy(baseHp = v) } }
        StatRow("Attack", p.baseAttack) { v -> model.update { it.copy(baseAttack = v) } }
        StatRow("Defense", p.baseDefense) { v -> model.update { it.copy(baseDefense = v) } }
        StatRow("Sp. Atk", p.baseSpAttack) { v -> model.update { it.copy(baseSpAttack = v) } }
        StatRow("Sp. Def", p.baseSpDefense) { v -> model.update { it.copy(baseSpDefense = v) } }
        StatRow("Speed", p.baseSpeed) { v -> model.update { it.copy(baseSpeed = v) } }
    }

    SectionCard("Types and abilities") {
        FieldFlow {
            PickerField("Type 1", p.type1, types, { v -> model.update { it.copy(type1 = v) } })
            PickerField("Type 2", p.type2, types, { v -> model.update { it.copy(type2 = v) } })
            PickerField("Ability 1", p.ability1, abilities, { v -> model.update { it.copy(ability1 = v) } })
            PickerField("Ability 2", p.ability2, abilities, { v -> model.update { it.copy(ability2 = v) } })
        }
    }

    SectionCard("Catching and growth") {
        FieldFlow {
            NumberField("Catch rate", p.catchRate, 0..255, { v -> model.update { it.copy(catchRate = v) } })
            NumberField("Base EXP", p.baseExp, 0..255, { v -> model.update { it.copy(baseExp = v) } })
            NumberField("Friendship", p.baseFriendship, 0..255, { v -> model.update { it.copy(baseFriendship = v) } })
            NumberField("Safari flee", p.safariFleeRate, 0..255, { v -> model.update { it.copy(safariFleeRate = v) } })
            NumberField("Hatch cycles", p.hatchCycles, 0..255, { v -> model.update { it.copy(hatchCycles = v) } })
            NumberField("Gender (255 none)", p.genderRatio, 0..255, { v -> model.update { it.copy(genderRatio = v) } }, Modifier.width(140.dp))
        }
        FieldFlow {
            PickerField("Growth rate", p.growthRate, GROWTH_RATES, { v -> model.update { it.copy(growthRate = v) } })
            PickerField("Egg group 1", p.eggGroup1, EGG_GROUPS, { v -> model.update { it.copy(eggGroup1 = v) } })
            PickerField("Egg group 2", p.eggGroup2, EGG_GROUPS, { v -> model.update { it.copy(eggGroup2 = v) } })
        }
    }

    SectionCard("EV yield", subtitle = "Effort points given when this Pokémon is defeated (0–3 each)") {
        FieldFlow {
            NumberField("HP", p.evHp, 0..3, { v -> model.update { it.copy(evHp = v) } }, Modifier.width(70.dp))
            NumberField("Atk", p.evAttack, 0..3, { v -> model.update { it.copy(evAttack = v) } }, Modifier.width(70.dp))
            NumberField("Def", p.evDefense, 0..3, { v -> model.update { it.copy(evDefense = v) } }, Modifier.width(70.dp))
            NumberField("SpA", p.evSpAttack, 0..3, { v -> model.update { it.copy(evSpAttack = v) } }, Modifier.width(70.dp))
            NumberField("SpD", p.evSpDefense, 0..3, { v -> model.update { it.copy(evSpDefense = v) } }, Modifier.width(70.dp))
            NumberField("Spe", p.evSpeed, 0..3, { v -> model.update { it.copy(evSpeed = v) } }, Modifier.width(70.dp))
        }
    }

    SectionCard("Held items in the wild") {
        FieldFlow {
            ItemPicker("Common (50%)", p.heldItemCommon, items, { v -> model.update { it.copy(heldItemCommon = v) } }, Modifier.width(240.dp))
            ItemPicker("Rare (5%)", p.heldItemRare, items, { v -> model.update { it.copy(heldItemRare = v) } }, Modifier.width(240.dp))
        }
    }

    SectionCard("TM / HM compatibility") {
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
