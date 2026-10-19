package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.TextArea
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainer.MessageTrigger
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerMessage
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerMessages
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.text.MessageText

/** The lines trainers say in battle (banks 728, `a/0/5/7` and `a/1/3/1`): when each shows and its text; every change is saved to the project as it is made. */
class TrainerMessageEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    private val names = project.names
    var messages: TrainerMessages? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    var search: String by mutableStateOf("")
    var selectedTrainer: Int by mutableIntStateOf(0)
        private set

    init {
        when (val r = TrainerMessages.load(project)) {
            is FoxResult.Ok -> messages = r.value
            is FoxResult.Err -> error = r.message
        }
    }

    val trainerCount: Int get() = messages?.offsets?.size ?: 0

    fun trainerName(trainer: Int): String = names?.trainerNames?.getOrNull(trainer)?.takeIf { it.isNotBlank() } ?: "Trainer $trainer"

    fun label(trainer: Int): String = "%03d  %s".format(trainer, trainerName(trainer))

    fun count(trainer: Int): Int = messages?.messagesOf(trainer)?.size ?: 0

    fun rows(): List<Int> {
        val q = search.trim()
        return (0 until trainerCount).filter { q.isEmpty() || label(it).contains(q, ignoreCase = true) }
    }

    fun select(trainer: Int) { if (trainer in 0 until trainerCount) selectedTrainer = trainer }

    /** The message numbers of the selected trainer. */
    fun selectedMessages(): List<Int> = messages?.messagesOf(selectedTrainer) ?: emptyList()

    private fun commit(result: FoxResult<TrainerMessages>): String? {
        val updated = when (result) {
            is FoxResult.Ok -> result.value
            is FoxResult.Err -> return result.message
        }
        return when (val saved = updated.save(project)) {
            is FoxResult.Ok -> { messages = updated; error = null; onChanged(); null }
            is FoxResult.Err -> saved.message
        }
    }

    fun setTrigger(index: Int, trigger: Int) { commit(FoxResult.Ok(messages?.withTrigger(index, trigger) ?: return))?.let { error = it } }

    /** Stores a new text for message [index]; returns the reason it cannot be used, or null. */
    fun setText(index: Int, text: String): String? {
        val current = messages ?: return null
        return commit(current.withText(index, text))
    }

    fun add() {
        val current = messages ?: return
        commit(current.replaceAll(current.all() + TrainerMessage(selectedTrainer, 1, "")))?.let { error = it }
    }

    fun remove(index: Int) {
        val current = messages ?: return
        commit(current.replaceAll(current.all().filterIndexed { i, _ -> i != index }))?.let { error = it }
    }

    fun exportCsv(): String = messages?.exportCsv(::trainerName) ?: ""

    /** Replaces every message with a CSV file's rows; returns the reason it was refused, or null. */
    fun importCsv(text: String): String? {
        val current = messages ?: return null
        return commit(current.importCsv(text))
    }
}

private val TRIGGER_NAMES: List<StringResource> = listOf(
    Res.string.trainer_trigger_0, Res.string.trainer_trigger_1, Res.string.trainer_trigger_2, Res.string.trainer_trigger_3,
    Res.string.trainer_trigger_4, Res.string.trainer_trigger_5, Res.string.trainer_trigger_6, Res.string.trainer_trigger_7,
    Res.string.trainer_trigger_8, Res.string.trainer_trigger_9, Res.string.trainer_trigger_10, Res.string.trainer_trigger_11,
    Res.string.trainer_trigger_12, Res.string.trainer_trigger_13, Res.string.trainer_trigger_14, Res.string.trainer_trigger_15,
    Res.string.trainer_trigger_16, Res.string.trainer_trigger_17, Res.string.trainer_trigger_18, Res.string.trainer_trigger_19,
    Res.string.trainer_trigger_20,
)

@Composable
fun TrainerMessageEditor(model: TrainerMessageEditorModel, modifier: Modifier = Modifier) {
    val messages = model.messages
    if (messages == null) {
        Text(model.error ?: "", color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    val triggerNames = TRIGGER_NAMES.map { stringResource(it) }
    Row(modifier.fillMaxSize()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedTrainer,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = stringResource(Res.string.trainer_message_search),
            label = model::label,
            onSelect = model::select,
            isEdited = { false },
            detail = { model.count(it).takeIf { n -> n > 0 }?.toString() },
        )
        DetailPane {
            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            EditorHeader(model.selectedTrainer.toString(), model.trainerName(model.selectedTrainer), trailing = {
                Chip(stringResource(Res.string.trainer_message_total_x, messages.size))
                CsvFileButtons(tr(Res.string.app_trainer_messages), export = { model.exportCsv() }, import = { model.importCsv(it) })
            })
            SectionCard(stringResource(Res.string.trainer_message_battle_messages), subtitle = stringResource(Res.string.trainer_message_what_the_trainer_says)) {
                val mine = model.selectedMessages()
                if (mine.isEmpty()) Text(stringResource(Res.string.trainer_message_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (index in mine) key(index, messages) { MessageRow(model, messages.message(index), index, triggerNames) }
                Button(onClick = { model.add() }) { Text(stringResource(Res.string.trainer_message_add)) }
            }
        }
    }
}

@Composable
private fun MessageRow(model: TrainerMessageEditorModel, message: TrainerMessage, index: Int, triggerNames: List<String>) {
    var text by remember { mutableStateOf(message.text) }
    var problem by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("%04d".format(index), style = io.github.legendaryredfox.foxhg.app.ui.Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val options = if (message.trigger in triggerNames.indices) triggerNames else triggerNames + MessageTrigger.name(message.trigger)
            PickerField(stringResource(Res.string.trainer_message_when), message.trigger, options, { model.setTrigger(index, it) }, Modifier.width(340.dp), tag = "Shows")
            OutlinedButton(onClick = { problem = model.setText(index, text)?.let { tr(Res.string.trainer_message_cannot_encode_x, it) } }, enabled = text != message.text) {
                Text(stringResource(Res.string.trainer_message_apply))
            }
            TextButton(onClick = { model.remove(index) }) { Text(stringResource(Res.string.learnset_remove)) }
        }
        TextArea(text, { text = it; problem = null }, invalid = problem != null || MessageText.encode(text) is FoxResult.Err, modifier = Modifier.fillMaxWidth(), minHeight = 64.dp)
        problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
