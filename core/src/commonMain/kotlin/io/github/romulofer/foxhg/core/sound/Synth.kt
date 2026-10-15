package io.github.romulofer.foxhg.core.sound

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Stereo 16-bit audio, interleaved left/right. */
class PcmClip(val samples: ShortArray, val sampleRate: Int) {
    val seconds: Double get() = samples.size / 2.0 / sampleRate
}

/**
 * Plays a sequence of the sound archive to a clip, for previews. It follows the DS sound system closely where that is
 * simple (tracks, notes, instruments, samples, ADPCM, loops, volume, pan, pitch bend, tempo) and approximates the rest:
 * envelope times are curves fitted by ear, modulation, portamento and sweeps are ignored, and the variable and random
 * commands of sequences are skipped. Good for hearing what a sound is, not a bit-exact player.
 */
class Synth(private val sdat: Sdat) {
    private class Note(val start: Int, val length: Int, val region: Region, val pitch: Double, val gain: Double, val left: Double, val right: Double, val wave: Wave?, val untilEnd: Boolean)

    private val banks = HashMap<Int, Bank?>()
    private val archives = HashMap<Int, WaveArchive?>()

    private fun bank(id: Int): Bank? = banks.getOrPut(id) {
        val info = sdat.bank(id) ?: return@getOrPut null
        sdat.file(info.file)?.let { (Bank.parse(it) as? FoxResult.Ok)?.value }
    }

    private fun archive(id: Int): WaveArchive? = archives.getOrPut(id) { sdat.waveArchive(id)?.let { WaveArchive(it) } }

    /** Renders sequence [id] (with [bankOverride] instead of its own bank, as cries do) for at most [maxSeconds]. */
    fun render(id: Int, bankOverride: Int? = null, maxSeconds: Double = 30.0): PcmClip? {
        val seq = sdat.sequence(id) ?: return null
        val data = sdat.file(seq.file) ?: return null
        if (data.size < 0x20 || data.decodeToString(0, 4) != "SSEQ") return null
        val bankId = bankOverride ?: seq.bank
        val bank = bank(bankId) ?: return null
        val bankInfo = sdat.bank(bankId) ?: return null
        val notes = ArrayList<Note>()
        val limit = (maxSeconds * RATE).toInt()
        sequence(data, bank, bankInfo, seq.volume, limit, notes)
        if (notes.isEmpty()) return null
        return mix(notes, limit)
    }

    private class Track(var pc: Int, val id: Int) {
        var wait = 0
        var program = 0
        var volume = 127
        var expression = 127
        var pan = 64
        var transpose = 0
        var bend = 0
        var bendRange = 2
        var noteWait = true
        var tie = false
        var attack = -1
        var decay = -1
        var sustain = -1
        var release = -1
        var ended = false
        val calls = ArrayList<Int>()
        val loops = ArrayList<IntArray>() // start pc, remaining (0 = forever)
    }

    private fun sequence(data: ByteArray, bank: Bank, info: BankInfo, seqVolume: Int, limit: Int, out: MutableList<Note>) {
        val base = data.u32(0x18).toInt()
        fun u8(p: Int) = data[p].toInt() and 0xFF
        fun varLen(t: Track): Int {
            var v = 0
            while (true) {
                val b = u8(t.pc++)
                v = (v shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) return v
            }
        }
        val tracks = ArrayList<Track>().also { it += Track(base, 0) }
        var tempo = 120
        var master = 127
        var sample = 0.0
        var guard = 0
        while (sample < limit && tracks.any { !it.ended } && guard++ < 2_000_000) {
            for (t in tracks.toList()) {
                if (t.ended) continue
                while (t.wait <= 0 && !t.ended) {
                    if (t.pc !in base until data.size) { t.ended = true; break }
                    val cmd = u8(t.pc++)
                    when {
                        cmd < 0x80 -> {
                            val velocity = u8(t.pc++)
                            val length = varLen(t)
                            val key = (cmd + t.transpose).coerceIn(0, 127)
                            val region = bank.region(t.program, key)
                            if (region != null) addNote(out, info, region, sample.toInt(), length, key, velocity, t, master, seqVolume, tempo)
                            if (t.noteWait) t.wait = length
                        }
                        cmd == 0x80 -> t.wait = varLen(t)
                        cmd == 0x81 -> t.program = varLen(t)
                        cmd == 0x93 -> { val id = u8(t.pc); val off = u8(t.pc + 1) or (u8(t.pc + 2) shl 8) or (u8(t.pc + 3) shl 16); t.pc += 4; if (tracks.size < 16 && tracks.none { it.id == id }) tracks += Track(base + off, id) }
                        cmd == 0x94 -> t.pc = base + (u8(t.pc) or (u8(t.pc + 1) shl 8) or (u8(t.pc + 2) shl 16))
                        cmd == 0x95 -> { val off = u8(t.pc) or (u8(t.pc + 1) shl 8) or (u8(t.pc + 2) shl 16); t.calls += t.pc + 3; t.pc = base + off }
                        cmd == 0xFD -> if (t.calls.isNotEmpty()) t.pc = t.calls.removeAt(t.calls.size - 1) else t.ended = true
                        cmd == 0xFF -> t.ended = true
                        cmd == 0xFE -> t.pc += 2
                        cmd == 0xD4 -> t.loops += intArrayOf(t.pc + 1, u8(t.pc))
                        cmd == 0xFC -> if (t.loops.isNotEmpty()) {
                            val loop = t.loops.last()
                            if (loop[1] == 0 || loop[1] > 1) { if (loop[1] > 1) loop[1]--; t.pc = loop[0] } else t.loops.removeAt(t.loops.size - 1)
                        }
                        cmd in 0xB0..0xBD -> t.pc += 3
                        cmd == 0xA0 -> t.pc += 4
                        cmd == 0xA1 -> t.pc += 1
                        cmd == 0xA2 -> {}
                        cmd == 0xE0 || cmd == 0xE3 -> t.pc += 2
                        cmd == 0xE1 -> { tempo = (u8(t.pc) or (u8(t.pc + 1) shl 8)).coerceAtLeast(1); t.pc += 2 }
                        cmd in 0xC0..0xD7 -> {
                            val v = u8(t.pc++)
                            when (cmd) {
                                0xC0 -> t.pan = v
                                0xC1 -> t.volume = v
                                0xC2 -> master = v
                                0xC3 -> t.transpose = v.toByte().toInt()
                                0xC4 -> t.bend = v.toByte().toInt()
                                0xC5 -> t.bendRange = v
                                0xC7 -> t.noteWait = v != 0
                                0xC8 -> t.tie = v != 0
                                0xD0 -> t.attack = v
                                0xD1 -> t.decay = v
                                0xD2 -> t.sustain = v
                                0xD3 -> t.release = v
                                0xD5 -> t.expression = v
                            }
                        }
                        else -> t.ended = true // a command we do not know: stop this track rather than play garbage
                    }
                }
            }
            // advance one tick
            sample += RATE * 60.0 / (tempo * 48.0)
            for (t in tracks) if (!t.ended) t.wait--
        }
    }

    private fun addNote(out: MutableList<Note>, info: BankInfo, region: Region, start: Int, ticks: Int, key: Int, velocity: Int, t: Track, master: Int, seqVolume: Int, tempo: Int) {
        val archiveId = info.waveArchives.getOrNull(region.waveArchive) ?: -1
        val wave = if (region.kind == 1 && archiveId >= 0) archive(archiveId)?.wave(region.waveIndex) else null
        if (region.kind == 1 && wave == null) return
        val samplesPerTick = RATE * 60.0 / (tempo * 48.0)
        val untilEnd = ticks == 0 && wave != null && !wave.loops
        val length = if (ticks == 0 && !untilEnd) samplesPerTick.toInt() else (ticks * samplesPerTick).toInt()
        val semitones = key - region.baseNote + t.bend / 127.0 * t.bendRange
        val pitch = when (region.kind) {
            1 -> wave!!.sampleRate / RATE.toDouble() * 2.0.pow(semitones / 12.0)
            2 -> 440.0 * 2.0.pow((key - 69 + t.bend / 127.0 * t.bendRange) / 12.0) / RATE
            else -> 8000.0 * 2.0.pow((key - 60) / 12.0) / RATE
        }
        fun sq(v: Int) = (v / 127.0).let { it * it }
        val gain = sq(velocity) * sq(t.volume) * sq(t.expression) * sq(master) * (seqVolume / 127.0)
        val pan = (t.pan + region.pan - 64).coerceIn(0, 127) / 127.0 * PI / 2
        val adsr = region.copyEnvelope(t)
        out += Note(start, length, adsr, pitch, gain, cos(pan), sin(pan), wave, untilEnd)
    }

    private fun Region.copyEnvelope(t: Track) = Region(kind, waveIndex, waveArchive, baseNote, if (t.attack >= 0) t.attack else attack, if (t.decay >= 0) t.decay else decay, if (t.sustain >= 0) t.sustain else sustain, if (t.release >= 0) t.release else release, pan)

    private fun mix(notes: List<Note>, limit: Int): PcmClip {
        var end = 0
        val prepared = notes.map { n ->
            val release = releaseSeconds(n.region.release)
            val tail = if (n.wave != null && !n.wave.loops && !n.untilEnd) 0 else (release * RATE).toInt()
            val natural = if (n.wave != null && !n.wave.loops) (n.wave.samples.size / n.pitch).toInt() else Int.MAX_VALUE
            val total = if (n.untilEnd) natural else minOf(n.length + tail, natural)
            end = maxOf(end, minOf(n.start + total, limit + RATE / 2))
            total
        }
        val left = FloatArray(end + 1)
        val right = FloatArray(end + 1)
        notes.forEachIndexed { i, n ->
            val total = prepared[i]
            val attack = attackSeconds(n.region.attack) * RATE
            val decay = decaySeconds(n.region.decay) * RATE
            val sustain = (n.region.sustain / 127.0).let { it * it }
            val release = maxOf(1.0, releaseSeconds(n.region.release) * RATE)
            var phase = 0.0
            var noise = 0x7FFF
            var held = 0.0
            for (k in 0 until total) {
                val at = n.start + k
                if (at >= end) break
                val envelope = when {
                    !n.untilEnd && k >= n.length -> held * (1.0 - (k - n.length) / release).coerceAtLeast(0.0)
                    k < attack -> k / attack
                    k < attack + decay -> 1.0 - (1.0 - sustain) * (k - attack) / maxOf(1.0, decay)
                    else -> sustain
                }.also { if (k < n.length || n.untilEnd) held = it }
                val value: Double = when (n.region.kind) {
                    1 -> {
                        val w = n.wave!!
                        var pos = phase
                        if (w.loops && pos >= w.loopEnd) { val span = (w.loopEnd - w.loopStart).coerceAtLeast(1); pos = w.loopStart + (pos - w.loopStart) % span }
                        val i0 = pos.toInt()
                        if (i0 >= w.samples.size - 1) 0.0 else {
                            val f = pos - i0
                            w.samples[i0] * (1 - f) + w.samples[i0 + 1] * f
                        }
                    }
                    2 -> if ((phase % 1.0) < (n.region.waveIndex.coerceIn(0, 6) + 1) / 8.0) 0.6 else -0.6
                    else -> { if ((phase % 1.0) < n.pitch) { val bit = (noise xor (noise shr 1)) and 1; noise = (noise shr 1) or (bit shl 14) }; if (noise and 1 == 0) 0.6 else -0.6 }
                }
                val s = (value * envelope * n.gain).toFloat()
                left[at] += s * n.left.toFloat()
                right[at] += s * n.right.toFloat()
                phase += n.pitch
            }
        }
        val peak = maxOf(left.maxOf { kotlin.math.abs(it) }, right.maxOf { kotlin.math.abs(it) })
        val scale = if (peak > 0.95f) 0.95f / peak else 1f
        val out = ShortArray((end + 1) * 2)
        for (i in 0..end) {
            out[i * 2] = (left[i] * scale * 32767).toInt().coerceIn(-32768, 32767).toShort()
            out[i * 2 + 1] = (right[i] * scale * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        return PcmClip(out, RATE)
    }

    private fun attackSeconds(a: Int) = if (a >= 127) 0.0 else 0.0005 * 2.0.pow((127 - a) / 10.0)
    private fun decaySeconds(d: Int) = if (d >= 127) 0.0 else 0.002 * 2.0.pow((127 - d) / 9.0)
    private fun releaseSeconds(r: Int) = minOf(4.0, 0.0005 * 2.0.pow((127 - r) / 9.0))

    companion object {
        const val RATE = 32768
    }
}
