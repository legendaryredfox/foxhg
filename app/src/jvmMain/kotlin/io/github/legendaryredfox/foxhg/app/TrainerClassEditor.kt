package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
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
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SearchBox
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainerclass.TrainerClassGenders
import io.github.legendaryredfox.foxhg.core.data.trainerclass.TrainerClassPrizes
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** Gender (ARM9) and prize money multiplier (overlay 12) of each trainer class; a build replaces what was edited. */
class TrainerClassEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    var table: TrainerClassGenders? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var prizes: TrainerClassPrizes? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")

    init {
        when (val loaded = TrainerClassGenders.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
        when (val loaded = TrainerClassPrizes.load(project)) {
            is FoxResult.Ok -> prizes = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    fun setPrize(row: Int, multiplier: Int) {
        val updated = (prizes ?: return).withEntry(row, multiplier)
        when (val saved = TrainerClassPrizes.save(project, updated)) {
            is FoxResult.Ok -> { prizes = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
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
        SearchBox(model.search, { model.search = it }, stringResource(Res.string.trainer_class_search_classes), Modifier.width(272.dp))
        DetailPane {
            SectionCard(stringResource(Res.string.trainer_class_trainer_class_gender), subtitle = stringResource(Res.string.trainer_class_used_for_battle_text_and_sprites_c)) {
                val rows = table.genders.indices.filter { model.search.isBlank() || model.label(it).contains(model.search.trim(), ignoreCase = true) }
                rows.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (i in pair) PickerField(model.label(i), table.genders[i], TrainerClassGenders.NAMES, { v -> model.setGender(i, v) }, Modifier.width(280.dp))
                    }
                }
            }
            val prizes = model.prizes
            if (prizes != null) {
                SectionCard(stringResource(Res.string.trainer_class_prize_money_multiplier), subtitle = stringResource(Res.string.trainer_class_money_won_multiplier_x_level_of_th)) {
                    val rows = prizes.entries.indices.filter { model.search.isBlank() || model.label(prizes.entries[it].trainerClass).contains(model.search.trim(), ignoreCase = true) }
                    rows.chunked(3).forEach { triple ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            for (r in triple) NumberField(model.label(prizes.entries[r].trainerClass), prizes.entries[r].multiplier, 0..65535, { v -> model.setPrize(r, v) }, Modifier.width(180.dp))
                        }
                    }
                }
            }
        }
    }
}
