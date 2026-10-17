package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.fly.FlyEntry
import io.github.legendaryredfox.foxhg.core.data.headbutt.HeadbuttFile
import io.github.legendaryredfox.foxhg.core.data.item.ItemData
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** Bytes the game does not use are kept on parse, so a file edited by another tool writes back as it was. */
class KeptPaddingTest {
    private fun <T> FoxResult<T>.ok(): T = (this as FoxResult.Ok).value

    @Test
    fun anItemKeepsItsByte13() {
        val bytes = ByteArray(ItemData.SIZE).also { it[13] = 0x5A; it[20] = 3 }
        assertContentEquals(bytes, ItemData.parse(bytes).ok().toBytes())
    }

    @Test
    fun aFlyEntryKeepsTheUnusedFlagBits() {
        val bytes = ByteArray(FlyEntry.SIZE).also { it[1] = 0xFF.toByte() }
        val out = ByteArray(FlyEntry.SIZE)
        FlyEntry.read(bytes, 0).write(out, 0)
        assertContentEquals(bytes, out)
    }

    @Test
    fun aHeadbuttFileKeepsHeaderBytes1And3() {
        val stub = byteArrayOf(0, 7, 0, 9)
        assertContentEquals(stub, HeadbuttFile.parse(stub).ok().toBytes())
        val full = ByteArray(4 + 4 * 18 + 24).also { it[0] = 1; it[1] = 4; it[3] = 8 }
        assertContentEquals(full, HeadbuttFile.parse(full).ok().toBytes())
    }
}
