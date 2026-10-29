package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mart.Marts
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.list
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.nds.FileRomSink
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A ROM with two marts moved into the synthetic overlay: the game maps the overlay at start-up and the pointer table names it. */
class MovedMartsEmulatorTest {
    @Test
    fun aMovedMartIsInTheSyntheticOverlayThePointerNamesWhenTheGameRuns() {
        if (!available()) return
        val dir = Files.createTempDirectory("foxhg-emulator")
        try {
            val built = dir.resolve("moved-marts.nds")
            val expected = FileRomSource(romPath).use { source ->
                val project = (RomProject.open(source) as FoxResult.Ok).value
                check(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
                var marts = (Marts.load(project) as FoxResult.Ok).value
                repeat(6) { marts = marts.withAdded(0, 17 + it) }
                marts = marts.withAdded(28, 18)
                check(marts.save(project) is FoxResult.Ok)
                FileRomSink(built).use { sink -> check(project.build(sink) is FoxResult.Ok) }
                marts.specials
            }
            EmulatorDriver(built, dir).use { emu ->
                emu.step(600)
                for (m in listOf(0, 28)) {
                    val pointer = emu.readU32(0x02000000L + HgssEnglishLayout.specialMartPointers + 4 * m)
                    assertTrue(pointer >= HgssEnglishLayout.syntheticOverlayAddress, "mart $m points into the synthetic overlay, got 0x${pointer.toString(16)}")
                    assertEquals(expected[m], emu.list(pointer), "mart $m as the game reads it from the synthetic overlay")
                }
                assertEquals(expected[1], emu.list(emu.readU32(0x02000000L + HgssEnglishLayout.specialMartPointers + 4)), "an untouched mart is where it was")
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
