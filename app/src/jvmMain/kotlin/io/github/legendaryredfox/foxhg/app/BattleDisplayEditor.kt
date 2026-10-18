package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PokemonIcon
import io.github.legendaryredfox.foxhg.app.ui.PokemonPortraits
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.battledisplay.BattleDisplay
import io.github.legendaryredfox.foxhg.core.data.battledisplay.BattleDisplayTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** State of the Battle display editor: one record per species (sprite offsets and heights), edits written to the project. */
class BattleDisplayEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    private var table: BattleDisplayTable? = null
    private val original: BattleDisplayTable? = (BattleDisplayTable.load(project) as? FoxResult.Ok)?.value

    val names: GameNames? = project.names
    var loadError: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedIndex: Int by mutableIntStateOf(1)
        private set
    private var version by mutableIntStateOf(0)

    init {
        when (val loaded = BattleDisplayTable.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> loadError = loaded.message
        }
    }

    val recordCount: Int get() = table?.count ?: 0

    val selected: BattleDisplay?
        get() {
            version // read to subscribe
            return table?.takeIf { selectedIndex < it.count }?.get(selectedIndex)
        }

    fun label(index: Int): String =
        "%03d  %s".format(index, names?.species?.getOrNull(index) ?: if (index == 0) "-----" else "Form $index")

    fun rows(): List<Int> {
        val q = search.trim()
        return (0 until recordCount).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    fun select(index: Int) {
        if (index in 0 until recordCount) selectedIndex = index
    }

    fun update(change: (BattleDisplay) -> BattleDisplay) {
        val current = table ?: return
        val updated = current.with(selectedIndex, change(current.get(selectedIndex)))
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> {
                table = updated
                version++
                onChanged()
            }
            is FoxResult.Err -> loadError = saved.message
        }
    }

    /** True when record [index] differs from what the ROM originally has. */
    fun isEdited(index: Int): Boolean {
        version // subscribe
        val current = table?.takeIf { index < it.count } ?: return false
        return original?.takeIf { index < it.count }?.get(index) != current.get(index)
    }
}

@Composable
fun BattleDisplayEditor(model: BattleDisplayEditorModel, modifier: Modifier = Modifier) {
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
private fun Form(model: BattleDisplayEditorModel, d: BattleDisplay) {
    EditorHeader(model.selectedIndex.toString(), nameOf(model.label(model.selectedIndex)), trailing = { PokemonPortraits(model.selectedIndex) })
    SectionCard(stringResource(Res.string.battle_display_sprite_and_shadow), subtitle = stringResource(Res.string.battle_display_vertical_offset_of_the_battle_spri)) {
        FieldFlow {
            NumberField(stringResource(Res.string.battle_display_sprite_y), d.spriteY, -128..127, { v -> model.update { it.copy(spriteY = v) } }, Modifier.width(110.dp))
            NumberField(stringResource(Res.string.battle_display_shadow_x), d.shadowX, -128..127, { v -> model.update { it.copy(shadowX = v) } }, Modifier.width(110.dp))
            NumberField(stringResource(Res.string.battle_display_shadow_size), d.shadowSize, 0..255, { v -> model.update { it.copy(shadowSize = v) } }, Modifier.width(110.dp))
            NumberField(stringResource(Res.string.battle_display_movement_raw), d.movement, 0..255, { v -> model.update { it.copy(movement = v) } }, Modifier.width(130.dp))
        }
    }
    SectionCard(stringResource(Res.string.battle_display_sprite_heights), subtitle = stringResource(Res.string.battle_display_0_to_255_per_battle_sprite_a_slot)) {
        FieldFlow {
            BattleDisplay.HEIGHT_NAMES.forEachIndexed { slot, name ->
                val height = d.heights[slot]
                if (height == null) {
                    Text(stringResource(Res.string.battle_display_x_no_sprite, name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    NumberField(name, height, 0..255, { v -> model.update { it.copy(heights = it.heights.toMutableList().also { list -> list[slot] = v }) } }, Modifier.width(130.dp))
                }
            }
        }
    }
}
