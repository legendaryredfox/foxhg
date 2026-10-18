package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.hiddenitem.HiddenItem
import io.github.legendaryredfox.foxhg.core.data.hiddenitem.HiddenItems
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * The hidden items of the ARM9 (Itemfinder spots). The vanilla table is full, so spots can be changed and removed, and
 * a removed one can be added again; the table can not grow past what the game reserved for it.
 */
class HiddenItemEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var table: HiddenItems? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    /** Why the last index typed was refused, or null. */
    var indexNotice: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(0)
        private set

    init {
        when (val loaded = HiddenItems.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    val selected: HiddenItem? get() = table?.entries?.getOrNull(selectedIndex)

    fun itemName(item: Int): String = names?.items?.getOrNull(item) ?: "Item $item"

    fun label(position: Int): String {
        val e = table?.entries?.getOrNull(position) ?: return "%03d".format(position)
        val quantity = if (e.quantity == 1) "" else " x${e.quantity}"
        return "%03d  %s%s".format(position, itemName(e.item), quantity)
    }

    fun rows(): List<Int> {
        val q = search.trim()
        return (table?.entries?.indices ?: IntRange.EMPTY).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) || table!!.entries[it].index.toString() == q }
    }

    fun select(position: Int) {
        if (position in 0 until (table?.entries?.size ?: 0)) { selectedIndex = position; indexNotice = null }
    }

    private fun apply(updated: HiddenItems) {
        when (val saved = HiddenItems.save(project, updated)) {
            is FoxResult.Ok -> { table = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }

    fun update(change: (HiddenItem) -> HiddenItem) {
        val t = table ?: return
        val current = selected ?: return
        apply(t.with(selectedIndex, change(current)))
    }

    /** Another spot with the same index would share its script and its "picked up" flag, so an index in use is refused. */
    fun isIndexFree(index: Int): Boolean = table?.entries?.withIndex()?.none { (i, e) -> i != selectedIndex && e.index == index } ?: true

    /** Sets the index of the selected spot, or says which spot already has it. */
    fun setIndex(index: Int) {
        if (isIndexFree(index)) {
            indexNotice = null
            update { it.copy(index = index) }
        } else {
            val owner = table?.entries?.indexOfFirst { it.index == index } ?: -1
            indexNotice = tr(Res.string.hidden_item_index_x_is_already_used_by_spot_x, index, owner)
        }
    }

    val canAdd: Boolean get() = table?.let { it.entries.size < it.capacity } ?: false

    fun add() {
        val t = table ?: return
        if (!canAdd) return
        apply(t.added(HiddenItem(item = 1, quantity = 1, unknown3 = 0, unknown4 = 0, index = t.freeIndex())))
        selectedIndex = t.entries.size
    }

    fun remove() {
        val t = table ?: return
        if (t.entries.isEmpty()) return
        apply(t.removed(selectedIndex))
        selectedIndex = selectedIndex.coerceAtMost(maxOf(0, t.entries.size - 2))
    }
}

@Composable
fun HiddenItemEditor(model: HiddenItemEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val table = model.table ?: return
    Row(modifier.fillMaxSize()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedIndex,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = stringResource(Res.string.hidden_item_search_items_or_an_index),
            label = model::label,
            onSelect = model::select,
            isEdited = { false },
            detail = { table.entries.getOrNull(it)?.let { e -> "#${e.index}" } },
        )
        val spot = model.selected
        DetailPane {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Chip(stringResource(Res.string.hidden_item_x_of_x_spots, table.entries.size, table.capacity))
                if (!model.canAdd) Text(stringResource(Res.string.hidden_item_the_game_reserves_room_for_x_spots, table.capacity), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (spot != null) {
                EditorHeader(model.selectedIndex.toString(), model.itemName(spot.item), trailing = {
                    OutlinedButton(onClick = model::remove, modifier = Modifier.testTag("hidden-remove")) { Text(stringResource(Res.string.learnset_remove)) }
                })
                SectionCard(stringResource(Res.string.hidden_item_spot), subtitle = stringResource(Res.string.hidden_item_what_the_itemfinder_points_at)) {
                    FieldFlow {
                        ItemPicker(stringResource(Res.string.evolution_item), spot.item, model.names?.items ?: emptyList(), { v -> model.update { it.copy(item = v) } }, Modifier.width(240.dp))
                        NumberField(stringResource(Res.string.ground_item_quantity), spot.quantity, 1..255, { v -> model.update { it.copy(quantity = v) } }, Modifier.width(100.dp))
                        NumberField(stringResource(Res.string.hidden_item_index), spot.index, 0..65535, model::setIndex, Modifier.width(100.dp))
                    }
                    model.indexNotice?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("hidden-index-notice")) }
                    Text(
                        stringResource(Res.string.hidden_item_script_x_and_flag_x_picked_up_foll, spot.script, spot.flag, spot.script),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = model::add, enabled = model.canAdd, modifier = Modifier.testTag("hidden-add")) { Text(stringResource(Res.string.hidden_item_add_a_spot)) }
        }
    }
}
