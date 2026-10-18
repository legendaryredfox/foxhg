package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.item.ItemData
import io.github.legendaryredfox.foxhg.core.data.mart.BadgeMartEntry
import io.github.legendaryredfox.foxhg.core.data.mart.MartGrowth
import io.github.legendaryredfox.foxhg.core.data.mart.Marts
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * The Poké Marts of the ARM9: rows 0 to 29 are the special marts (`SpMartScreen 0` to `29`, so the number in the list is the one the
 * script uses), the last row is the standard clerk's badge list. Edits are written straight into the project; a special mart can lose items and take them back, not hold more than it had.
 */
class MartEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    private val original: Marts? = (Marts.load(project) as? FoxResult.Ok)?.value
    private val prices: List<Int> = (ItemData.load(project) as? FoxResult.Ok)?.value?.records?.map { it.price } ?: emptyList()
    var marts: Marts? by mutableStateOf(original)
        private set
    var error: String? by mutableStateOf(if (original == null) (Marts.load(project) as? FoxResult.Err)?.message else null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(0)
        private set

    val rowCount: Int get() = 1 + (marts?.specials?.size ?: 0)

    /** True for the last row, the badge list. */
    fun isBadgeRow(row: Int): Boolean = row == (marts?.specials?.size ?: 0)

    fun price(item: Int): Int? = prices.getOrNull(item)

    fun itemName(item: Int): String = names?.items?.getOrNull(item) ?: "Item $item"

    fun label(row: Int): String {
        val m = marts ?: return ""
        return if (isBadgeRow(row)) "$row  Standard mart (by badges)" else "$row  ${summary(m.specials[row])}"
    }

    private fun summary(items: List<Int>): String = items.take(3).joinToString(", ") { itemName(it) } + if (items.size > 3) ", ..." else ""

    fun rows(): List<Int> {
        val q = search.trim()
        return (0 until rowCount).filter { q.isEmpty() || (marts?.let { m -> if (isBadgeRow(it)) "standard" else m.specials[it].joinToString { i -> itemName(i) } } ?: "").contains(q, ignoreCase = true) || label(it).contains(q, ignoreCase = true) }
    }

    fun select(row: Int) {
        if (row in 0 until rowCount) selectedIndex = row
    }

    fun isEdited(row: Int): Boolean {
        val now = marts ?: return false
        val was = original ?: return false
        return if (isBadgeRow(row)) now.badge != was.badge else now.specials[row] != was.specials[row]
    }

    fun update(change: (Marts) -> Marts) {
        val updated = change(marts ?: return)
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { marts = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }
}

@Composable
fun MartEditor(model: MartEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier)
        return
    }
    val marts = model.marts ?: return
    Row(modifier.fillMaxWidth()) {
        ListPane(
            rows = model.rows(), selected = model.selectedIndex, search = model.search, onSearch = { model.search = it },
            placeholder = stringResource(Res.string.mart_search_marts_or_items), label = model::label, onSelect = model::select, isEdited = model::isEdited,
        )
        DetailPane {
            if (model.isBadgeRow(model.selectedIndex)) BadgeMart(model, marts) else SpecialMart(model, marts, model.selectedIndex)
        }
    }
}

@Composable
private fun BadgeMart(model: MartEditorModel, marts: Marts) {
    val items = model.names?.items ?: emptyList()
    EditorHeader(model.selectedIndex.toString(), "Standard mart")
    SectionCard(
        stringResource(Res.string.mart_items_by_badges),
        subtitle = stringResource(Res.string.mart_the_clerk_of_the_ordinary_pok_mart),
    ) {
        marts.badge.forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                ItemPicker(stringResource(Res.string.mart_item_x, i + 1), e.item, items, { v -> model.update { it.withBadge(i, e.copy(item = v)) } }, Modifier.width(240.dp))
                NumberField(stringResource(Res.string.mart_tier), e.tier, 1..6, { v -> model.update { it.withBadge(i, e.copy(tier = v)) } }, Modifier.width(80.dp), tag = "Tier ${i + 1}")
                model.price(e.item)?.let { Chip("$it") }
            }
        }
    }
}

@Composable
private fun SpecialMart(model: MartEditorModel, marts: Marts, mart: Int) {
    val items = model.names?.items ?: emptyList()
    val list = marts.specials[mart]
    EditorHeader(mart.toString(), "Special mart $mart", trailing = { if (marts.isMoved(mart)) Chip(stringResource(Res.string.mart_moved_to_the_synthetic_overlay), emphasis = true) })
    SectionCard(
        stringResource(Res.string.editor_items),
        subtitle = "Opened by the script command SpMartScreen $mart. Prices are set per item in the Items editor. " + when {
            marts.growth == MartGrowth.AVAILABLE -> "It holds ${marts.capacity[mart]} items in the ARM9; more are kept in the synthetic overlay (${marts.areaBytes} of ${HgssLayout.MART_AREA_SIZE} bytes used by all marts)."
            marts.growth == MartGrowth.NEEDS_EXPANSION -> "It holds up to ${marts.capacity[mart]} items (${list.size} now): the lists sit one after another in the ARM9. Apply the ARM9 expansion patch in the ROM toolbox to let marts grow."
            else -> "It holds up to ${marts.capacity[mart]} items: the mart area of the synthetic overlay is in use by something else, so marts cannot grow."
        },
    ) {
        list.forEachIndexed { slot, item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                ItemPicker(stringResource(Res.string.mart_item_x, slot + 1), item, items, { v -> model.update { it.withItem(mart, slot, v) } }, Modifier.width(240.dp))
                model.price(item)?.let { Chip("$it") }
                OutlinedButton(onClick = { model.update { it.withRemoved(mart, slot) } }, enabled = list.size > 1, modifier = Modifier.testTag("mart-remove:$slot")) { Text(stringResource(Res.string.learnset_remove)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { model.update { it.withAdded(mart, POTION) } }, enabled = marts.canAdd(mart), modifier = Modifier.testTag("mart-add")) { Text(stringResource(Res.string.mart_add_item)) }
            if (!marts.canAdd(mart)) Text(if (marts.growth == MartGrowth.AVAILABLE) stringResource(Res.string.mart_full_no_room_left_for_more_items) else stringResource(Res.string.mart_full_remove_an_item_to_add_another), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The item a new mart slot starts as (not the Poké Ball: some marts hide it). */
private const val POTION = 17
