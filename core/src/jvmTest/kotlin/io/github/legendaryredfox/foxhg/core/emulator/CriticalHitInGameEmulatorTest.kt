package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.battlerule.BattleRules
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The critical hit table in the game: the chance at stage 0 is set to 1 in 1, so every move of the lead crits. A wild Rattata battle runs one
 * turn (Tackle) and the enemy's HP bar is read: a normal Tackle of the level 5 Cyndaquil takes 11 to 14 of its 49 pixels (measured with the vanilla table), a critical hit 30 (the picture of the end of the turn is `critical-hit.png`).
 */
class CriticalHitInGameEmulatorTest {
    private fun EmulatorDriver.enemyBarPixels(): Int {
        val image = screenshot() ?: error("no frame yet")
        return (46..100).count { x ->
            val c = image.getRGB(x, 43)
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            (g > r + 30 && g > b + 30) || (r > 200 && g in 120..230 && b < 90) || (r > 190 && g < 90 && b < 90)
        }
    }

    /** True when the bottom screen shows the red FIGHT button of the command menu: the turn is over (during it the screen shows only the Poké Ball). */
    private fun EmulatorDriver.atCommandMenu(): Boolean {
        val c = (screenshot() ?: error("no frame yet")).getRGB(60, 252)
        return ((c shr 16) and 255) > 180 && ((c shr 8) and 255) < 110 && (c and 255) < 110
    }

    /** The bar once the turn is over: the end of the turn comes at a different frame from run to run, and the bars are hidden during move animations. */
    private fun EmulatorDriver.settled(read: EmulatorDriver.() -> Int): Int {
        repeat(120) {
            step(30)
            if (atCommandMenu()) { step(60); read().let { if (it > 0) return it } }
        }
        error("the turn did not end")
    }

    @Test
    fun withACriticalChanceOfOneEveryTackleIsACriticalHit() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-crit")
        try {
            val rom = MartInGame.build(work, "crit.nds") { project ->
                check(BattleRules.write(project, BattleRules.byId("crit-stage-0")!!, 1) is FoxResult.Ok)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.add(first + 1, "        WildBattle 19, 5, 0")
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run(EmulatorSaves.OPEN_BUY_LIST_SCRIPT + ",s1200")
                val before = emu.enemyBarPixels()
                emu.run("A,s120,A,s120,A,s400")
                val after = emu.settled { enemyBarPixels() }
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("critical-hit.png"))
                println("CRIT enemy HP bar pixels before the turn: $before, after: $after")
                assertTrue(before in 44..54, "the enemy's bar starts full: $before")
                assertTrue(after <= 30, "the Tackle took over a third of the bar (a critical hit left 19 and 25 pixels in two runs, the vanilla 1 in 16 left 35 and 38): $after")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
