package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.research.Usage
import io.github.legendaryredfox.foxhg.core.data.research.UsageKind
import io.github.legendaryredfox.foxhg.core.data.research.Usages
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The Research Helper: where the game uses a flag, variable, item, trainer or overworld sprite entry, with a link to each script or event file. */
class ResearchEditorModel(private val project: RomProject) {
    val names: GameNames? = project.names
    var kind: UsageKind by mutableStateOf(UsageKind.FLAG)
    var id: Int by mutableIntStateOf(0)
    var results: List<Usage>? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    /** What was searched for, as the results are titled (the fields can change after). */
    var searched: String by mutableStateOf("")
        private set

    fun search() {
        when (val r = Usages.find(project, kind, id)) {
            is FoxResult.Ok -> { results = r.value; error = null; searched = "${kind.label} $id${nameOf(kind, id)?.let { " ($it)" } ?: ""}" }
            is FoxResult.Err -> { results = null; error = r.message }
        }
    }

    fun nameOf(kind: UsageKind, id: Int): String? = when (kind) {
        UsageKind.ITEM -> names?.items?.getOrNull(id)
        else -> null
    }

    companion object { const val LIMIT = 300 }
}

@Composable
fun ResearchEditor(model: ResearchEditorModel, onOpen: (EditorKind, Int) -> Unit, modifier: Modifier = Modifier) {
    DetailPane(modifier) {
        Text("Research", style = MaterialTheme.typography.headlineSmall)
        SectionCard("Find uses", subtitle = "Every script command and event that uses a flag, variable, item, trainer or overworld sprite entry") {
            FieldFlow {
                for (k in UsageKind.entries) ToggleChip(k.label, model.kind == k, { model.kind = k }, Modifier.testTag("research-kind:${k.name}"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                NumberField("Number", model.id, 0..65535, { model.id = it }, Modifier.width(130.dp), tag = "Research number")
                Button(onClick = model::search, modifier = Modifier.testTag("research-find")) { Text("Find") }
            }
            model.nameOf(model.kind, model.id)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Scripts are matched by the type of the command's parameter, so a plain number that equals a variable is not told apart. Trainers on a map are found by the script number of their object (3000 + trainer - 1 or 5000 + trainer - 1).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val results = model.results
        if (results != null) {
            SectionCard("${model.searched}: ${results.size} ${if (results.size == 1) "use" else "uses"}", subtitle = if (results.size > ResearchEditorModel.LIMIT) "The first ${ResearchEditorModel.LIMIT} are listed." else "Click one to open it.") {
                if (results.isEmpty()) Text("Nothing uses it.", style = MaterialTheme.typography.bodySmall)
                results.take(ResearchEditorModel.LIMIT).forEachIndexed { i, u ->
                    val where = if (u.place == Usage.Place.SCRIPT) "Script ${u.file}, item ${u.index}" else "Events ${u.file}, #${u.index}"
                    TextButton(onClick = { onOpen(if (u.place == Usage.Place.SCRIPT) EditorKind.SCRIPTS else EditorKind.EVENTS, u.file) }, modifier = Modifier.testTag("research-result:$i")) {
                        Text("$where: ${u.text}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
