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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.pickup.PickupTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The Pickup ability (overlay 12): item tables, activation divisor and slot weights, written straight into the project on each edit. */
class PickupEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var table: PickupTable? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    init {
        when (val loaded = PickupTable.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    fun update(change: (PickupTable) -> PickupTable) {
        val updated = change(table ?: return)
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { table = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }
}

@Composable
fun PickupEditor(model: PickupEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val table = model.table ?: return
    val items = model.names?.items ?: emptyList()
    fun name(id: Int) = items.getOrNull(id) ?: "Item $id"
    val rolls = table.commonRolls()
    val activation = table.activationPercent
    fun percent(rolls: Int) = "%.2f%%".format(activation * rolls / 100.0)
    DetailPane(modifier.padding(16.dp)) {
        Text("Pickup", style = MaterialTheme.typography.headlineSmall)
        SectionCard(
            "Activation",
            subtitle = "After a battle, a Pokémon with Pickup and no held item activates with a 1 in divisor chance, then rolls 0 to 99. " +
                "The first slot whose weight is above the roll gives a common item; a roll of 98 or 99 that no weight catches gives a rare one",
        ) {
            FieldFlow {
                NumberField("Divisor", table.divisor, 1..255, { v -> model.update { it.copy(divisor = v) } }, Modifier.width(100.dp))
                Chip("Activates ${"%.2f".format(activation)}% of battles")
            }
            table.weights.forEachIndexed { slot, weight ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    NumberField("Slot ${slot + 1} weight", weight, 0..255, { v -> model.update { it.withWeight(slot, v) } }, Modifier.width(130.dp), tag = "Weight ${slot + 1}")
                    Chip("${rolls[slot]} of 100 rolls, ${percent(rolls[slot])} overall", emphasis = rolls[slot] == 0)
                }
            }
            val rare = table.rareRolls()
            Text(
                "Rare slots: ${rare[0]} and ${rare[1]} of 100 rolls (${percent(rare[0])} and ${percent(rare[1])} overall). Rolls that give nothing: ${table.missRolls()} of 100",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard("Common items", subtitle = "18 entries. A Pokémon of level bracket b (1 to 10 is 0, 11 to 20 is 1 ...) gets entry b + slot, so neighbouring brackets share entries") {
            FieldFlow {
                table.common.forEachIndexed { i, item ->
                    ItemPicker("Entry ${i + 1}", item, items, { v -> model.update { it.withCommon(i, v) } }, Modifier.width(210.dp))
                }
            }
        }
        SectionCard("Rare items", subtitle = "11 entries, picked as entry b + (99 - roll): bracket b with a roll of 99 gets entry b, a roll of 98 entry b + 1") {
            FieldFlow {
                table.rare.forEachIndexed { i, item ->
                    ItemPicker("Entry ${i + 1}", item, items, { v -> model.update { it.withRare(i, v) } }, Modifier.width(210.dp))
                }
            }
        }
        SectionCard("By level", subtitle = "What each level range can give, slot 1 to 9 for common items and the two rare ones") {
            for (bracket in 0 until PickupTable.BRACKETS) {
                Text(
                    "Level ${PickupTable.levelRange(bracket)}: " + (0 until PickupTable.SLOTS).joinToString(", ") { name(table.commonItem(bracket, it)) } +
                        ". Rare: " + (0 until 2).joinToString(", ") { name(table.rareItem(bracket, it)) },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
