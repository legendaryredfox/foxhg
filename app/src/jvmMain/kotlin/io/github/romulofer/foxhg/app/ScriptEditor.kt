package io.github.romulofer.foxhg.app

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.buildAnnotatedString
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.app.ui.TextArea
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.mapheader.MapHeader
import io.github.romulofer.foxhg.core.data.mapheader.MapHeaderTable
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.script.LevelScript
import io.github.romulofer.foxhg.core.script.LevelTrigger
import io.github.romulofer.foxhg.core.script.ScriptCommands
import io.github.romulofer.foxhg.core.script.ScriptEntry
import io.github.romulofer.foxhg.core.script.ScriptText
import io.github.romulofer.foxhg.core.script.VariableTrigger

/** Script files labelled by the maps that use them. */
class ScriptLabels(project: RomProject) {
    private val headers: List<MapHeader> = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
    private val names = project.names

    private fun mapName(header: Int): String =
        headers.getOrNull(header)?.let { names?.locations?.getOrNull(it.locationName) }?.takeIf { it.isNotBlank() } ?: "Map $header"

    fun scriptUsers(file: Int): List<Int> = headers.indices.filter { headers[it].script == file }
    fun levelScriptUsers(file: Int): List<Int> = headers.indices.filter { headers[it].levelScript == file }

    /** Script files (header.script) of the maps that use level script [file]; its trigger numbers are scripts of those. */
    fun scriptFilesOfLevel(file: Int): List<Int> = levelScriptUsers(file).map { headers[it].script }.distinct()

    fun describe(users: List<Int>): String = users.joinToString(", ") { "$it (${mapName(it)})" }

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
class ScriptEditorModel(project: RomProject, labels: ScriptLabels, onChanged: () -> Unit = {}) {
    val files = RecordEditorModel(project, ScriptEntry.SPEC, labels::label, onChanged)

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
        draft = (files.selected as? ScriptEntry.Script)?.let { ScriptText.toText(it.file) } ?: ""
        draftError = null
    }
}

@Composable
fun ScriptEditor(model: ScriptEditorModel, labels: ScriptLabels, modifier: Modifier = Modifier) {
    RecordListDetail(model.files, modifier, onSelect = model::select) { entry ->
        val index = model.files.selectedIndex
        EditorHeader(index.toString(), nameOf(model.files.label(index)))
        val scriptUsers = labels.scriptUsers(index)
        val levelUsers = labels.levelScriptUsers(index)
        if (scriptUsers.isNotEmpty()) Text("Script of map header " + labels.describe(scriptUsers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (levelUsers.isNotEmpty()) Text("Level script of map header " + labels.describe(levelUsers), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (entry) {
            is ScriptEntry.Script -> SectionCard(
                "Script",
                subtitle = "Commands as in the script command database; a jump takes a label. Applied as you type once it is valid.",
                actions = { Chip("${entry.file.scriptCount} scripts") },
            ) {
                TextArea(model.draft, model::edit, invalid = model.draftError != null, modifier = Modifier.fillMaxWidth(), minHeight = androidx.compose.ui.unit.Dp(480f), transformation = ScriptHighlight)
                model.draftError?.let { Text("Not applied: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            is ScriptEntry.Level -> LevelScriptForm(entry.file, labels.scriptFilesOfLevel(index)) { changed -> model.files.update { ScriptEntry.Level(changed.aligned()) } }
            is ScriptEntry.Raw -> SectionCard("Not editable yet", subtitle = entry.reason, actions = { Chip("${entry.bytes.size} bytes") }) {
                Text(
                    entry.bytes.take(512).chunked(16).joinToString("\n") { row -> row.joinToString(" ") { "%02X".format(it) } } + if (entry.bytes.size > 512) "\n…" else "",
                    style = io.github.romulofer.foxhg.app.ui.Fox.mono,
                )
            }
        }
    }
}

/** Colours labels, command names, numbers and comments of a script text. */
private object ScriptHighlight : VisualTransformation {
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
                    trimmed.endsWith(":") -> addStyle(SpanStyle(color = Color(0xFFC2410C), fontWeight = FontWeight.Bold), offset + indent, offset + indent + trimmed.length)
                    trimmed.isNotEmpty() -> {
                        val nameEnd = trimmed.indexOf(' ').let { if (it < 0) trimmed.length else it }
                        val name = trimmed.substring(0, nameEnd)
                        val known = name.startsWith(".") || ScriptCommands.byName(name) != null || ScriptCommands.movementIds.containsKey(name) || name == "Move"
                        addStyle(SpanStyle(color = if (known) Color(0xFF1D4ED8) else Color(0xFFB91C1C), fontWeight = FontWeight.SemiBold), offset + indent, offset + indent + nameEnd)
                        for (m in number.findAll(trimmed, nameEnd)) addStyle(SpanStyle(color = Color(0xFF047857)), offset + indent + m.range.first, offset + indent + m.range.last + 1)
                    }
                }
                if (comment >= 0) addStyle(SpanStyle(color = Color(0xFF6B7280)), offset + comment, offset + line.length)
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
