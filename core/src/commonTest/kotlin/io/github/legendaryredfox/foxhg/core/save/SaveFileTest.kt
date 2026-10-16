package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveFileTest {
    @Test
    fun crc16IsTheCcittFalseOne() {
        assertEquals(0x29B1, SaveFormat.crc16("123456789".encodeToByteArray()), "the standard check value of CRC-16/CCITT-FALSE")
        assertEquals(0xFFFF, SaveFormat.crc16(ByteArray(0)))
    }

    /** A save with one sound slot: [slot] holds both blocks with footers of count [count]. */
    private fun save(slot: Int, count: Long, other: Pair<Int, Long>? = null): ByteArray {
        val file = ByteArray(SaveFormat.FILE_SIZE) { 0xFF.toByte() }
        for ((s, c) in listOfNotNull(slot to count, other)) {
            for (kind in BlockKind.entries) {
                val offset = SaveFormat.SLOT_OFFSETS[s] + kind.offset
                for (i in 0 until kind.size - BlockFooter.SIZE) file[offset + i] = (i * 7 + kind.index + s).toByte()
                val footer = offset + kind.size - BlockFooter.SIZE
                file.putU32(footer, c); file.putU32(footer + 4, kind.size.toLong()); file.putU32(footer + 8, BlockFooter.MAGIC)
                file.putU16(footer + 12, kind.index); file.putU16(footer + 14, SaveFormat.crc16(file, offset, footer))
            }
        }
        return file
    }

    @Test
    fun theSoundSlotIsTheCurrentOne() {
        val parsed = (SaveFile.parse(save(1, 1)) as FoxResult.Ok).value
        assertEquals(listOf(false, false, true, true), parsed.blocks.map { it.valid })
        assertEquals(1, parsed.current(BlockKind.GENERAL)?.slot)
        assertEquals(1, parsed.current(BlockKind.STORAGE)?.slot)
        assertEquals(1L, parsed.current(BlockKind.GENERAL)?.footer?.count)
    }

    @Test
    fun theNewerOfTwoSoundSlotsWinsAndTheCounterWraps() {
        assertEquals(0, (SaveFile.parse(save(0, 5, 1 to 4)) as FoxResult.Ok).value.current(BlockKind.GENERAL)?.slot)
        assertEquals(1, (SaveFile.parse(save(0, 5, 1 to 6)) as FoxResult.Ok).value.current(BlockKind.GENERAL)?.slot)
        assertEquals(1, (SaveFile.parse(save(0, 0xFFFFFFFFL, 1 to 0)) as FoxResult.Ok).value.current(BlockKind.GENERAL)?.slot, "0 comes after 0xFFFFFFFF")
    }

    @Test
    fun aDamagedBlockIsNotCurrent() {
        val bytes = save(1, 1, 0 to 2)
        bytes[SaveFormat.SLOT_OFFSETS[0] + 100] = 0x11 // slot 0's general data changes, its CRC no longer fits
        val parsed = (SaveFile.parse(bytes) as FoxResult.Ok).value
        assertFalse(parsed.block(0, BlockKind.GENERAL).valid)
        assertEquals(1, parsed.current(BlockKind.GENERAL)?.slot, "the older but whole slot is used")
        assertEquals(0, parsed.current(BlockKind.STORAGE)?.slot, "the storage of slot 0 is still whole and newer")
        assertNull((SaveFile.parse(ByteArray(SaveFormat.FILE_SIZE) { 0xFF.toByte() }) as FoxResult.Ok).value.current(BlockKind.GENERAL))
    }

    @Test
    fun aChangedBlockGetsItsChecksumBackAndNothingElseMoves() {
        val parsed = (SaveFile.parse(save(1, 1)) as FoxResult.Ok).value
        val block = parsed.block(1, BlockKind.GENERAL)
        val data = parsed.data(block)
        data[10] = (data[10] + 1).toByte()
        val changed = parsed.withData(block, data)
        assertTrue(changed.block(1, BlockKind.GENERAL).valid)
        assertContentEquals(data, changed.data(changed.block(1, BlockKind.GENERAL)))
        assertEquals(parsed.block(1, BlockKind.GENERAL).footer.count, changed.block(1, BlockKind.GENERAL).footer.count)
        assertContentEquals(parsed.bytes.copyOfRange(0, SaveFormat.SLOT_OFFSETS[1]), changed.bytes.copyOfRange(0, SaveFormat.SLOT_OFFSETS[1]), "slot 0 is untouched")
        assertContentEquals(parsed.bytes.copyOfRange(SaveFormat.SLOT_OFFSETS[1] + SaveFormat.STORAGE_OFFSET, SaveFormat.FILE_SIZE), changed.bytes.copyOfRange(SaveFormat.SLOT_OFFSETS[1] + SaveFormat.STORAGE_OFFSET, SaveFormat.FILE_SIZE), "so is the storage")
        assertEquals(parsed.toFileBytes().size, changed.toFileBytes().size)
    }

    @Test
    fun aDsvFooterIsKeptAndOtherSizesAreRefused() {
        val raw = save(1, 1)
        val footer = ByteArray(SaveFormat.DSV_FOOTER_SIZE).also { SaveFormat.DSV_END.encodeToByteArray().copyInto(it, it.size - SaveFormat.DSV_END.length) }
        val dsv = (SaveFile.parse(raw + footer) as FoxResult.Ok).value
        assertNotNull(dsv.dsvFooter)
        assertContentEquals(raw + footer, dsv.toFileBytes())
        assertContentEquals(raw, (SaveFile.parse(raw) as FoxResult.Ok).value.toFileBytes())
        assertIs<FoxResult.Err>(SaveFile.parse(raw + ByteArray(SaveFormat.DSV_FOOTER_SIZE)), "a footer without the DeSmuME text")
        assertIs<FoxResult.Err>(SaveFile.parse(ByteArray(1234)))
    }

    @Test
    fun theFlagBitmapFollowsTheVarsAndFlag966IsOneBit() {
        val chunk = ByteArray(0x44C)
        assertFalse(SaveVarsFlags.isSet(chunk, 0x966))
        val set = SaveVarsFlags.with(chunk, 0x966, true)
        assertTrue(SaveVarsFlags.isSet(set, 0x966))
        assertEquals(0x40, set[0x2E0 + 0x966 / 8].toInt() and 0xFF, "flag 0x966 is bit 6 of byte 0x12C of the bitmap, 0x40C in the chunk")
        assertEquals(0x40C, 0x2E0 + 0x966 / 8)
        assertEquals(1, set.count { it != 0.toByte() })
        assertContentEquals(chunk, SaveVarsFlags.with(set, 0x966, false))
        assertEquals(0x1234, SaveVarsFlags.variable(ByteArray(0x44C).also { it[6] = 0x34; it[7] = 0x12 }, 3))
        kotlin.test.assertFailsWith<IllegalArgumentException> { SaveVarsFlags.isSet(chunk, 0) }
        kotlin.test.assertFailsWith<IllegalArgumentException> { SaveVarsFlags.isSet(chunk, 2912) }
    }
}
