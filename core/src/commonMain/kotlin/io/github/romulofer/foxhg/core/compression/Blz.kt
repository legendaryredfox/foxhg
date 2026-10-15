package io.github.romulofer.foxhg.core.compression

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.u32
import io.github.romulofer.foxhg.core.putU32

/**
 * "Bottom LZ" code compression used by the ARM9 binary and the overlays (the BIOS/SDK call is
 * `MI_UncompressBackward`). Written from the format, not from another tool's source.
 *
 * Layout of a compressed file, read from its end:
 * ```
 * u32 extraSize      decompressed size = file length + extraSize
 * u8  headerLen      size of this header including 0xFF padding (>= 8)
 * u24 compressedLen  bytes at the end that are compressed, header included; the bytes before them pass through
 * ... 0xFF padding up to headerLen
 * ```
 * The stream before the header is read **backwards** and writes the output backwards. It is LZ10-like: a flag byte
 * (MSB first), then per flag bit either a literal byte or a 2-byte match `b1 b2` with
 * `length = (b1 >> 4) + 3` and `disp = (((b1 & 0xF) << 8) | b2) + 3`, copying from `disp` bytes behind the write
 * position (toward the end of the file).
 *
 * The hardware decompresses in place, so a valid stream must never let the write pointer run into unread input.
 * [compress] guarantees that (leaving an uncompressed prefix when needed) and [isInPlaceSafe] checks it.
 */
object Blz {
    private const val MIN_MATCH = 3
    private const val MAX_MATCH = 18
    private const val MIN_DISP = 3
    private const val MAX_DISP = 0x1002
    private const val MAX_CHAIN = 96

    private class Header(val length: Int, val headerLen: Int, val compressedLen: Int, val extraSize: Int) {
        val passthrough: Int get() = length - compressedLen
        val outputLength: Int get() = length + extraSize - passthrough
    }

    private fun readHeader(data: ByteArray): FoxResult<Header> {
        if (data.size < 8) return FoxResult.Err("BLZ: data shorter than its 8-byte header")
        val word = data.u32(data.size - 8)
        val headerLen = (word ushr 24).toInt()
        val compressedLen = (word and 0xFFFFFF).toInt()
        val extra = data.u32(data.size - 4).toInt()
        if (headerLen < 8 || headerLen > compressedLen || compressedLen > data.size) {
            return FoxResult.Err("BLZ: implausible header (headerLen=$headerLen compressedLen=$compressedLen size=${data.size})")
        }
        for (i in data.size - headerLen until data.size - 8) {
            if (data[i] != 0xFF.toByte()) return FoxResult.Err("BLZ: header padding is not 0xFF")
        }
        return FoxResult.Ok(Header(data.size, headerLen, compressedLen, extra))
    }

    /** True when the last 8 bytes look like a BLZ header with a non-zero extra size. */
    fun looksCompressed(data: ByteArray): Boolean {
        val header = (readHeader(data) as? FoxResult.Ok)?.value ?: return false
        return header.extraSize != 0
    }

    fun decompress(data: ByteArray): FoxResult<ByteArray> {
        val header = when (val h = readHeader(data)) {
            is FoxResult.Ok -> h.value
            is FoxResult.Err -> return h
        }
        if (header.extraSize == 0) return FoxResult.Ok(data.copyOf())
        val outLength = header.outputLength
        if (outLength < 0) return FoxResult.Err("BLZ: negative output length")
        val out = ByteArray(header.passthrough + outLength)
        data.copyInto(out, 0, 0, header.passthrough)

        // Logical streams: input read backwards from `inEnd`, output written backwards from the end of `out`.
        var read = data.size - header.headerLen
        val inStart = header.passthrough
        var written = 0
        val total = outLength
        var flags = 0
        var mask = 0
        while (written < total) {
            if (mask == 0) {
                if (read <= inStart) return FoxResult.Err("BLZ: input ended before the output was complete")
                flags = data[--read].toInt() and 0xFF
                mask = 0x80
            }
            val isMatch = flags and mask != 0
            mask = mask ushr 1
            if (isMatch) {
                if (read - 2 < inStart) return FoxResult.Err("BLZ: truncated match")
                val b1 = data[--read].toInt() and 0xFF
                val b2 = data[--read].toInt() and 0xFF
                val length = (b1 ushr 4) + MIN_MATCH
                val disp = (((b1 and 0x0F) shl 8) or b2) + MIN_DISP
                if (disp > written) return FoxResult.Err("BLZ: match reaches before the start of the output")
                repeat(length) {
                    if (written >= total) return@repeat
                    out[out.size - 1 - written] = out[out.size - 1 - (written - disp)]
                    written++
                }
            } else {
                if (read <= inStart) return FoxResult.Err("BLZ: truncated literal")
                out[out.size - 1 - written] = data[--read]
                written++
            }
        }
        return FoxResult.Ok(out)
    }

    /**
     * True when decompressing [data] in place never overwrites input that has not been read yet. Real ROM data
     * satisfies this; so does everything [compress] produces.
     */
    fun isInPlaceSafe(data: ByteArray): Boolean {
        val header = (readHeader(data) as? FoxResult.Ok)?.value ?: return false
        if (header.extraSize == 0) return true
        val outEnd = header.length + header.extraSize
        var readIndex = data.size - header.headerLen
        val inStart = header.passthrough
        var written = 0
        val total = header.outputLength
        var flags = 0
        var mask = 0
        while (written < total) {
            if (mask == 0) {
                if (readIndex <= inStart) return false
                flags = data[--readIndex].toInt() and 0xFF
                mask = 0x80
            }
            val isMatch = flags and mask != 0
            mask = mask ushr 1
            val count: Int
            if (isMatch) {
                if (readIndex - 2 < inStart) return false
                val b1 = data[--readIndex].toInt() and 0xFF
                readIndex--
                count = (b1 ushr 4) + MIN_MATCH
            } else {
                if (readIndex <= inStart) return false
                readIndex--
                count = 1
            }
            repeat(count) {
                if (written < total) {
                    if (outEnd - written - 1 < readIndex) return false
                    written++
                }
            }
        }
        return true
    }

    /**
     * Compresses [data]. The first [rawPrefix] bytes are never compressed (pass 0 for overlays; the ARM9 keeps its
     * leading boot area raw). Returns an error when the result would not be smaller than the input.
     */
    fun compress(data: ByteArray, rawPrefix: Int = 0): FoxResult<ByteArray> {
        require(rawPrefix in 0..data.size) { "rawPrefix $rawPrefix outside 0..${data.size}" }
        var passthrough = rawPrefix
        repeat(12) {
            val attempt = encode(data, passthrough)
            when (attempt) {
                is Attempt.Done -> return FoxResult.Ok(attempt.bytes)
                is Attempt.TooBig -> return FoxResult.Err("BLZ: data does not shrink (${data.size - passthrough} bytes)")
                is Attempt.Unsafe -> {
                    val next = data.size - attempt.safeTailBytes
                    passthrough = if (next > passthrough) next else passthrough + 256
                    if (passthrough >= data.size) return FoxResult.Err("BLZ: no in-place safe encoding found")
                }
            }
        }
        return FoxResult.Err("BLZ: could not find an in-place safe split")
    }

    private sealed interface Attempt {
        class Done(val bytes: ByteArray) : Attempt
        object TooBig : Attempt
        class Unsafe(val safeTailBytes: Int) : Attempt
    }

    private fun encode(data: ByteArray, passthrough: Int): Attempt {
        val bodyLength = data.size - passthrough
        if (bodyLength < MIN_MATCH) return Attempt.TooBig
        // Work on the body reversed, so the decoder (which runs backwards) sees it front to back.
        val r = ByteArray(bodyLength) { data[data.size - 1 - it] }

        val stream = ByteArrayBuilder(bodyLength / 2 + 16)
        val head = IntArray(1 shl 16) { -1 }
        val prev = IntArray(bodyLength) { -1 }
        fun hash(i: Int) = ((r[i].toInt() and 0xFF) shl 8 xor ((r[i + 1].toInt() and 0xFF) shl 4) xor (r[i + 2].toInt() and 0xFF)) and 0xFFFF
        fun insert(i: Int) {
            if (i + 2 < bodyLength) {
                val h = hash(i)
                prev[i] = head[h]
                head[h] = i
            }
        }

        // In-place rule: after consuming `read` input bytes and producing `written` output bytes, the decoder needs
        // written - read <= bodyLength - storedLength at every step (the input sits at the end of the output buffer).
        var flagPos = -1
        var flagBit = 0
        var read = 0
        var written = 0
        var peakSaving = 0
        var peakWritten = 0

        fun beginItem(isMatch: Boolean) {
            if (flagBit == 0) {
                flagPos = stream.size
                stream.add(0)
                read++
                flagBit = 8
            }
            flagBit--
            if (isMatch) stream.setOr(flagPos, 1 shl flagBit)
        }

        var i = 0
        while (i < bodyLength) {
            var bestLen = 0
            var bestDisp = 0
            if (i + 2 < bodyLength) {
                var candidate = head[hash(i)]
                var chain = 0
                val maxLen = minOf(MAX_MATCH, bodyLength - i)
                while (candidate >= 0 && chain < MAX_CHAIN) {
                    val disp = i - candidate
                    if (disp > MAX_DISP) break
                    if (disp >= MIN_DISP) {
                        var len = 0
                        while (len < maxLen && r[candidate + len] == r[i + len]) len++
                        if (len > bestLen) {
                            bestLen = len
                            bestDisp = disp
                            if (len == maxLen) break
                        }
                    }
                    candidate = prev[candidate]
                    chain++
                }
            }
            if (bestLen >= MIN_MATCH) {
                beginItem(true)
                stream.add(((bestLen - MIN_MATCH) shl 4) or ((bestDisp - MIN_DISP) ushr 8))
                stream.add((bestDisp - MIN_DISP) and 0xFF)
                read += 2
                for (k in 0 until bestLen) insert(i + k)
                i += bestLen
                written += bestLen
            } else {
                beginItem(false)
                stream.add(r[i].toInt() and 0xFF)
                read += 1
                insert(i)
                i++
                written += 1
            }
            if (written - read > peakSaving) {
                peakSaving = written - read
                peakWritten = written
            }
        }

        var headerLen = 8
        val storedLength = stream.size
        while ((passthrough + storedLength + headerLen) % 4 != 0) headerLen++
        val totalSaved = bodyLength - storedLength - headerLen
        if (totalSaved <= 0) return Attempt.TooBig
        if (peakSaving > bodyLength - storedLength) {
            // Early output saved more than the whole file does, so the tail of the stream (the head of the file)
            // would be overwritten before it is read. Keep that head raw and try again.
            return Attempt.Unsafe(peakWritten)
        }

        val out = ByteArray(passthrough + storedLength + headerLen)
        data.copyInto(out, 0, 0, passthrough)
        val body = stream.toByteArray()
        for (k in body.indices) out[passthrough + k] = body[body.size - 1 - k]
        for (k in passthrough + storedLength until out.size - 8) out[k] = 0xFF.toByte()
        val compressedLen = storedLength + headerLen
        out.putU32(out.size - 8, (headerLen.toLong() shl 24) or compressedLen.toLong())
        out.putU32(out.size - 4, totalSaved.toLong())
        return Attempt.Done(out)
    }

    private class ByteArrayBuilder(capacity: Int) {
        private var buffer = ByteArray(maxOf(capacity, 16))
        var size = 0
            private set

        fun add(value: Int) {
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = value.toByte()
        }

        fun setOr(index: Int, bits: Int) {
            buffer[index] = (buffer[index].toInt() or bits).toByte()
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }
}
