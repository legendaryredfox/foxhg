package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.TextArea
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeader
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.LevelScript
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import io.github.legendaryredfox.foxhg.core.script.LevelTrigger
import io.github.legendaryredfox.foxhg.core.script.ScriptCommands
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import io.github.legendaryredfox.foxhg.core.script.VariableTrigger

/** Script files labelled by the maps that use them. */
class ScriptLabels(project: RomProject) {
    private val headers: List<MapHeader> = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
    private val names = project.names

    private fun mapName(header: Int): String =
        headers.getOrNull(header)?.let { names?.locations?.getOrNull(it.locationName) }?.takeIf { it.isNotBlank() } ?: "Map $header"

    /** The text bank (a file of the message NARC) the script file's first map reads its messages from, or null. */
    fun textBank(file: Int): Int? = scriptUsers(file).firstOrNull()?.let { headers[it].textArchive }

    fun scriptUsers(file: Int): List<Int> = headers.indices.filter { headers[it].script == file }
    fun levelScriptUsers(file: Int): List<Int> = headers.indices.filter { headers[it].levelScript == file }

    /** Script files (header.script) of the maps that use level script [file]; its trigger numbers are scripts of those. */
    fun scriptFilesOfLevel(file: Int): List<Int> = levelScriptUsers(file).map { headers[it].script }.distinct()

    fun describe(users: List<Int>): String = summarize(users.map { "$it (${mapName(it)})" })

    /** The first script file a map uses, so the editor opens on a script with something in it. */
    fun firstUsedFile(): Int = headers.minOfOrNull { it.script }?.coerceAtLeast(0) ?: 0

    fun label(file: Int): String {
        val s = scriptUsers(file)
        val l = levelScriptUsers(file)
        val name = when {
            s.isNotEmpty() -> mapName(s.first()) + if (s.size > 1) " +${s.size - 1}" else ""
            l.isNotEmpty() -> mapName(l.first()) + " (level script)"
            else -> "(not used by a map)"
        }
        return "%03d  %s".format(file, name)
    }
}

/** State of the Scripts editor: the file list is a [RecordEditorModel], the text being typed is the draft. */
class ScriptEditorModel(project: RomProject, private val labels: ScriptLabels, onChanged: () -> Unit = {}) {
    val files = RecordEditorModel(project, ScriptEntry.SPEC, labels::label, onChanged)
    init { labels.firstUsedFile().let { if (it in 0 until files.recordCount) files.select(it) } }

    private val archives: List<TextArchive> = (TextArchive.load(project) as? FoxResult.Ok)?.value?.records ?: emptyList()

    /** The messages the selected script file can show, for the notes in its text. */
    private fun messages(file: Int): ScriptText.Messages? =
        labels.textBank(file)?.let { archives.getOrNull(it) }?.let { bank -> ScriptText.Messages { id -> bank.entries.getOrNull(id)?.text() } }

    var draft: String by mutableStateOf("")
        private set
    var draftError: String? by mutableStateOf(null)
        private set

    init { loadDraft() }

    fun select(index: Int) {
        files.select(index)
        loadDraft()
    }

    /** Applies the text as soon as it assembles; until then the file keeps its last good version. */
    fun edit(text: String) {
        draft = text
        if (files.selected !is ScriptEntry.Script) return
        when (val r = ScriptText.parse(text)) {
            is FoxResult.Ok -> {
                draftError = null
                val entry = ScriptEntry.Script(r.value)
                if (entry != files.selected) files.update { entry }
            }
            is FoxResult.Err -> draftError = r.message
        }
    }

    private fun loadDraft() {
        draft = (files.selected as? ScriptEntry.Script)?.let { ScriptText.toText(it.file, messages(files.selectedIndex)) } ?: ""
        draftError = null
    }
}

@Composable
fun ScriptEditor(model: ScriptEditorModel, labels: ScriptLabels, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val highlight = remember(dark) { ScriptHighlight(dark) }
    RecordListDetail(model.files, modifier, onSelect = model::select, rowDetail = { labels.scriptUsers(it).firstOrNull()?.let { h -> "hdr $h" } }) { entry ->
        val index = model.files.selectedIndex
        EditorHeader(index.toString(), nameOf(model.files.label(index)))
        val scriptUsers = labels.scriptUsers(index)
        val levelUsers = labels.levelScriptUsers(index)
        if (scriptUsers.isNotEmpty()) Text("Script of map header " + labels.describe(scriptUsers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (levelUsers.isNotEmpty()) Text("Level script of map header " + labels.describe(levelUsers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entry is ScriptEntry.Script) ScriptSounds(model.draft)
        when (entry) {
            is ScriptEntry.Script -> SectionCard(
                "Script",
                subtitle = "Commands as in the script command database; a jump takes a label. Flag and variable names can be used instead of numbers. Applied as you type once it is valid.",
                actions = { Chip("${entry.file.scriptCount} scripts") },
            ) {
                TextArea(model.draft, model::edit, invalid = model.draftError != null, modifier = Modifier.fillMaxWidth(), minHeight = androidx.compose.ui.unit.Dp(480f), transformation = highlight)
                model.draftError?.let { Text("Not applied: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            is ScriptEntry.Level -> LevelScriptForm(entry.file, labels.scriptFilesOfLevel(index)) { changed -> model.files.update { ScriptEntry.Level(changed.aligned()) } }
            is ScriptEntry.Raw -> SectionCard("Not editable yet", subtitle = entry.reason, actions = { Chip("${entry.bytes.size} bytes") }) {
                Text(
                    entry.bytes.take(512).chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } + if (entry.bytes.size > 512) "\n…" else "",
                    style = io.github.legendaryredfox.foxhg.app.ui.Fox.mono,
                )
            }
        }
    }
}

/** Colours labels, command names, numbers and comments of a script text. */
internal class ScriptHighlight(dark: Boolean) : VisualTransformation {
    private val label = if (dark) Color(0xFFFFA070) else Color(0xFFC2410C)
    private val known = if (dark) Color(0xFF8AB4FF) else Color(0xFF1D4ED8)
    private val unknown = if (dark) Color(0xFFFF8A8A) else Color(0xFFB91C1C)
    private val numberColor = if (dark) Color(0xFF5FD7A7) else Color(0xFF047857)
    private val commentColor = if (dark) Color(0xFFA8B0BD) else Color(0xFF5B6472)
    private val number = Regex("""\b(0x[0-9A-Fa-f]+|\d+)\b""")

    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            var offset = 0
            for (line in text.text.split('\n')) {
                val comment = listOf(line.indexOf(';'), line.indexOf("//")).filter { it >= 0 }.minOrNull() ?: -1
                val code = if (comment >= 0) line.substring(0, comment) else line
                val trimmed = code.trim()
                val indent = code.length - code.trimStart().length
                when {
                    trimmed.endsWith(":") -> addStyle(SpanStyle(color = label, fontWeight = FontWeight.Bold), offset + indent, offset + indent + trimmed.length)
                    trimmed.isNotEmpty() -> {
                        val nameEnd = trimmed.indexOf(' ').let { if (it < 0) trimmed.length else it }
                        val name = trimmed.substring(0, nameEnd)
                        val isKnown = name.startsWith(".") || ScriptCommands.byName(name) != null || ScriptCommands.movementIds.containsKey(name) || name == "Move"
                        addStyle(SpanStyle(color = if (isKnown) known else unknown, fontWeight = FontWeight.SemiBold), offset + indent, offset + indent + nameEnd)
                        for (m in number.findAll(trimmed, nameEnd)) addStyle(SpanStyle(color = numberColor), offset + indent + m.range.first, offset + indent + m.range.last + 1)
                    }
                }
                if (comment >= 0) addStyle(SpanStyle(color = commentColor), offset + comment, offset + line.length)
                offset += line.length + 1
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
private fun LevelScriptForm(file: LevelScript, scriptFiles: List<Int>, set: (LevelScript) -> Unit) {
    val targets = if (scriptFiles.isEmpty()) "No map uses this level script." else "The script numbers are scripts of script file " + scriptFiles.joinToString(", ")
    SectionCard("Level script", subtitle = targets) {
        Text("Runs when the map is entered (map change), when its screen is reset, or when the game is loaded on it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionCard("Triggers", actions = { Chip(file.triggers.count { it.type != LevelScript.VARIABLE_VALUE }.toString()) }) {
        file.triggers.forEachIndexed { i, t ->
            if (t.type == LevelScript.VARIABLE_VALUE) {
                Text("Variable triggers (below) are listed after this point.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                val types = LevelScript.TYPES.keys.sorted()
                PickerField("When", types.indexOf(t.type).coerceAtLeast(0), types.map { LevelScript.TYPES.getValue(it) }, { v -> set(file.copy(triggers = file.triggers.toMutableList().also { it[i] = t.copy(type = types[v]) })) }, Modifier.width(180.dp))
                NumberField("Script", t.script, 0..65535, { v -> set(file.copy(triggers = file.triggers.toMutableList().also { it[i] = t.copy(script = v) })) }, Modifier.width(110.dp))
                TextButton(onClick = { set(file.copy(triggers = file.triggers.filterIndexed { k, _ -> k != i })) }) { Text("Remove") }
            }
        }
        Button(onClick = { set(file.copy(triggers = file.triggers + LevelTrigger(2, 1))) }) { Text("Add trigger") }
    }
    SectionCard("Variable triggers", subtitle = "Run a script on map load while a variable has a value", actions = { Chip(file.variableTriggers.size.toString()) }) {
        file.variableTriggers.forEachIndexed { i, v ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                fun put(n: VariableTrigger) = set(file.withVariableTriggers(file.variableTriggers.toMutableList().also { it[i] = n }))
                NumberField("Variable", v.variable, 1..65535, { n -> put(v.copy(variable = n)) }, Modifier.width(110.dp))
                NumberField("Equals", v.value, 0..65535, { n -> put(v.copy(value = n)) }, Modifier.width(110.dp))
                NumberField("Script", v.script, 0..65535, { n -> put(v.copy(script = n)) }, Modifier.width(110.dp))
                TextButton(onClick = { set(file.withVariableTriggers(file.variableTriggers.filterIndexed { k, _ -> k != i })) }) { Text("Remove") }
            }
        }
        Button(onClick = { set(file.withVariableTriggers(file.variableTriggers + VariableTrigger(0x4000, 0, 1))) }) { Text("Add variable trigger") }
    }
}

private val SOUND_COMMANDS = Regex("""^\s*(PlaySE|StopSE|WaitSE|PlayFanfare|PlayBGM|TempBGM|PlayCry)\s+(0x[0-9A-Fa-f]+|\d+)""", RegexOption.MULTILINE)

/** The sounds a script plays, with a button for each; only shown when there is any. */
@Composable
private fun ScriptSounds(text: String) {
    val found = remember(text) {
        SOUND_COMMANDS.findAll(text).mapNotNull { m ->
            val id = m.groupValues[2].let { if (it.startsWith("0x") || it.startsWith("0X")) it.substring(2).toIntOrNull(16) else it.toIntOrNull() } ?: return@mapNotNull null
            m.groupValues[1] to id
        }.distinct().toList()
    }
    if (found.isEmpty()) return
    SectionCard("Sounds in this script", subtitle = "Press play to hear what the commands play") {
        found.forEach { (command, id) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$command $id", style = io.github.legendaryredfox.foxhg.app.ui.Fox.mono, modifier = Modifier.width(160.dp))
                if (command == "PlayCry") io.github.legendaryredfox.foxhg.app.ui.CryButton(id) else io.github.legendaryredfox.foxhg.app.ui.SoundButton(id)
            }
        }
        io.github.legendaryredfox.foxhg.app.ui.AudioStatus()
    }
}
