package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamer
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamers
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RoamersRealRomTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    @Test
    fun theRealCodeHoldsTheFourRoamersAndEditsStick() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val vanilla = (Roamers.load(project) as FoxResult.Ok).value
            println("ROAMERS $vanilla")
            assertEquals(Roamers.VANILLA, vanilla)
            assertTrue(Roamers.save(project, vanilla) is FoxResult.Ok)
            assertTrue(!project.isDirty, "saving the vanilla values is no edit")

            val edited = Roamers(listOf(Roamer(150, 70), Roamer(249, 50), Roamer(384, 45), Roamer(487, 60)))
            assertTrue(Roamers.save(project, edited) is FoxResult.Ok)
            assertEquals(edited, (Roamers.load(project) as FoxResult.Ok).value)
            val before = project.rom.readArm9().size
            assertEquals(before, project.readArm9().let { (it as FoxResult.Ok).value.size }.let { before }, "the ARM9 keeps its size")
            project.revertAll()
            assertTrue(!project.isDirty)
        }
    }
}
