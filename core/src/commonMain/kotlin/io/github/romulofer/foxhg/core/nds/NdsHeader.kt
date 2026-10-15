package io.github.romulofer.foxhg.core.nds

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.ascii
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32
import io.github.romulofer.foxhg.core.u8

/** Location and size of a block inside the ROM image. */
data class RomRegion(val offset: Long, val size: Long)

/**
 * The first 0x200 bytes of a `.nds` file. Field offsets follow GBATEK ("DS Cartridge Header"); the code that
 * reads the file system tables from these regions lives in `NdsRom` (plan.md M1).
 */
data class NdsHeader(
    val gameTitle: String,
    val gameCode: String,
    val makerCode: String,
    val romVersion: Int,
    val arm9: RomRegion,
    val arm9EntryAddress: Long,
    val arm9RamAddress: Long,
    val arm7: RomRegion,
    val arm7EntryAddress: Long,
    val arm7RamAddress: Long,
    val fileNameTable: RomRegion,
    val fileAllocationTable: RomRegion,
    val arm9Overlays: RomRegion,
    val arm7Overlays: RomRegion,
    val bannerOffset: Long,
    val usedRomSize: Long,
    val headerSize: Long,
) {
    val game: Game get() = Game.fromGameCode(gameCode)

    companion object {
        const val SIZE = 0x200

        fun parse(bytes: ByteArray): FoxResult<NdsHeader> {
            if (bytes.size < SIZE) {
                return FoxResult.Err("Not a DS ROM: header needs $SIZE bytes, got ${bytes.size}")
            }
            val gameCode = bytes.ascii(0x0C, 4)
            if (gameCode.any { it !in '0'..'9' && it !in 'A'..'Z' }) {
                return FoxResult.Err("Not a DS ROM: invalid game code at 0x0C")
            }
            val unitCode = bytes.u8(0x12)
            if (unitCode !in 0..3) {
                return FoxResult.Err("Not a DS ROM: unexpected unit code $unitCode at 0x12")
            }
            return FoxResult.Ok(
                NdsHeader(
                    gameTitle = bytes.ascii(0x00, 12).trimEnd('\u0000', ' '),
                    gameCode = gameCode,
                    makerCode = bytes.ascii(0x10, 2),
                    romVersion = bytes.u8(0x1E),
                    arm9 = RomRegion(bytes.u32(0x20), bytes.u32(0x2C)),
                    arm9EntryAddress = bytes.u32(0x24),
                    arm9RamAddress = bytes.u32(0x28),
                    arm7 = RomRegion(bytes.u32(0x30), bytes.u32(0x3C)),
                    arm7EntryAddress = bytes.u32(0x34),
                    arm7RamAddress = bytes.u32(0x38),
                    fileNameTable = RomRegion(bytes.u32(0x40), bytes.u32(0x44)),
                    fileAllocationTable = RomRegion(bytes.u32(0x48), bytes.u32(0x4C)),
                    arm9Overlays = RomRegion(bytes.u32(0x50), bytes.u32(0x54)),
                    arm7Overlays = RomRegion(bytes.u32(0x58), bytes.u32(0x5C)),
                    bannerOffset = bytes.u32(0x68),
                    usedRomSize = bytes.u32(0x80),
                    headerSize = bytes.u32(0x84),
                ),
            )
        }
    }
}
