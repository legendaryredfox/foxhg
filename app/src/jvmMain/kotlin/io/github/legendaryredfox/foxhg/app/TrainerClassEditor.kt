package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.AudioStatus
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.SoundPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SearchBox
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainerclass.EncounterMusicRow
import io.github.legendaryredfox.foxhg.core.data.trainerclass.NewTrainerClass
import io.github.legendaryredfox.foxhg.core.data.trainerclass.TrainerEncounterMusic
import io.github.legendaryredfox.foxhg.core.data.trainerclass.TrainerClassGenders
import io.github.legendaryredfox.foxhg.core.data.trainerclass.TrainerClassPrizes
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** Gender (ARM9) and prize money multiplier (overlay 12) of each trainer class; a build replaces what was edited. */
class TrainerClassEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    var names: GameNames? by mutableStateOf(project.names)
        private set
    var table: TrainerClassGenders? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var prizes: TrainerClassPrizes? by mutableStateOf(null)
        private set
    var music: TrainerEncounterMusic? by mutableStateOf(null)
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
        music = (TrainerEncounterMusic.load(project) as? FoxResult.Ok)?.value
    }

    /** Sets the eye-contact songs of a class (adds its row when it has none); returns why it failed, or null. */
    fun setMusic(row: EncounterMusicRow): String? {
        val updated = (music ?: return "No music table").with(row)
        return when (val saved = TrainerEncounterMusic.save(project, updated)) {
            is FoxResult.Ok -> { music = updated; onChanged(); null }
            is FoxResult.Err -> saved.message
        }
    }

    fun setPrize(row: Int, multiplier: Int) {
        val updated = (prizes ?: return).withEntry(row, multiplier)
        when (val saved = TrainerClassPrizes.save(project, updated)) {
            is FoxResult.Ok -> { prizes = updated; onChanged() }
            is FoxResult.Err -> error = saved.message
        }
    }

    /** Adds a class at the end of the list (see [NewTrainerClass]) and reloads the tables; returns why it failed, or null. */
    fun addClass(name: String, template: Int, gender: Int, multiplier: Int, eyeMusic: Int = 0): String? {
        val id = when (val r = NewTrainerClass.add(project, name.trim(), template, gender, multiplier, eyeMusic)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r.message
        }
        when (val loaded = TrainerClassGenders.load(project)) {
            is FoxResult.Ok -> table = loaded.value
            is FoxResult.Err -> return loaded.message
        }
        when (val loaded = TrainerClassPrizes.load(project)) {
            is FoxResult.Ok -> prizes = loaded.value
            is FoxResult.Err -> return loaded.message
        }
        names = project.names
        music = (TrainerEncounterMusic.load(project) as? FoxResult.Ok)?.value
        lastAdded = id
        onChanged()
        return null
    }

    var lastAdded: Int by mutableStateOf(-1)
        private set

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
            AddClassCard(model)
            EncounterMusicCard(model)
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

/** A new class at the end of the list: name, the class whose picture it copies, gender and prize multiplier. */
@Composable
private fun AddClassCard(model: TrainerClassEditorModel) {
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf(0) }
    var gender by remember { mutableStateOf(0) }
    var multiplier by remember { mutableStateOf(5) }
    var music by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    SectionCard(
        stringResource(Res.string.trainer_class_add_title),
        subtitle = stringResource(Res.string.trainer_class_add_subtitle),
        actions = { OutlinedButton(onClick = { open = !open }, modifier = Modifier.testTag("add-class-toggle")) { Text(if (open) stringResource(Res.string.trainer_hide) else stringResource(Res.string.trainer_show)) } },
    ) {
        if (open) FieldFlow {
            LabeledText(stringResource(Res.string.trainer_class_add_name), name, tag = "New class name") { name = it }
            PickerField(stringResource(Res.string.trainer_class_add_copy), template, model.names?.trainerClasses ?: emptyList(), { template = it }, Modifier.width(240.dp), tag = "Copy the picture of")
            PickerField(stringResource(Res.string.trainer_class_add_gender), gender, TrainerClassGenders.NAMES, { gender = it }, Modifier.width(140.dp), tag = "New class gender")
            NumberField(stringResource(Res.string.trainer_class_add_prize), multiplier, 0..65535, { multiplier = it }, Modifier.width(140.dp))
            SoundPicker(stringResource(Res.string.trainer_class_add_music), music, 0..65535, { music = it }, Modifier.width(260.dp), tag = "New class music")
            OutlinedButton(onClick = {
                val problem = model.addClass(name, template, gender, multiplier, music)
                failed = problem != null
                message = problem?.let { tr(Res.string.trainer_class_add_failed_x, it) } ?: tr(Res.string.trainer_class_added_x, model.lastAdded)
                if (problem == null) name = ""
            }, enabled = name.isNotBlank(), modifier = Modifier.testTag("add-class")) { Text(stringResource(Res.string.trainer_class_add_button)) }
        }
        AudioStatus()
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) }
    }
}

/** The eye-contact songs of the classes that have a row, and a control to give any class one. */
@Composable
private fun EncounterMusicCard(model: TrainerClassEditorModel) {
    val music = model.music ?: return
    var open by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var pickedClass by remember { mutableStateOf(0) }
    var song by remember { mutableStateOf(TrainerEncounterMusic.DEFAULT_SEQUENCE) }
    fun apply(row: EncounterMusicRow, done: String) {
        val problem = model.setMusic(row)
        failed = problem != null
        message = problem?.let { tr(Res.string.trainer_class_music_failed_x, it) } ?: done
    }
    SectionCard(
        stringResource(Res.string.trainer_class_music_title),
        subtitle = stringResource(Res.string.trainer_class_music_subtitle),
        actions = { OutlinedButton(onClick = { open = !open }, modifier = Modifier.testTag("music-toggle")) { Text(if (open) stringResource(Res.string.trainer_hide) else stringResource(Res.string.trainer_show)) } },
    ) {
        if (open) {
            FieldFlow {
                PickerField(stringResource(Res.string.trainer_class_music_class), pickedClass, model.names?.trainerClasses ?: emptyList(), { pickedClass = it }, Modifier.width(240.dp), tag = "Music class")
                SoundPicker(stringResource(Res.string.trainer_class_music_song), song, 0..65535, { song = it }, Modifier.width(260.dp), tag = "Music song")
                OutlinedButton(onClick = { apply(EncounterMusicRow(pickedClass, song, song), tr(Res.string.trainer_class_music_done_x, model.label(pickedClass))) }, modifier = Modifier.testTag("music-set")) { Text(stringResource(Res.string.trainer_class_music_set)) }
            }
            music.rows.filter { model.search.isBlank() || model.label(it.trainerClass).contains(model.search.trim(), ignoreCase = true) }.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SoundPicker(model.label(row.trainerClass) + " - " + stringResource(Res.string.trainer_class_music_first), row.first, 0..65535, { v -> apply(row.copy(first = v), tr(Res.string.trainer_class_music_done_x, model.label(row.trainerClass))) }, Modifier.width(300.dp))
                    SoundPicker(stringResource(Res.string.trainer_class_music_second), row.second, 0..65535, { v -> apply(row.copy(second = v), tr(Res.string.trainer_class_music_done_x, model.label(row.trainerClass))) }, Modifier.width(260.dp), tag = model.label(row.trainerClass) + " - second song")
                }
            }
        }
        AudioStatus()
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary) }
    }
}
