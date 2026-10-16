package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.TextArea
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.text.TextArchive

/**
 * Message banks (`a/0/2/7`) and the messages in them. Text is written as in [io.github.legendaryredfox.foxhg.core.text.MessageText]
 * and applied as you type as long as it is valid; a draft that is not valid stays on screen and is not written.
 * Names shown by the other editors come from the ROM as it was opened and update after a reopen.
 */
class MessageEditorModel(project: RomProject, onChanged: () -> Unit = {}) {
    val banks = RecordEditorModel(project, TextArchive.SPEC, { bank -> "%03d  %s".format(bank, TextBanks.describe(bank) ?: "Bank $bank") }, onChanged)
    private val original: List<TextArchive> = NarcRecords.original(project, TextArchive.SPEC)

    var messageIndex: Int by mutableIntStateOf(0)
        private set
    var messageSearch: String by mutableStateOf("")
    var draft: String by mutableStateOf("")
        private set
    var draftError: String? by mutableStateOf(null)
        private set

    val archive: TextArchive? get() = banks.selected

    init { loadDraft() }

    /** Lower-case text of every message of a bank, rebuilt when the bank changes. */
    private val searchText = HashMap<Int, Pair<TextArchive, String>>()

    private fun textOf(bank: Int): String {
        val archive = banks.records.getOrNull(bank) ?: return ""
        searchText[bank]?.let { (cached, text) -> if (cached === archive) return text }
        return archive.entries.joinToString("\u0000") { it.text() }.lowercase().also { searchText[bank] = archive to it }
    }

    /** Banks whose name or any of whose messages contains the search text. */
    fun bankRows(): List<Int> {
        val q = banks.search.trim().lowercase()
        if (q.isEmpty()) return (0 until banks.recordCount).toList()
        return (0 until banks.recordCount).filter { banks.label(it).lowercase().contains(q) || textOf(it).contains(q) }
    }

    /** The bank search also narrows the message list of the bank you open, so the match is in view. */
    fun selectBank(bank: Int) {
        banks.select(bank)
        messageIndex = 0
        messageSearch = banks.search.trim()
        loadDraft()
    }

    fun selectMessage(index: Int) {
        if (index in (archive?.entries?.indices ?: IntRange.EMPTY)) {
            messageIndex = index
            loadDraft()
        }
    }

    fun edit(text: String) {
        draft = text
        val a = archive ?: return
        when (val r = a.withText(messageIndex, text)) {
            is FoxResult.Ok -> { draftError = null; if (r.value != a) banks.update { r.value } }
            is FoxResult.Err -> draftError = r.message
        }
    }

    fun isBankEdited(bank: Int): Boolean = banks.isEdited(bank)

    fun isMessageEdited(index: Int): Boolean {
        val now = archive?.entries?.getOrNull(index) ?: return false
        return original.getOrNull(banks.selectedIndex)?.entries?.getOrNull(index) != now
    }

    /** Puts the message back to what the ROM has. */
    fun revertMessage() {
        val old = original.getOrNull(banks.selectedIndex)?.entries?.getOrNull(messageIndex) ?: return
        banks.update { it.withEntry(messageIndex, old) }
        loadDraft()
    }

    private fun loadDraft() {
        draft = archive?.entries?.getOrNull(messageIndex)?.text() ?: ""
        draftError = null
    }
}

@Composable
fun MessageEditor(model: MessageEditorModel, modifier: Modifier = Modifier) {
    model.banks.loadError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListPane(
            rows = model.bankRows(),
            selected = model.banks.selectedIndex,
            search = model.banks.search,
            onSearch = { model.banks.search = it },
            placeholder = "Search banks and messages…",
            label = model.banks::label,
            onSelect = model::selectBank,
            isEdited = model::isBankEdited,
            detail = { b -> model.banks.records.getOrNull(b)?.entries?.size?.let { "$it msg" } },
            width = 250.dp,
        )
        val archive = model.archive ?: return@Row
        val texts = remember(archive) { archive.entries.map { it.text().replace("\\n", " ").replace("\n", " ") } }
        val rows = texts.indices.filter { model.messageSearch.isBlank() || texts[it].contains(model.messageSearch.trim(), ignoreCase = true) }
        ListPane(
            rows = rows,
            selected = model.messageIndex,
            search = model.messageSearch,
            onSearch = { model.messageSearch = it },
            placeholder = "Search messages…",
            label = { "%03d  %s".format(it, texts[it]) },
            onSelect = model::selectMessage,
            isEdited = model::isMessageEdited,
            width = 330.dp,
        )
        DetailPane {
            val entry = archive.entries.getOrNull(model.messageIndex)
            EditorHeader(model.banks.selectedIndex.toString(), TextBanks.describe(model.banks.selectedIndex) ?: "Bank ${model.banks.selectedIndex}")
            SectionCard(
                "Message ${model.messageIndex} of ${archive.entries.size}",
                subtitle = "Commands are written {NAME args}, for example {STRVAR_1 3, 0, 0}, {PAUSE 30}, {COLOR 1}. Line breaks: \\n next line, \\r new box, \\f clear.",
                actions = {
                    if (entry?.compressed == true) Chip("packed")
                    if (model.isMessageEdited(model.messageIndex)) OutlinedButton(onClick = model::revertMessage) { Text("Revert") }
                },
            ) {
                TextArea(model.draft, model::edit, invalid = model.draftError != null, modifier = Modifier.fillMaxWidth())
                val lines = model.draft.split("\\n", "\\r", "\\f", "\n").size
                Text("${plural(model.draft.length, "character")}, ${plural(lines, "line")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                model.draftError?.let { Text("Not applied: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
