package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.grounditem.GroundItems
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An item ball in the game: ball script 7000 (the Route 29 Potion) is edited to give 77 Ultra Balls (item 2, quantity 77), and the clerk of Cherrygrove's
 * Poké Mart is made to run it (`CallStd 7000`, as an overworld event with that script does). RAM holds more copies of the pair (item, quantity) afterwards
 * than before (the bag and its copies), and the game shows the message of the ball (picture `ground-item.png`).
 */
class GroundItemInGameEmulatorTest {
    @Test
    fun anEditedItemBallGivesItsNewItemAndQuantity() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-ball")
        try {
            val rom = MartInGame.build(work, "ball.nds") { project ->
                check(GroundItems.change(project, 7000, 2, 77) is FoxResult.Ok)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.add(first + 1, "        CallStd 7000")
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s200")
                val before = emu.findU16s(listOf(2, 77))
                emu.run("A*2,s300,A,s30")
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("ground-item.png"))
                emu.run("s200")
                val after = emu.findU16s(listOf(2, 77))
                println("GROUNDITEM pair (2, 77) in RAM before: ${before.size}, after: ${after.size}")
                assertTrue(after.size > before.size, "the bag holds the Ultra Balls the edited ball gave")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
