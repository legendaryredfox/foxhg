package io.github.legendaryredfox.foxhg.core.narc

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NarcTest {
    private val sample = Narc(
        listOf(
            byteArrayOf(1, 2, 3, 4),
            byteArrayOf(),
            byteArrayOf(9, 8, 7), // needs padding to 4
            ByteArray(100) { it.toByte() },
        ),
    )

    @Test
    fun serializeThenParseKeepsEveryFile() {
        val bytes = sample.toBytes()
        val parsed = (Narc.parse(bytes) as FoxResult.Ok).value
        assertEquals(sample.files.size, parsed.files.size)
        sample.files.forEachIndexed { i, f -> assertContentEquals(f, parsed.files[i], "file $i") }
    }

    @Test
    fun parseThenSerializeIsByteIdentical() {
        val bytes = sample.toBytes()
        val again = (Narc.parse(bytes) as FoxResult.Ok).value.toBytes()
        assertContentEquals(bytes, again)
    }

    @Test
    fun keepsRawNameTable() {
        val named = Narc(listOf(byteArrayOf(5)), nameTable = byteArrayOf(8, 0, 0, 0, 0, 0, 1, 0, 1, 'a'.code.toByte(), 0, 0))
        val parsed = (Narc.parse(named.toBytes()) as FoxResult.Ok).value
        assertContentEquals(named.nameTable, parsed.nameTable)
    }

    @Test
    fun rejectsGarbage() {
        assertIs<FoxResult.Err>(Narc.parse(ByteArray(64)))
        val truncated = sample.toBytes().copyOf(40)
        assertIs<FoxResult.Err>(Narc.parse(truncated))
    }

    @Test
    fun aDamagedHeaderIsAnErrorNotAnException() {
        val good = sample.toBytes()
        val badHeaderSize = good.copyOf().also { it[12] = 0xFF.toByte(); it[13] = 0xFF.toByte() }
        assertIs<FoxResult.Err>(Narc.parse(badHeaderSize))
        val hugeEnd = good.copyOf().also { b ->
            // the end of the first file: BTAF starts at 16, entries after its 12 byte head
            b[16 + 16] = 0xFF.toByte(); b[16 + 17] = 0xFF.toByte(); b[16 + 18] = 0xFF.toByte(); b[16 + 19] = 0x7F
        }
        assertIs<FoxResult.Err>(Narc.parse(hugeEnd))
        for (cut in listOf(15, 20, 30, good.size - 3)) assertIs<FoxResult.Err>(Narc.parse(good.copyOf(cut)), "cut at $cut")
    }
}
