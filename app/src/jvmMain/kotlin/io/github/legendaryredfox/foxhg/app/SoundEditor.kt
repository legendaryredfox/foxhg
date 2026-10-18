package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
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
import io.github.legendaryredfox.foxhg.app.ui.AudioStatus
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.LocalSounds
import io.github.legendaryredfox.foxhg.app.ui.PlayButton
import io.github.legendaryredfox.foxhg.app.ui.SectionCard

/** Every sound of the game's sound archive, to listen to. The archive itself is not edited here. */
@Composable
fun SoundEditor(modifier: Modifier = Modifier) {
    val context = LocalSounds.current
    val sounds = context?.sounds
    val sdat = sounds?.sdat
    if (sounds == null || sdat == null) {
        Text(stringResource(Res.string.sound_this_rom_has_no_readable_sound_arc), color = MaterialTheme.colorScheme.error, modifier = modifier)
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
            rows = rows, selected = selected, search = search, onSearch = { search = it }, placeholder = stringResource(Res.string.sound_search_sounds),
            label = { id -> "%04d  %s".format(id, sounds.name(id) ?: "") }, onSelect = { selected = it }, isEdited = { false },
            width = 330.dp,
        )
        DetailPane {
            val info = sdat.sequence(selected)
            EditorHeader(selected.toString(), info?.name ?: tr(Res.string.sound_no_sound))
            if (info != null) {
                SectionCard(stringResource(Res.string.sound_sound), subtitle = describe(info.name), actions = { Chip(stringResource(Res.string.sound_bank_x, info.bank)) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayButton("seq$selected", { it.sequence(selected) })
                        Text(stringResource(Res.string.sound_play), style = MaterialTheme.typography.bodyMedium)
                    }
                    AudioStatus()
                    Text(
                        stringResource(Res.string.sound_sequence_x_volume_x_file_x_preview, info.id, info.volume, info.file),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun describe(name: String): String = when {
    name.startsWith("SEQ_SE_") -> tr(Res.string.sound_sound_effect)
    name.startsWith("SEQ_GS_") -> "Music"
    name.startsWith("SEQ_ME_") -> "Jingle"
    name.startsWith("SEQ_PV") -> tr(Res.string.sound_pok_mon_cry_template_played_with_e)
    else -> "Sound"
}
