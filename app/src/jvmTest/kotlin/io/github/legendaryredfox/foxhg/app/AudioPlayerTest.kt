package io.github.legendaryredfox.foxhg.app

import io.github.legendaryredfox.foxhg.app.ui.AudioPlayer
import io.github.legendaryredfox.foxhg.core.sound.PcmClip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioPlayerTest {
    private fun waitUntil(limitMs: Long = 5000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + limitMs
        while (System.currentTimeMillis() < end) { if (condition()) return true; Thread.sleep(20) }
        return condition()
    }

    @Test
    fun aSoundThatProducesNothingReportsItAndNeverThrows() {
        val player = AudioPlayer()
        player.toggle("none") { null }
        assertTrue(waitUntil { player.playing == null && player.error != null })
        assertEquals("This sound has nothing to play", player.error)
    }

    @Test
    fun aMachineWithoutAudioOutputReportsInsteadOfFailing() {
        val player = AudioPlayer()
        player.toggle("tone") { PcmClip(ShortArray(2 * 3200) { (if (it % 64 < 32) 8000 else -8000).toShort() }, 32768) }
        // With a sound device the clip plays and ends on its own; without one (CI, xvfb) the error says so.
        assertTrue(waitUntil(8000) { player.playing == null }, "playback ends or fails")
        assertTrue(player.error == null || player.error!!.startsWith("Cannot play sound"), player.error)
        player.stop()
        assertNull(player.playing)
        assertNotNull(player)
    }

    @Test
    fun pressingTheSameSoundTwiceStopsIt() {
        val player = AudioPlayer()
        player.toggle("a") { PcmClip(ShortArray(2 * 32768 * 5), 32768) }
        player.toggle("a") { error("must not render again") }
        assertNull(player.playing)
    }
}
