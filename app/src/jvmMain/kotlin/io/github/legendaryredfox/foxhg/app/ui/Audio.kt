package io.github.legendaryredfox.foxhg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.core.sound.PcmClip
import io.github.legendaryredfox.foxhg.core.sound.Sounds
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import kotlin.concurrent.thread

/**
 * Plays one clip at a time through the system's audio output. Rendering happens on a worker thread; [playing] is the key
 * of what is playing now, [error] says why nothing could be played (no sound device, for example).
 */
class AudioPlayer {
    var playing: String? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set
    private var clip: Clip? = null
    private var generation = 0

    fun toggle(key: String, produce: () -> PcmClip?) {
        if (playing == key) { stop(); return }
        stop()
        val mine = ++generation
        playing = key
        error = null
        thread(isDaemon = true, name = "foxhg-audio") {
            try {
                val pcm = produce()
                if (pcm == null) { if (generation == mine) { error = "This sound has nothing to play"; playing = null }; return@thread }
                val bytes = ByteArray(pcm.samples.size * 2)
                pcm.samples.forEachIndexed { i, s -> bytes[i * 2] = s.toInt().toByte(); bytes[i * 2 + 1] = (s.toInt() shr 8).toByte() }
                val next = AudioSystem.getClip()
                next.open(AudioFormat(pcm.sampleRate.toFloat(), 16, 2, true, false), bytes, 0, bytes.size)
                next.addLineListener { e -> if (e.type == LineEvent.Type.STOP) { next.close(); if (generation == mine) playing = null } }
                synchronized(this) {
                    if (generation != mine) { next.close(); return@thread }
                    clip = next
                }
                next.start()
            } catch (e: Exception) {
                if (generation == mine) { error = "Cannot play sound: ${e.message ?: e::class.simpleName}"; playing = null }
            }
        }
    }

    fun stop() {
        generation++
        synchronized(this) { clip?.let { it.stop(); it.close() }; clip = null }
        playing = null
    }
}

/** The sounds of the open project and the player they go to; null when nothing is open. */
class SoundContext(val sounds: Sounds, val player: AudioPlayer)

val LocalSounds: ProvidableCompositionLocal<SoundContext?> = compositionLocalOf { null }

@Composable
fun ProvideSounds(context: SoundContext?, content: @Composable () -> Unit) = CompositionLocalProvider(LocalSounds provides context, content = content)

/** A small play / stop button; [produce] makes the clip when it is pressed. */
@Composable
fun PlayButton(key: String, produce: (Sounds) -> PcmClip?, enabled: Boolean = true) {
    val context = LocalSounds.current ?: return
    val active = context.player.playing == key
    Box(
        Modifier.size(26.dp).testTag("play:$key").let { if (enabled) it.clickable { context.player.toggle(key) { produce(context.sounds) } } else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (active) "■" else "▶",
            color = if (!enabled) MaterialTheme.colorScheme.outline else if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** Plays the cry of a species. */
@Composable
fun CryButton(species: Int) = PlayButton("cry$species", { it.cry(species) }, enabled = species in 1..Sounds.MAX_CRY)

/** Plays sound number [id] (a sound effect, jingle or music), with its name next to it. */
@Composable
fun SoundButton(id: Int, withName: Boolean = true) {
    val context = LocalSounds.current ?: return
    val name = context.sounds.name(id)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayButton("seq$id", { it.sequence(id) }, enabled = name != null)
        if (withName) Text(name ?: "no sound $id", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** What went wrong with the last attempt to play something, if anything. */
@Composable
fun AudioStatus() {
    val error = LocalSounds.current?.player?.error ?: return
    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
