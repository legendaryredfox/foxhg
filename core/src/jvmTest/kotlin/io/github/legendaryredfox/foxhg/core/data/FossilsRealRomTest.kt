package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.fossil.Fossil
import io.github.legendaryredfox.foxhg.core.data.fossil.Fossils
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class FossilsRealRomTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    @Test
    fun theRealOverlayHoldsTheSevenFossilsAndEditsStick() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val vanilla = (Fossils.load(project) as FoxResult.Ok).value
            assertEquals(Fossils.VANILLA, vanilla)
            assertTrue(Fossils.save(project, vanilla) is FoxResult.Ok)
            assertTrue(!project.isDirty, "saving the vanilla values is no edit")

            val edited = Fossils(vanilla.fossils.mapIndexed { i, f -> if (i == 3) Fossil(f.item, 150) else f })
            assertTrue(Fossils.save(project, edited) is FoxResult.Ok)
            assertEquals(edited, (Fossils.load(project) as FoxResult.Ok).value)
            assertTrue(project.isDirty)
            project.revertAll()
            assertTrue(!project.isDirty)
        }
    }
}
