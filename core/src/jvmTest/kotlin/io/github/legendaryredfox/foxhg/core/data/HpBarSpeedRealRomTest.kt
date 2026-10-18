package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.hpbar.HpBarSpeed
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class HpBarSpeedRealRomTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun theHpBarSpeedIsTheGamesAndEditsStick() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            assertEquals(HpBarSpeed.VANILLA, (HpBarSpeed.load(project) as FoxResult.Ok).value)
            assertEquals(1.0, HpBarSpeed.VANILLA.multiplier)
            // the guide's byte sequences: 80 01 = x0.25, C0 01 = x0.5, 40 02 = x2, 80 02 = x4, C0 02 = x8
            assertEquals(listOf(0x0180, 0x01C0, 0x0200, 0x0240, 0x0280, 0x02C0), (6..11).map { HpBarSpeed(it).instruction })
            assertEquals(listOf(0.25, 0.5, 1.0, 2.0, 4.0, 8.0), (6..11).map { HpBarSpeed(it).multiplier })
            assertTrue(HpBarSpeed.save(project, HpBarSpeed(10)) is FoxResult.Ok)
            assertEquals(HpBarSpeed(10), (HpBarSpeed.load(project) as FoxResult.Ok).value)
            assertTrue(project.isDirty)
            assertTrue(HpBarSpeed.save(project, HpBarSpeed.VANILLA) is FoxResult.Ok)
            assertTrue(!project.isDirty, "vanilla again leaves the overlay as the ROM has it")
        }
    }
}
