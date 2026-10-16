package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.ConfirmDialog
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.patch.PatchStatus
import io.github.legendaryredfox.foxhg.core.patch.RomPatch
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** State of the ROM toolbox: the patches, their status for the open ROM and the result of the last one applied. */
class PatchToolboxModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    val patches: List<RomPatch> = DsprePatches.all
    private var version by mutableIntStateOf(0)
    var message: String? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    fun status(patch: RomPatch): PatchStatus {
        version // subscribe
        return patch.status(project)
    }

    fun changes(patch: RomPatch): List<String> = patch.changesFor(project)

    fun apply(patch: RomPatch) {
        message = null
        error = null
        when (val r = patch.apply(project)) {
            is FoxResult.Ok -> { message = "\"${patch.title}\" applied. Ctrl+Z undoes it; the ROM changes when it is built."; onChanged() }
            is FoxResult.Err -> error = "\"${patch.title}\" failed: ${r.message}"
        }
        version++
    }
}

@Composable
fun PatchToolbox(model: PatchToolboxModel, modifier: Modifier = Modifier) {
    var asking by remember { mutableStateOf<RomPatch?>(null) }
    DetailPane(modifier) {
        Text("ROM toolbox", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Patches to the game's code and data. They change the project, not the ROM on disk: Build ROM… writes them. Each one checks the bytes it replaces " +
                "first and refuses a ROM that differs from a clean HeartGold English. They were not tried in game (a patched ROM was only booted to the intro).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        model.message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.testTag("toolbox-message")) }
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("toolbox-error")) }
        for (patch in model.patches) {
            val status = model.status(patch)
            SectionCard(patch.title, subtitle = patch.description) {
                var showChanges by remember(patch.id) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (status) {
                        PatchStatus.Applied -> Chip("Applied", emphasis = true, modifier = Modifier.testTag("status:${patch.id}"))
                        PatchStatus.Available -> Chip("Not applied", modifier = Modifier.testTag("status:${patch.id}"))
                        is PatchStatus.Unavailable -> Chip(status.reason, modifier = Modifier.testTag("status:${patch.id}"))
                    }
                    OutlinedButton(onClick = { showChanges = !showChanges }) { Text(if (showChanges) "Hide changes" else "Show changes") }
                    Button(onClick = { asking = patch }, enabled = status == PatchStatus.Available, modifier = Modifier.testTag("apply:${patch.id}")) { Text("Apply") }
                }
                if (showChanges) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (line in model.changes(patch)) Text(line, style = io.github.legendaryredfox.foxhg.app.ui.Fox.mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    asking?.let { patch ->
        ConfirmDialog(
            "Apply \"${patch.title}\"?",
            "These changes go into the project (Ctrl+Z undoes them):\n\n" + model.changes(patch).joinToString("\n") { "- $it" } +
                "\n\nIf you do not know what they do, do not apply them: a wrong code patch can stop the game from starting.",
            "Apply",
            onConfirm = { asking = null; model.apply(patch) },
            onDismiss = { asking = null },
        )
    }
}
