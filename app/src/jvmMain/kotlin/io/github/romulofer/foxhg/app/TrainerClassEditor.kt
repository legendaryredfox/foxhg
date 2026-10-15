package io.github.romulofer.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import io.github.romulofer.foxhg.app.ui.SearchBox
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassGenders
import io.github.romulofer.foxhg.core.game.GameNames
import io.github.romulofer.foxhg.core.project.RomProject

/** Gender of each trainer class. It lives in the ARM9, so saving it makes the build replace the ARM9. */
class TrainerClassEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var table: TrainerClassGenders? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")

    init {
        when (val loaded = TrainerClassGenders.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    fun label(index: Int): String = "%03d  %s".format(index, names?.trainerClasses?.getOrNull(index) ?: "Class $index")

    fun setGender(index: Int, gender: Int) {
        val updated = (table ?: return).with(index, gender)
        when (val saved = TrainerClassGenders.save(project, updated)) {
            is FoxResult.Ok -> { table = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }
}

@Composable
fun TrainerClassEditor(model: TrainerClassEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier)
        return
    }
    val table = model.table ?: return
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchBox(model.search, { model.search = it }, "Search classes…", Modifier.width(272.dp))
        DetailPane {
            SectionCard("Trainer class gender", subtitle = "Used for battle text and sprites. Prize money and class names are not editable yet") {
                val rows = table.genders.indices.filter { model.search.isBlank() || model.label(it).contains(model.search.trim(), ignoreCase = true) }
                rows.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (i in pair) PickerField(model.label(i), table.genders[i], TrainerClassGenders.NAMES, { v -> model.setGender(i, v) }, Modifier.width(280.dp))
                    }
                }
            }
        }
    }
}
