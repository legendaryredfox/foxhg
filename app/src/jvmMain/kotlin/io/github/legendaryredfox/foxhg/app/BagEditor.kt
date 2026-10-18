package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.save.BagData
import io.github.legendaryredfox.foxhg.core.save.BagPocket
import io.github.legendaryredfox.foxhg.core.save.BagSlot

/** The bag of the save: pick a pocket, change the quantity of an item or its kind, remove it or add another. Written to the file only by the Write button. */
@Composable
fun SaveBagScreen(model: SaveEditorModel, names: GameNames?, modifier: Modifier = Modifier) {
    val bag = model.bag
    DetailPane(modifier.padding(16.dp)) {
        EditorHeader("", model.path.fileName.toString(), trailing = { SaveStatus(model) })
        if (bag == null) {
            Text(stringResource(Res.string.bag_this_save_has_no_sound_general_blo), color = MaterialTheme.colorScheme.error)
            return@DetailPane
        }
        var pocketIndex by remember { mutableStateOf(0) }
        val pocket = BagPocket.entries[pocketIndex]
        val itemNames = remember(names) { names?.items ?: List(BagData.MAX_ITEM + 1) { "#$it" } }
        SectionCard(stringResource(Res.string.bag_pockets)) {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BagPocket.entries.forEachIndexed { i, p ->
                    val label = "${p.label} ${bag.items(p).size}/${p.capacity}"
                    if (i == pocketIndex) Button(onClick = { pocketIndex = i }, modifier = Modifier.testTag("bag-pocket:$i")) { Text(label) }
                    else OutlinedButton(onClick = { pocketIndex = i }, modifier = Modifier.testTag("bag-pocket:$i")) { Text(label) }
                }
            }
        }
        fun replace(list: List<BagSlot>) = model.updateBag { it.withItems(pocket, list) }
        val list = bag.items(pocket)
        SectionCard(pocket.label, subtitle = stringResource(Res.string.bag_each_item_once_1_to_x_of_it_the_ga, pocket.maxQuantity)) {
            if (list.isEmpty()) Text(stringResource(Res.string.pc_empty), style = MaterialTheme.typography.bodyMedium)
            list.forEachIndexed { i, slot ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ItemPicker(stringResource(Res.string.evolution_item), slot.id, itemNames, { v -> replace(list.toMutableList().also { it[i] = slot.copy(id = v) }) }, Modifier.width(240.dp))
                    NumberField(stringResource(Res.string.ground_item_quantity), slot.quantity, 1..pocket.maxQuantity, { v -> replace(list.toMutableList().also { it[i] = slot.copy(quantity = v) }) }, Modifier.width(110.dp), tag = "Quantity $i")
                    OutlinedButton(onClick = { replace(list.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.testTag("bag-remove:$i")) { Text(stringResource(Res.string.learnset_remove)) }
                }
            }
        }
        var newItem by remember(pocketIndex) { mutableStateOf(1) }
        var newQuantity by remember(pocketIndex) { mutableStateOf(1) }
        SectionCard(stringResource(Res.string.bag_add_to_x, pocket.label), subtitle = stringResource(Res.string.bag_an_item_the_pocket_already_has_get)) {
            FieldFlow {
                ItemPicker(stringResource(Res.string.evolution_item), newItem, itemNames, { newItem = it }, Modifier.width(240.dp))
                NumberField(stringResource(Res.string.ground_item_quantity), newQuantity, 1..pocket.maxQuantity, { newQuantity = it }, Modifier.width(110.dp), tag = "New quantity")
            }
            Button(onClick = { model.updateBag { it.adding(pocket, newItem, newQuantity) } }, modifier = Modifier.testTag("bag-add")) { Text(stringResource(Res.string.encounter_add)) }
        }
    }
}
