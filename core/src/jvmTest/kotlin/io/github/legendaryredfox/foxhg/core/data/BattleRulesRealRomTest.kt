package io.github.legendaryredfox.foxhg.core.data

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
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

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
            assertTrue(BattleRules.write(project, burn, 0) is FoxResult.Err, "a divisor of 0 would divide by zero")
            assertTrue(BattleRules.write(project, BattleRules.byId("grip-claw")!!, 8) is FoxResult.Err, "the game does not take a Grip Claw duration above 7")
            for (rule in listOf(burn, slow, reckless)) assertTrue(BattleRules.write(project, rule, rule.vanilla) is FoxResult.Ok)
            assertTrue(!project.isDirty, "vanilla again leaves the ROM as it was")
        }
    }
}
