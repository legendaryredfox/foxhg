package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.battlerule.BattleRules
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class BattleRulesRealRomTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    @Test
    fun everyRuleHoldsTheGuidesVanillaValueAndEditsStick() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            for (rule in BattleRules.all) assertEquals(rule.vanilla, (BattleRules.read(project, rule) as? FoxResult.Ok)?.value ?: -1, "${rule.id} in the ROM")
            assertEquals(BattleRules.all.size, BattleRules.all.map { it.id }.toSet().size, "ids are unique")
            assertTrue(!project.isDirty)

            val burn = BattleRules.byId("burn-damage")!!
            val slow = BattleRules.byId("slow-start")!!
            val reckless = BattleRules.byId("reckless")!!
            assertTrue(BattleRules.write(project, burn, 16) is FoxResult.Ok)
            assertTrue(BattleRules.write(project, slow, 3) is FoxResult.Ok)
            assertTrue(BattleRules.write(project, reckless, 15) is FoxResult.Ok)
            assertEquals(listOf(16, 3, 15), listOf(burn, slow, reckless).map { (BattleRules.read(project, it) as FoxResult.Ok).value })
            // the other rules of the same files and overlay are untouched
            for (rule in BattleRules.all.filter { it !in listOf(burn, slow, reckless) }) assertEquals(rule.vanilla, (BattleRules.read(project, rule) as FoxResult.Ok).value, "${rule.id} untouched")
            assertTrue(project.isDirty)
            // a rule with a place in two overlays (the Trainer AI keeps a copy of the Magnitude table): both change
            val magnitude = BattleRules.byId("magnitude-10")!!
            assertEquals(2, magnitude.sites.map { (it as io.github.legendaryredfox.foxhg.core.data.battlerule.BattleSite.Overlay).id }.toSet().size)
            assertTrue(BattleRules.write(project, magnitude, 200) is FoxResult.Ok)
            assertEquals(200, (BattleRules.read(project, magnitude) as FoxResult.Ok).value)
            assertTrue(BattleRules.write(project, magnitude, magnitude.vanilla) is FoxResult.Ok)
            val paralysis = BattleRules.byId("paralysis-shift")!!
            val bytes = { listOf(0x185CA, 0x18776).map { (project.readOverlay(12) as FoxResult.Ok).value[it].toInt() and 0xFF } }
            assertEquals(listOf(0xB6, 0xA4), bytes(), "the game's two shift bytes")
            for ((shift, expected) in listOf(1 to listOf(0x76, 0x64), 3 to listOf(0xF6, 0xE4))) {
                assertTrue(BattleRules.write(project, paralysis, shift) is FoxResult.Ok)
                assertEquals(expected, bytes(), "paralysis shift $shift")
                assertEquals(shift, (BattleRules.read(project, paralysis) as FoxResult.Ok).value)
            }
            assertTrue(BattleRules.write(project, paralysis, 0) is FoxResult.Err, "a shift of 0 would mean 32 in Thumb")
            assertTrue(BattleRules.write(project, paralysis, 4) is FoxResult.Err)
            assertTrue(BattleRules.write(project, paralysis, paralysis.vanilla) is FoxResult.Ok)
            assertEquals(listOf(0xB6, 0xA4), bytes())
            assertTrue(BattleRules.write(project, burn, 0) is FoxResult.Err, "a divisor of 0 would divide by zero")
            assertTrue(BattleRules.write(project, BattleRules.byId("grip-claw")!!, 8) is FoxResult.Err, "the game does not take a Grip Claw duration above 7")
            val flailMultiplier = BattleRules.byId("flail-multiplier")!!
            val flailLimit6 = BattleRules.byId("flail-limit-6")!!
            assertTrue(BattleRules.write(project, flailMultiplier, 65) is FoxResult.Err, "a multiplier above limit 6 would read past the table")
            assertTrue(BattleRules.write(project, flailLimit6, 63) is FoxResult.Err, "limit 6 below the multiplier too")
            assertTrue(BattleRules.write(project, flailMultiplier, 48) is FoxResult.Ok)
            assertTrue(BattleRules.write(project, flailLimit6, 48) is FoxResult.Ok)
            assertTrue(BattleRules.write(project, flailLimit6, 100) is FoxResult.Ok)
            assertTrue(BattleRules.write(project, flailMultiplier, 100) is FoxResult.Ok)
            for (rule in listOf(flailMultiplier, flailLimit6)) assertTrue(BattleRules.write(project, rule, rule.vanilla) is FoxResult.Ok)
            for (rule in listOf(burn, slow, reckless)) assertTrue(BattleRules.write(project, rule, rule.vanilla) is FoxResult.Ok)
            assertTrue(!project.isDirty, "vanilla again leaves the ROM as it was")
        }
    }
}
