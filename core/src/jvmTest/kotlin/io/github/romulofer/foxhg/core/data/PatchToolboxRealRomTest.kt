package io.github.romulofer.foxhg.core.data

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.narc.Narc
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.patch.BytePatch
import io.github.romulofer.foxhg.core.patch.PatchStatus
import io.github.romulofer.foxhg.core.patch.PatchTarget
import io.github.romulofer.foxhg.core.patch.RomPatch
import io.github.romulofer.foxhg.core.patch.TextureAnimationsPatch
import io.github.romulofer.foxhg.core.patch.dspre.DsprePatches
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatchToolboxRealRomTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun withProject(block: (RomProject) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block((RomProject.open(source) as FoxResult.Ok).value) }
    }

    private fun images(project: RomProject) = mapOf(
        PatchTarget.Arm9 to (project.readArm9() as FoxResult.Ok).value,
        PatchTarget.Overlay(1) to (project.readOverlay(1) as FoxResult.Ok).value,
        PatchTarget.SyntheticOverlay to (PatchTarget.SyntheticOverlay.read(project) as FoxResult.Ok).value,
    )

    @Test
    fun theVanillaRomOffersTheExpansionAndTheIndependentPatchesAndHoldsTheRestBack() = withProject { project ->
        assertEquals(PatchStatus.Available, DsprePatches.arm9Expansion.status(project))
        assertEquals(PatchStatus.Available, DsprePatches.matrixExpansion.status(project))
        assertEquals(PatchStatus.Available, DsprePatches.trainerNames.status(project))
        assertEquals(PatchStatus.Available, TextureAnimationsPatch.status(project))
        for (p in listOf(DsprePatches.dynamicCamera, DsprePatches.buildingRotation, DsprePatches.scriptCommandRepoint)) {
            assertEquals(PatchStatus.Unavailable("Apply \"Expand ARM9 memory\" first"), p.status(project), p.id)
        }
    }

    @Test
    fun everyPatchChangesOnlyItsBytesAndIsDetectedAfterwardsAndUndoes() = withProject { project ->
        var now = 0L
        project.clock = { now += 5000; now } // one undo step per write
        val vanilla = images(project)
        // ARM9 expansion first, the others need it
        for (patch in DsprePatches.all) {
            val before = images(project)
            assertEquals(PatchStatus.Available, patch.status(project), patch.id)
            assertTrue(patch.apply(project) is FoxResult.Ok, patch.id)
            assertEquals(PatchStatus.Applied, patch.status(project), patch.id)
            assertTrue(patch.apply(project) is FoxResult.Err, "${patch.id}: applying twice is refused")
            val after = images(project)
            if (patch !is BytePatch) {
                val a = Narc.parse(project.read(HgssLayout.AREA_DATA)!!).let { (it as FoxResult.Ok).value.files }
                assertTrue(a.all { it[4] == 0xFF.toByte() && it[5] == 0xFF.toByte() }, "every area data file has no dynamic texture")
                continue
            }
            val touched = (patch as BytePatch).editsFor(project).let { (it as FoxResult.Ok).value }.groupBy({ it.target }, { e -> e.offset until e.offset + e.original.size })
            for ((target, image) in before) {
                val ranges = touched[target].orEmpty()
                if (target == PatchTarget.SyntheticOverlay && patch === DsprePatches.arm9Expansion) continue // the member is replaced by a bigger one
                for (i in image.indices) if (ranges.none { i in it }) assertEquals(image[i], after.getValue(target)[i], "${patch.id}: $target byte $i")
            }
        }
        assertEquals(HgssLayout.SYNTHETIC_OVERLAY_SIZE, images(project).getValue(PatchTarget.SyntheticOverlay).size)
        while (project.canUndo) project.undo()
        val restored = images(project)
        for ((target, bytes) in vanilla) assertContentEquals(bytes, restored.getValue(target), "$target after undoing everything")
        assertEquals(PatchStatus.Available, TextureAnimationsPatch.status(project), "undo brought the animations back")
        project.revertAll()
    }

    @Test
    fun theCommandTableBlockHoldsTheVanillaTable() = withProject { project ->
        DsprePatches.arm9Expansion.apply(project)
        DsprePatches.scriptCommandRepoint.apply(project)
        val synth = images(project).getValue(PatchTarget.SyntheticOverlay)
        val arm9 = images(project).getValue(PatchTarget.Arm9)
        assertEquals("COUN", String(synth, 0x200, 4))
        assertEquals("TABL", String(synth, 0x208, 4))
        assertContentEquals(arm9.copyOfRange(0xFAD00, 0xFAD00 + 0x355 * 4), synth.copyOfRange(0x20C, 0x20C + 0x355 * 4))
        project.revertAll()
    }

    @Suppress("unused")
    private fun unused(p: RomPatch) = p
}
