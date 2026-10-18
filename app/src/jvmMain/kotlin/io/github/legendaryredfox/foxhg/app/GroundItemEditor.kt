package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
            placeholder = "Search items, places or a script number…",
            label = model::label,
            onSelect = model::select,
            isEdited = { false },
            detail = { model.balls.getOrNull(it)?.let { b -> "#${b.scriptNumber}" } },
        )
        val ball = model.selected
        DetailPane {
            Chip("${model.balls.size} item balls")
            if (ball != null) {
                EditorHeader(ball.scriptNumber.toString(), model.itemName(ball.item))
                SectionCard("Ball", subtitle = "What the player finds") {
                    FieldFlow {
                        ItemPicker("Item", ball.item, model.names?.items ?: emptyList(), { v -> model.change(v, ball.quantity) }, Modifier.width(240.dp))
                        NumberField("Quantity", ball.quantity, 1..GroundItems.MAX_QUANTITY, { v -> model.change(ball.item, v) }, Modifier.width(100.dp))
                    }
                    val where = model.where(ball)
                    Text(
                        if (ball.usedBy.isEmpty()) "No overworld event runs script ${ball.scriptNumber}: put it on an overworld object of a map to place the ball."
                        else "Used by ${ball.usedBy.size} ${if (ball.usedBy.size == 1) "map" else "maps"}${if (where.isNotEmpty()) ": $where" else ""}. Every event with script ${ball.scriptNumber} gives this item.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
