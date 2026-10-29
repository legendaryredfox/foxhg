package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.remember
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.grounditem.GroundItem
import io.github.legendaryredfox.foxhg.core.data.grounditem.GroundItems
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The item balls of the overworld: the item and quantity each ball script gives, and the maps whose events use it. */
class GroundItemEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var balls: List<GroundItem> by mutableStateOf(emptyList())
        private set
    var error: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(0)
        private set

    /** Map header numbers by the event file they use, to name where a ball is. */
    private val headers = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
    private val mapsOfEventFile: Map<Int, List<Int>> = headers.withIndex().groupBy({ it.value.eventFile }, { it.index })

    init { reload() }

    private fun reload() {
        when (val r = GroundItems.load(project)) {
            is FoxResult.Ok -> { balls = r.value; error = null }
            is FoxResult.Err -> error = r.message
        }
    }

    val selected: GroundItem? get() = balls.getOrNull(selectedIndex)

    fun itemName(item: Int): String = names?.items?.getOrNull(item) ?: "Item $item"

    /** The places (location names, else map numbers) of the maps whose events use [ball]. */
    fun where(ball: GroundItem): String = ball.usedBy.flatMap { mapsOfEventFile[it].orEmpty() }.map { header ->
        names?.locations?.getOrNull(headers.getOrNull(header)?.locationName ?: -1)?.takeIf { it.isNotBlank() } ?: "map $header"
    }.distinct().joinToString(", ")

    fun label(position: Int): String {
        val ball = balls.getOrNull(position) ?: return "%03d".format(position)
        val quantity = if (ball.quantity == 1) "" else " x${ball.quantity}"
        return "${ball.scriptNumber}  ${itemName(ball.item)}$quantity"
    }

    fun rows(): List<Int> {
        val q = search.trim()
        return balls.indices.filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) || where(balls[it]).contains(q, ignoreCase = true) }
    }

    fun select(position: Int) { if (position in balls.indices) selectedIndex = position }

    /** Adds a ball and selects it; the message says what happened. */
    fun addBall(item: Int, quantity: Int): String? = when (val r = GroundItems.add(project, item, quantity, itemCount = names?.items?.size ?: 0x10000)) {
        is FoxResult.Ok -> { reload(); selectedIndex = balls.lastIndex; onChanged(); null }
        is FoxResult.Err -> r.message
    }

    /** Removes the selected ball; null when it went, else why it stays. */
    fun removeSelectedBall(): String? = when (val r = GroundItems.remove(project, selected?.scriptNumber ?: 0)) {
        is FoxResult.Ok -> { reload(); selectedIndex = selectedIndex.coerceAtMost(balls.lastIndex); onChanged(); null }
        is FoxResult.Err -> r.message
    }

    fun change(item: Int, quantity: Int) {
        val ball = selected ?: return
        when (val r = GroundItems.change(project, ball.scriptNumber, item, quantity, itemCount = names?.items?.size ?: 0x10000)) {
            is FoxResult.Ok -> { reload(); onChanged() }
            is FoxResult.Err -> error = r.message
        }
    }
}

@Composable
fun GroundItemEditor(model: GroundItemEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    Row(modifier.fillMaxSize()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedIndex,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = stringResource(Res.string.ground_item_search_items_places_or_a_script_nu),
            label = model::label,
            onSelect = model::select,
            isEdited = { false },
            detail = { model.balls.getOrNull(it)?.let { b -> "#${b.scriptNumber}" } },
        )
        val ball = model.selected
        DetailPane {
            Chip(stringResource(Res.string.ground_item_x_item_balls, model.balls.size))
            AddBallCard(model)
            if (ball != null) {
                EditorHeader(ball.scriptNumber.toString(), model.itemName(ball.item))
                SectionCard(stringResource(Res.string.ground_item_ball), subtitle = stringResource(Res.string.ground_item_what_the_player_finds)) {
                    FieldFlow {
                        ItemPicker(stringResource(Res.string.evolution_item), ball.item, model.names?.items ?: emptyList(), { v -> model.change(v, ball.quantity) }, Modifier.width(240.dp))
                        NumberField(stringResource(Res.string.ground_item_quantity), ball.quantity, 1..GroundItems.MAX_QUANTITY, { v -> model.change(ball.item, v) }, Modifier.width(100.dp))
                    }
                    val where = model.where(ball)
                    Text(
                        if (ball.usedBy.isEmpty()) stringResource(Res.string.ground_item_no_overworld_event_runs_script_x_p, ball.scriptNumber)
                        else "Used by ${ball.usedBy.size} ${if (ball.usedBy.size == 1) "map" else "maps"}${if (where.isNotEmpty()) ": $where" else ""}. Every event with script ${ball.scriptNumber} gives this item.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddBallCard(model: GroundItemEditorModel) {
    var item by remember { mutableIntStateOf(17) }
    var quantity by remember { mutableIntStateOf(1) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    SectionCard(stringResource(Res.string.ground_item_manage_title), subtitle = stringResource(Res.string.ground_item_manage_subtitle)) {
        FieldFlow {
            ItemPicker(stringResource(Res.string.evolution_item), item, model.names?.items ?: emptyList(), { item = it }, Modifier.width(240.dp))
            NumberField(stringResource(Res.string.ground_item_new_quantity), quantity, 1..GroundItems.MAX_QUANTITY, { quantity = it }, Modifier.width(100.dp))
            OutlinedButton(onClick = {
                val problem = model.addBall(item, quantity)
                failed = problem != null
                message = problem ?: tr(Res.string.ground_item_added_x, model.selected?.scriptNumber ?: 0)
            }, modifier = Modifier.testTag("add-ball")) { Text(stringResource(Res.string.ground_item_add_button)) }
            OutlinedButton(onClick = {
                val number = model.selected?.scriptNumber ?: 0
                val problem = model.removeSelectedBall()
                failed = problem != null
                message = problem ?: tr(Res.string.ground_item_removed_x, number)
            }, modifier = Modifier.testTag("remove-ball")) { Text(stringResource(Res.string.ground_item_remove_selected)) }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) }
    }
}
