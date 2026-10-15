package io.github.romulofer.foxhg.core.sound

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32

/** A decoded sample: mono PCM in -1..1, its rate in Hz and where it loops (in samples), if it does. */
class Wave(val samples: FloatArray, val sampleRate: Int, val loopStart: Int, val loopEnd: Int, val loops: Boolean)

/** One sound of an instrument: a sample (or a generated pulse/noise), its pitch and its envelope. */
class Region(
    /** 1 sample, 2 pulse wave (the sample number is the duty), 3 noise. */
    val kind: Int,
    val waveIndex: Int,
    val waveArchive: Int,
    val baseNote: Int,
    val attack: Int,
    val decay: Int,
    val sustain: Int,
    val release: Int,
    val pan: Int,
)

/** A bank of instruments ("SBNK"): which region plays for a program and a note. */
class Bank(private val instruments: List<Instrument?>) {
    class Instrument(val type: Int, val regions: List<Region>, val lowNote: Int, val highNote: Int, val endNotes: List<Int>)

    fun region(program: Int, note: Int): Region? {
        val i = instruments.getOrNull(program) ?: return null
        return when (i.type) {
            1, 2, 3 -> i.regions.firstOrNull()
            16 -> if (note in i.lowNote..i.highNote) i.regions.getOrNull(note - i.lowNote) else null
            17 -> { val k = i.endNotes.indexOfFirst { note <= it }; if (k >= 0) i.regions.getOrNull(k) else null }
            else -> null
        }
    }

    companion object {
        fun parse(bytes: ByteArray): FoxResult<Bank> {
            if (bytes.size < 0x3C || bytes.decodeToString(0, 4) != "SBNK") return FoxResult.Err("Not a bank (SBNK)")
            val count = bytes.u32(0x38).toInt()
            fun region(o: Int, withType: Boolean): Region {
                val p = if (withType) o + 2 else o // the regions of range banks start with an unused u16
                val kind = if (withType) bytes.u16(o) else 1
                return Region(kind, bytes.u16(p), bytes.u16(p + 2), bytes[p + 4].toInt() and 0xFF, bytes[p + 5].toInt() and 0xFF, bytes[p + 6].toInt() and 0xFF, bytes[p + 7].toInt() and 0xFF, bytes[p + 8].toInt() and 0xFF, bytes[p + 9].toInt() and 0xFF)
            }
            val list = List(count) { i ->
                val type = bytes[0x3C + i * 4].toInt() and 0xFF
                val o = bytes.u16(0x3C + i * 4 + 1)
                when (type) {
                    1, 2, 3 -> Bank.Instrument(type, listOf(Region(type, bytes.u16(o), bytes.u16(o + 2), bytes[o + 4].toInt() and 0xFF, bytes[o + 5].toInt() and 0xFF, bytes[o + 6].toInt() and 0xFF, bytes[o + 7].toInt() and 0xFF, bytes[o + 8].toInt() and 0xFF, bytes[o + 9].toInt() and 0xFF)), 0, 127, emptyList())
                    16 -> {
                        val low = bytes[o].toInt() and 0xFF
                        val high = bytes[o + 1].toInt() and 0xFF
                        Bank.Instrument(16, List(maxOf(0, high - low + 1)) { k -> region(o + 2 + k * 12, true) }, low, high, emptyList())
                    }
                    17 -> {
                        val ends = (0 until 8).map { bytes[o + it].toInt() and 0xFF }.takeWhile { it != 0 }
                        Bank.Instrument(17, List(ends.size) { k -> region(o + 8 + k * 12, true) }, 0, 127, ends)
                    }
                    else -> null
                }
            }
            return FoxResult.Ok(Bank(list))
        }
    }
}

/** A wave archive ("SWAR"): the samples a bank's instruments refer to. */
class WaveArchive(private val bytes: ByteArray) {
    private val count: Int = if (bytes.size >= 0x3C && bytes.decodeToString(0, 4) == "SWAR") bytes.u32(0x38).toInt() else 0
    private val cache = HashMap<Int, Wave?>()

    val size: Int get() = count

    fun wave(index: Int): Wave? = if (index !in 0 until count) null else cache.getOrPut(index) { decode(bytes.u32(0x3C + index * 4).toInt()) }

    private fun decode(o: Int): Wave? {
        if (o <= 0 || o + 12 > bytes.size) return null
        val type = bytes[o].toInt() and 0xFF
        val loops = bytes[o + 1].toInt() != 0
        val rate = bytes.u16(o + 2)
        val loopStartWords = bytes.u16(o + 6)
        val loopLengthWords = bytes.u32(o + 8).toInt()
        val dataStart = o + 12
        val dataLength = minOf((loopStartWords + loopLengthWords) * 4, bytes.size - dataStart)
        if (dataLength <= 0) return null
        return when (type) {
            0 -> Wave(FloatArray(dataLength) { bytes[dataStart + it] / 128f }, rate, loopStartWords * 4, dataLength, loops)
            1 -> {
                val n = dataLength / 2
                Wave(FloatArray(n) { bytes.u16(dataStart + it * 2).toShort() / 32768f }, rate, loopStartWords * 2, n, loops)
            }
            2 -> adpcm(dataStart, dataLength, rate, loopStartWords, loops)
            else -> null
        }
    }

    /** IMA ADPCM: a 4-byte header (first sample, step index), then 4-bit codes, low nibble first. */
    private fun adpcm(start: Int, length: Int, rate: Int, loopStartWords: Int, loops: Boolean): Wave {
        var predictor = bytes.u16(start).toShort().toInt()
        var index = bytes.u16(start + 2) and 0x7F
        val n = (length - 4) * 2
        val out = FloatArray(n)
        var loopStartSample = 0
        for (i in 0 until n) {
            if (i == (loopStartWords - 1) * 8 && loopStartWords > 1) loopStartSample = i
            val byte = bytes[start + 4 + i / 2].toInt() and 0xFF
            val code = if (i % 2 == 0) byte and 0xF else byte shr 4
            val step = STEPS[index.coerceIn(0, 88)]
            var diff = step shr 3
            if (code and 1 != 0) diff += step shr 2
            if (code and 2 != 0) diff += step shr 1
            if (code and 4 != 0) diff += step
            predictor = (if (code and 8 != 0) predictor - diff else predictor + diff).coerceIn(-32768, 32767)
            index = (index + INDEX_ADJUST[code and 7]).coerceIn(0, 88)
            out[i] = predictor / 32768f
        }
        return Wave(out, rate, if (loopStartWords > 1) (loopStartWords - 1) * 8 else 0, n, loops)
    }

    private companion object {
        val INDEX_ADJUST = intArrayOf(-1, -1, -1, -1, 2, 4, 6, 8)
        val STEPS = intArrayOf(
            7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 130, 143,
            157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796, 876, 963, 1060, 1166, 1282, 1411, 1552,
            1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487,
            12635, 13899, 15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767,
        )
    }
}
