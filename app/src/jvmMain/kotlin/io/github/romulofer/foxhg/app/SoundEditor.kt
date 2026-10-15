package io.github.romulofer.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.AudioStatus
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.LocalSounds
import io.github.romulofer.foxhg.app.ui.PlayButton
import io.github.romulofer.foxhg.app.ui.SectionCard

/** Every sound of the game's sound archive, to listen to. The archive itself is not edited here. */
@Composable
fun SoundEditor(modifier: Modifier = Modifier) {
    val context = LocalSounds.current
    val sounds = context?.sounds
    val sdat = sounds?.sdat
    if (sounds == null || sdat == null) {
        Text("This ROM has no readable sound archive.", color = MaterialTheme.colorScheme.error, modifier = modifier)
        return
    }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableIntStateOf(sounds.sequences.firstOrNull()?.id ?: 0) }
    val all = remember(sounds) { sounds.sequences }
    val rows = remember(search, all) {
        val q = search.trim()
        all.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) || it.id.toString() == q }.map { it.id }
    }
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ListPane(
            rows = rows, selected = selected, search = search, onSearch = { search = it }, placeholder = "Search sounds…",
            label = { id -> "%04d  %s".format(id, sounds.name(id) ?: "") }, onSelect = { selected = it }, isEdited = { false },
            width = 330.dp,
        )
        DetailPane {
            val info = sdat.sequence(selected)
            EditorHeader(selected.toString(), info?.name ?: "No sound")
            if (info != null) {
                SectionCard("Sound", subtitle = describe(info.name), actions = { Chip("bank ${info.bank}") }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayButton("seq$selected", { it.sequence(selected) })
                        Text("Play", style = MaterialTheme.typography.bodyMedium)
                    }
                    AudioStatus()
                    Text(
                        "Sequence ${info.id}, volume ${info.volume}, file ${info.file}. Previews are rendered by foxhg's own player, so timbres can differ a little from the console.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun describe(name: String): String = when {
    name.startsWith("SEQ_SE_") -> "Sound effect"
    name.startsWith("SEQ_GS_") -> "Music"
    name.startsWith("SEQ_ME_") -> "Jingle"
    name.startsWith("SEQ_PV") -> "Pokémon cry template (played with each species' own bank)"
    else -> "Sound"
}
