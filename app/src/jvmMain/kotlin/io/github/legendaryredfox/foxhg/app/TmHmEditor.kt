package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.tmhm.TmHmTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The move of each TM and HM. The table lives in the ARM9, so saving it makes the build replace the ARM9. */
class TmHmEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var table: TmHmTable? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    init {
        when (val loaded = TmHmTable.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    fun setMove(index: Int, move: Int) {
        val updated = (table ?: return).with(index, move)
        when (val saved = TmHmTable.save(project, updated)) {
            is FoxResult.Ok -> { table = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }
}

@Composable
fun TmHmEditor(model: TmHmEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier)
        return
    }
    val table = model.table ?: return
    val moves = model.names?.moves ?: emptyList()
    DetailPane(modifier) {
        SectionCard(stringResource(Res.string.tm_hm_tms), subtitle = stringResource(Res.string.tm_hm_which_species_can_learn_each_one_i)) {
            FieldFlow { for (i in 0 until TmHmTable.TM_COUNT) TmPicker(model, table, i, moves) }
        }
        SectionCard(stringResource(Res.string.tm_hm_hms)) {
            FieldFlow { for (i in TmHmTable.TM_COUNT until TmHmTable.COUNT) TmPicker(model, table, i, moves) }
        }
    }
}

@Composable
private fun TmPicker(model: TmHmEditorModel, table: TmHmTable, i: Int, moves: List<String>) {
    PickerField(table.name(i), table.moves[i], moves, { v -> model.setMove(i, v) }, Modifier.width(280.dp))
}
