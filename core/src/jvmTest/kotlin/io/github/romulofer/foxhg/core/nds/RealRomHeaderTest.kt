package io.github.romulofer.foxhg.core.nds

import io.github.romulofer.foxhg.core.FoxResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs against the clean HeartGold ROM at roms/heartgold.nds; prints a notice and passes when it is absent. */
class RealRomHeaderTest {
    private val rom: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun readsHeartGoldHeader() {
        if (!Files.exists(rom)) {
            println("SKIPPED: no ROM at ${rom.toAbsolutePath()} (set FOXHG_ROM or link roms/heartgold.nds)")
            return
        }
        val header = (readNdsHeader(rom) as FoxResult.Ok).value
        assertEquals(Game.HEARTGOLD_EN, header.game, "expected a clean English HeartGold ROM")
        assertTrue(header.fileAllocationTable.size > 0)
        assertTrue(header.arm9.offset >= header.headerSize)
    }
}
