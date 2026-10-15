package io.github.romulofer.foxhg.app

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
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.tmhm.TmHmTable
import io.github.romulofer.foxhg.core.game.GameNames
import io.github.romulofer.foxhg.core.project.RomProject

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
        SectionCard("TMs", subtitle = "Which species can learn each one is set in the personal data editor") {
            (0 until TmHmTable.TM_COUNT).chunked(2).forEach { pair -> TmRow(model, table, pair, moves) }
        }
        SectionCard("HMs") {
            (TmHmTable.TM_COUNT until TmHmTable.COUNT).chunked(2).forEach { pair -> TmRow(model, table, pair, moves) }
        }
    }
}

@Composable
private fun TmRow(model: TmHmEditorModel, table: TmHmTable, indexes: List<Int>, moves: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (i in indexes) PickerField(table.name(i), table.moves[i], moves, { v -> model.setMove(i, v) }, Modifier.width(280.dp))
    }
}
