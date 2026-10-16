package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.rocksmash.RockSmashItems
import io.github.legendaryredfox.foxhg.core.data.rocksmash.RockSmashSite
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * Rock Smash item drops: each map header has its drop chance and item table (a file of `a/2/5/3`), and the three item
 * tables themselves (overlay 1) are shared by every map that uses them.
 */
class RockSmashEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}, first: Int = 0, onSelected: (Int) -> Unit = {}) {
    private val labels = EventLabels(project)
    val sites = RecordEditorModel(project, RockSmashSite.SPEC, { i -> "%03d  %s".format(i, labels.mapName(i)) }, onChanged, firstIndex = first, onSelected = onSelected)

    var items: RockSmashItems? by mutableStateOf(null)
        private set
    var itemsError: String? by mutableStateOf(null)
        private set

    init {
        when (val loaded = RockSmashItems.load(project)) {
            is FoxResult.Ok -> items = loaded.value
            is FoxResult.Err -> itemsError = loaded.message
        }
    }

    fun setItem(table: Int, slot: Int, item: Int) {
        val updated = (items ?: return).with(table, slot, item)
        when (val saved = RockSmashItems.save(project, updated)) {
            is FoxResult.Ok -> { items = updated; onChanged() }
            is FoxResult.Err -> itemsError = saved.message
        }
    }

    /** How many map headers draw from item table [type]. */
    fun mapsUsing(type: Int): Int = sites.records.count { it.type == type }
}

@Composable
fun RockSmashEditor(model: RockSmashEditorModel, modifier: Modifier = Modifier) {
    val names = model.sites.names?.items ?: emptyList()
    RecordListDetail(
        model.sites, modifier,
        rowDetail = { i -> model.sites.records.getOrNull(i)?.takeIf { it.odds > 0 }?.let { "${it.odds}% " + RockSmashSite.TABLE_NAMES.getOrElse(it.type) { "?" }.substringBefore(" ") } },
    ) { site ->
        val index = model.sites.selectedIndex
        EditorHeader(index.toString(), nameOf(model.sites.label(index)))
        SectionCard(
            "Item drop",
            subtitle = "Only rolled when smashing a rock does not start a wild battle (the encounter rate is set in the Wild encounters editor)",
        ) {
            FieldFlow {
                NumberField("Drop chance %", site.odds, 0..100, { v -> model.sites.update { it.copy(odds = v) } }, Modifier.width(130.dp), tag = "Odds")
                PickerField("Item table", site.type, RockSmashSite.TABLE_NAMES, { v -> model.sites.update { it.copy(type = v) } }, Modifier.width(200.dp))
            }
            Text(
                "0 turns drops off for this map. +5% with Suction Cups, Magnet Pull or Keen Eye in the lead, +5% with a follower that used Rock Smash.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        model.itemsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        model.items?.let { tables ->
            RockSmashSite.TABLE_NAMES.forEachIndexed { t, name ->
                SectionCard(
                    "$name table",
                    subtitle = "Shared by every map that uses it. Serene Grace and Super Luck move a roll one slot toward the rarer end",
                    actions = {
                        if (t == site.type) Chip("this map", emphasis = true)
                        Chip(plural(model.mapsUsing(t), "map"))
                    },
                ) {
                    FieldFlow {
                        for (slot in 0 until RockSmashItems.SLOTS) {
                            ItemPicker("Slot ${slot + 1} (${RockSmashItems.SLOT_CHANCE[slot]}%)", tables.tables[t][slot], names, { v -> model.setItem(t, slot, v) }, Modifier.width(210.dp))
                        }
                    }
                }
            }
        }
    }
}
