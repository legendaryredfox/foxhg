package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mart.Marts
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.list
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Boots the real ROM on the DeSmuME core and reads its RAM. Skips (with a notice) without the ROM or the core. */
class EmulatorSmokeTest {
    @Test
    fun theVanillaRomBootsAndItsMartTablesAreInRam() {
        if (!available()) return
        val dir = Files.createTempDirectory("foxhg-emulator")
        try {
            val marts = FileRomSource(romPath).use { source -> (Marts.load((RomProject.open(source) as FoxResult.Ok).value) as FoxResult.Ok).value }
            EmulatorDriver(romPath, dir).use { emu ->
                emu.step(600)
                assertNotNull(emu.screenshot(), "the core drew a frame")
                for (m in listOf(0, 3, 28)) {
                    val pointer = emu.readU32(0x02000000L + HgssLayout.SPECIAL_MART_POINTERS + 4 * m)
                    assertEquals(0x02000000L + Marts.VANILLA_ADDRESSES[m], pointer, "the pointer of mart $m")
                    assertEquals(marts.specials[m], emu.list(pointer), "the list of mart $m as the game has it in RAM")
                }
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
