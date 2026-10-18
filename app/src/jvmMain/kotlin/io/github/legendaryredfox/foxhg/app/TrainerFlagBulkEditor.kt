package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SearchBox
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerFlagBulk
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** One battle flag (an AI bit or double battle) of every trainer at a time, with bulk changes of the trainers shown, written straight into the project. */
class TrainerFlagBulkEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val names: GameNames? = project.names
    private var table: TrainerTable? = null
    private var version by mutableIntStateOf(0)
    var error: String? by mutableStateOf(null)
        private set
    var flag: Int by mutableIntStateOf(0)
    var search: String by mutableStateOf("")

    init {
        when (val loaded = TrainerTable.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    fun name(index: Int): String = names?.trainerNames?.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Trainer $index"

    fun className(index: Int): String =
        table?.trainers?.getOrNull(index)?.header?.trainerClass?.let { names?.trainerClasses?.getOrNull(it) } ?: ""

    fun label(index: Int): String = "%03d  %s".format(index, name(index))

    /** Trainers matching the search text, by name, number or class. */
    fun rows(): List<Int> {
        version // subscribe
        val q = search.trim()
        val count = table?.trainers?.size ?: 0
        return (1 until count).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) || className(it).contains(q, ignoreCase = true) }
    }

    fun has(index: Int): Boolean {
        version // subscribe
        return table?.trainers?.getOrNull(index)?.let { TrainerFlagBulk.has(it, flag) } == true
    }

    fun count(): Int {
        version
        return table?.let { TrainerFlagBulk.withFlagSet(it, flag).size } ?: 0
    }

    /** The trainers of the same class as [index], including it. */
    fun classmates(index: Int): List<Int> {
        val t = table ?: return listOf(index)
        return TrainerFlagBulk.byClass(t)[t.trainers[index].header.trainerClass]?.filter { it != 0 } ?: listOf(index)
    }

    fun set(trainers: Collection<Int>, on: Boolean) {
        val current = table ?: return
        val updated = TrainerFlagBulk.set(current, flag, trainers, on)
        if (updated.trainers.indices.all { updated.trainers[it] === current.trainers[it] }) return
        when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { table = updated; version++; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }
}

@Composable
fun TrainerFlagBulkEditor(model: TrainerFlagBulkEditorModel, modifier: Modifier = Modifier) {
    model.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val rows = model.rows()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.trainer_flag_bulk_trainer_battle_flags), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            PickerField(stringResource(Res.string.trainer_flag_bulk_flag), model.flag, TrainerFlagBulk.NAMES, { model.flag = it }, Modifier.width(240.dp))
            SearchBox(model.search, { model.search = it }, stringResource(Res.string.trainer_flag_bulk_search_trainers_or_classes), Modifier.width(280.dp))
            Chip(stringResource(Res.string.trainer_flag_bulk_x_trainers_have_it, model.count()))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.tm_hm_bulk_x_shown, rows.size), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { model.set(rows, true) }, modifier = Modifier.testTag("flags-all")) { Text(stringResource(Res.string.trainer_flag_bulk_set_on_all_shown)) }
            OutlinedButton(onClick = { model.set(rows, false) }, modifier = Modifier.testTag("flags-none")) { Text(stringResource(Res.string.trainer_flag_bulk_clear_on_all_shown)) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows, key = { it }) { trainer ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(model.label(trainer), Modifier.width(220.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(model.className(trainer), Modifier.width(180.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    val on = model.has(trainer)
                    ToggleChip(if (on) stringResource(Res.string.trainer_flag_bulk_on) else stringResource(Res.string.trainer_flag_bulk_off), on, { v -> model.set(listOf(trainer), v) }, Modifier.testTag("flag:$trainer"))
                    val classmates = model.classmates(trainer)
                    if (classmates.size > 1) {
                        OutlinedButton(onClick = { model.set(classmates, !on) }, modifier = Modifier.testTag("flag-class:$trainer")) {
                            Text(if (on) stringResource(Res.string.trainer_flag_bulk_clear_whole_class) else stringResource(Res.string.trainer_flag_bulk_set_whole_class))
                        }
                    }
                }
            }
        }
    }
}
