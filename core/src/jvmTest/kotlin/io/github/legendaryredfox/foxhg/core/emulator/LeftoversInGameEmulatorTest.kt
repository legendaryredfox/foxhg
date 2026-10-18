package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.battlerule.BattleRules
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.save.*
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The "Leftovers healing" battle rule in the game: it is set to 2 (half of the maximum HP instead of 1/16), the lead Pokémon of the saved game is made a
 * level 50 Cyndaquil holding Leftovers with 40 of 110 HP, and a wild Rattata battle (from the clerk's script, as in the shiny rate tests) runs one turn
 * (Tackle). The lead's HP bar on the top screen is about a third full when the battle starts and well over half full after the turn: the Rattata's
 * Quick Attack can not do that, Leftovers healing 55 HP can (vanilla would heal 6).
 */
class LeftoversInGameEmulatorTest {
    private fun EmulatorDriver.playerBarPixels(): Int {
        val image = screenshot() ?: error("no frame yet")
        return (196..252).count { x ->
            val c = image.getRGB(x, 119)
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
    fun theEditedLeftoversRuleHealsMoreEachTurn() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-leftovers")
        try {
            val rom = MartInGame.build(work, "leftovers.nds") { project ->
                check(BattleRules.write(project, BattleRules.byId("leftovers")!!, 2) is FoxResult.Ok)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.add(first + 1, "        WildBattle 19, 5, 0")
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
            val original = (SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("party"))) as FoxResult.Ok).value
            val party = (PartyData.read(original) as FoxResult.Ok).value
            val lead = party.members[0].withLevel(50).withExperience(125_000).withHp(40, 110).withHeldItem(234)
            val saves = work.resolve("saves")
            Files.createDirectories(saves)
            Files.write(saves.resolve("leftovers.dsv"), (original.withParty(party.with(0, lead)) as FoxResult.Ok).value.toFileBytes())
            EmulatorDriver(rom, saves).use { emu ->
                emu.run(EmulatorSaves.OPEN_BUY_LIST_SCRIPT + ",s1200")
                val before = emu.playerBarPixels()
                emu.run("A,s120,A,s120,A,s400")
                val after = emu.settled { playerBarPixels() }
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("leftovers.png"))
                println("LEFTOVERS player HP bar pixels before the turn: $before, after: $after")
                assertTrue(before in 10..24, "the bar starts about a third full: $before")
                assertTrue(after >= 34, "Leftovers at 1/2 healed the lead past two thirds: $after")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
