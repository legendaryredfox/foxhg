package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.patch.BattleBugFixPatches
import io.github.legendaryredfox.foxhg.core.patch.BattleTweakPatches
import io.github.legendaryredfox.foxhg.core.patch.BytePatch
import io.github.legendaryredfox.foxhg.core.patch.FastHpBarPatch
import io.github.legendaryredfox.foxhg.core.patch.PatchStatus
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.patch.ReusableTmsPatch
import io.github.legendaryredfox.foxhg.core.patch.RomPatch
import io.github.legendaryredfox.foxhg.core.patch.TextureAnimationsPatch
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import io.github.legendaryredfox.foxhg.core.script.ScriptWeather
import io.github.legendaryredfox.foxhg.core.u8
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
        assertEquals(PatchStatus.Available, ReusableTmsPatch.status(project), "the guide's bytes are in the vanilla ARM9 and overlay 15")
        assertEquals(PatchStatus.Available, FastHpBarPatch.status(project), "the guide's bytes are in the vanilla overlay 12")
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
                val a = Narc.parse(project.read(HgssEnglishLayout.areaData)!!).let { (it as FoxResult.Ok).value.files }
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
        assertEquals(HgssEnglishLayout.syntheticOverlaySize, images(project).getValue(PatchTarget.SyntheticOverlay).size)
        while (project.canUndo) project.undo()
        val restored = images(project)
        for ((target, bytes) in vanilla) assertContentEquals(bytes, restored.getValue(target), "$target after undoing everything")
        assertEquals(PatchStatus.Available, TextureAnimationsPatch.status(project), "undo brought the animations back")
        project.revertAll()
    }

    @Test
    fun reusableTmsTurnOffAgainAndRestoreTheVanillaBytes() = withProject { project ->
        val vanilla = listOf(PatchTarget.Arm9, PatchTarget.Overlay(15)).associateWith { it.read(project).let { r -> (r as FoxResult.Ok).value } }
        assertTrue(ReusableTmsPatch.reversible)
        assertTrue(ReusableTmsPatch.revert(project) is FoxResult.Err, "off while it is off is refused")
        assertTrue(ReusableTmsPatch.apply(project) is FoxResult.Ok)
        assertEquals(PatchStatus.Applied, ReusableTmsPatch.status(project))
        assertTrue(ReusableTmsPatch.revert(project) is FoxResult.Ok)
        assertEquals(PatchStatus.Available, ReusableTmsPatch.status(project))
        for ((target, bytes) in vanilla) assertContentEquals(bytes, (target.read(project) as FoxResult.Ok).value, "$target after turning it off")
        assertTrue(DsprePatches.all.filter { it !== ReusableTmsPatch && it !== FastHpBarPatch && it !in BattleBugFixPatches.all && it !in BattleTweakPatches.all }.none { it.reversible }, "only the plain byte patches are switches")
        assertTrue(DsprePatches.trainerNames.revert(project) is FoxResult.Err)
        project.revertAll()
    }

    @Test
    fun theScriptWeatherImmediateIsWhereTheGuideSaysAndTheSnippetParses() = withProject { project ->
        assertTrue(ScriptWeather.check(project) is FoxResult.Ok, "the guide's byte pattern is in the vanilla ARM9")
        assertEquals(ScriptWeather.DEFAULT, (project.readArm9() as FoxResult.Ok).value.u8(ScriptWeather.OFFSET), "movs r1, #12")
        assertEquals(0x02000000 + ScriptWeather.OFFSET, ScriptWeather.ADDRESS)
        val script = "script_1:\n" + ScriptWeather.snippet(3) + "\n        End\n"
        val parsed = ScriptText.parse(script)
        assertTrue(parsed is FoxResult.Ok, "WriteByteToAddr and FlashEffect are commands of the database: $parsed")
        val text = ScriptText.toText((parsed as FoxResult.Ok).value)
        assertTrue("WriteByteToAddr ${ScriptWeather.ADDRESS}, 3" in text && "FlashEffect" in text && "WriteByteToAddr ${ScriptWeather.ADDRESS}, 12" in text, text)
        assertEquals((0..13).toList(), ScriptWeather.NAMES.keys.toList())
    }

    @Test
    fun fastHpBarTurnsOffAgainAndRestoresTheVanillaBytes() = withProject { project ->
        val vanilla = (PatchTarget.Overlay(12).read(project) as FoxResult.Ok).value
        assertTrue(FastHpBarPatch.reversible)
        assertTrue(FastHpBarPatch.apply(project) is FoxResult.Ok)
        assertEquals(PatchStatus.Applied, FastHpBarPatch.status(project))
        assertTrue(FastHpBarPatch.revert(project) is FoxResult.Ok)
        assertEquals(PatchStatus.Available, FastHpBarPatch.status(project))
        assertContentEquals(vanilla, (PatchTarget.Overlay(12).read(project) as FoxResult.Ok).value)
        project.revertAll()
    }

    @Test
    fun everyBattleBugFixAndTweakIsFoundInTheVanillaRomAndTurnsOffAgain() = withProject { project ->
        val vanilla = listOf(PatchTarget.Overlay(10), PatchTarget.Overlay(12)).associateWith { (it.read(project) as FoxResult.Ok).value }
        assertEquals(11, BattleBugFixPatches.all.size)
        assertEquals(3, BattleTweakPatches.all.size)
        for (patch in BattleBugFixPatches.all + BattleTweakPatches.all) {
            assertTrue(patch.reversible, patch.id)
            assertEquals(PatchStatus.Available, patch.status(project), "${patch.id}: the guide's bytes are in the vanilla ROM")
            assertTrue(patch.apply(project) is FoxResult.Ok, patch.id)
            assertEquals(PatchStatus.Applied, patch.status(project), patch.id)
            assertTrue(patch.revert(project) is FoxResult.Ok, patch.id)
            assertEquals(PatchStatus.Available, patch.status(project), patch.id)
            for ((target, bytes) in vanilla) assertContentEquals(bytes, (target.read(project) as FoxResult.Ok).value, "${patch.id}: $target after turning it off")
        }
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
