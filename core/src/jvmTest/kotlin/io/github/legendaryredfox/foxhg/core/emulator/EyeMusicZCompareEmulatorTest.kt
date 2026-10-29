package io.github.legendaryredfox.foxhg.core.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compares the sound the `EyeMusic*EmulatorTest` classes recorded (its name sorts last, so it runs after them), all from the same saved game and the same frames. The runs are not bit for bit equal (the
 * samples differ from the first one on), but the loudness over twenty slices of the ten seconds is the song's own: the Youngster's and the Lass's differ by about 4000
 * (an RMS of 16 bit samples) and two runs of one song by under 200. A new class with no music row must sound like the Youngster (sequence 1108); one given the music
 * 1113 like the Lass. The recordings come from the runs of the same build: without all four fresh files (a class run alone) this passes with a notice.
 */
class EyeMusicZCompareEmulatorTest {
    private fun read(tag: String): DoubleArray? =
        EyeMusicRun.dir.resolve("$tag.pcm").takeIf { Files.exists(it) && Duration.between(Files.getLastModifiedTime(it).toInstant(), Instant.now()) < Duration.ofMinutes(90) }
            ?.let { envelope(Files.readAllBytes(it)) }

    /** The RMS of the mono mix in each of [SLICES] equal slices. */
    private fun envelope(pcm: ByteArray): DoubleArray {
        val samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().let { b -> ShortArray(b.remaining()).also { b.get(it) } }
        val mono = DoubleArray(samples.size / 2) { (samples[2 * it] + samples[2 * it + 1]).toDouble() }
        val width = mono.size / SLICES
        return DoubleArray(SLICES) { i -> Math.sqrt((i * width until (i + 1) * width).sumOf { mono[it] * mono[it] } / width) }
    }

    private fun distance(a: DoubleArray, b: DoubleArray) = Math.sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) } / a.size)

    @Test
    fun aNewClassPlaysTheSongItWasGiven() {
        val tags = listOf("youngster", "lass", "new-plain", "new-music")
        val runs = tags.associateWith { read(it) }
        if (runs.values.any { it == null }) { println("SKIPPED: missing or old recordings in ${EyeMusicRun.dir.toAbsolutePath()}: ${runs.filterValues { it == null }.keys}"); return }
        val (youngster, lass, plain, sung) = tags.map { runs.getValue(it)!! }
        println("EYEMUSIC distances: youngster-lass ${distance(youngster, lass).toInt()}, no row: to the Youngster ${distance(plain, youngster).toInt()}, to the Lass ${distance(plain, lass).toInt()}; music 1113: to the Lass ${distance(sung, lass).toInt()}, to the Youngster ${distance(sung, youngster).toInt()}")
        assertTrue(distance(youngster, lass) > 1000, "the two vanilla songs sound different")
        assertTrue(distance(plain, youngster) * 5 < distance(plain, lass), "a new class with no music row plays the Youngster's song")
        assertTrue(distance(sung, lass) * 5 < distance(sung, youngster), "a new class with the music 1113 plays the Lass's song")
    }

    private companion object { const val SLICES = 20 }
}
