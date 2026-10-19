package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import io.github.legendaryredfox.foxhg.core.script.ScriptWeather
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The script weather snippet in the game: the clerk of Cherrygrove's Poke Mart runs `ScriptWeather.snippet(7)` (sandstorm), then `ScrCmd_684` (it stores the current weather) into a
 * variable and `GiveItem 3 <that variable>`, so the number of Great Balls the player gets is the weather the game now has. RAM holds more copies of the pair
 * (item 3, quantity 7) afterwards than before; without the snippet the game would give 12 (the vanilla value of Flash). The ROM is not patched.
 */
class ScriptWeatherInGameEmulatorTest {
    @Test
    fun theSnippetChangesTheWeatherOfTheGame() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-weather")
        try {
            val rom = MartInGame.build(work, "weather.nds") { project ->
                check(ScriptWeather.check(project) is FoxResult.Ok)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.addAll(first + 1, ScriptWeather.snippet(7).lines() + listOf("        ScrCmd_684 0x8004", "        GiveItem 3, 0x8004, 0x8005"))
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s200")
                val before = emu.findU16s(listOf(3, 7))
                emu.run("A*2,s300,A,s30")
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("script-weather.png"))
                emu.run("s200")
                val after = emu.findU16s(listOf(3, 7))
                println("SCRIPTWEATHER pair (3, 7) in RAM before: ${before.size}, after: ${after.size}")
                assertTrue(after.size > before.size, "the weather after the snippet is 7, so the player got 7 Great Balls")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
