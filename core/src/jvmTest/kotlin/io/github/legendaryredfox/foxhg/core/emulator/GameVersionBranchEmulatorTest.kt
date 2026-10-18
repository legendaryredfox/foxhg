package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.ShinyEmulator.monsInRam
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Which way HeartGold goes at the version test of the Ho-Oh and Lugia scripts (files 21 and 104): `GetGameVersion`, `CompareVarToValue result, 7`, then
 * `GoToIf 5, function_5` (5 is "not equal"). The clerk of Cherrygrove's Poké Mart runs the same three commands; the branch that is not taken gives a
 * Ditto of level 61 (HeartGold falls through, version 7), the one taken an Eevee of level 62. In the legendary scripts the fall-through sets the level
 * variable to 45 for Ho-Oh and 70 for Lugia, so those are the levels of HeartGold.
 */
class GameVersionBranchEmulatorTest {
    @Test
    fun heartGoldFallsThroughTheNotEqualSevenBranch() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-version")
        try {
            val rom = MartInGame.build(work, "version.nds") { project ->
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.addAll(first + 1, listOf(
                    "        GetGameVersion 32780", "        CompareVarToValue 32780, 7", "        GoToIf 5, function_90",
                    "        GiveMon 132, 61, 0, 0, 0, 32780", "        End",
                ))
                lines += listOf("", "function_90:", "        GiveMon 133, 62, 0, 0, 0, 32780", "        End")
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run(EmulatorSaves.OPEN_BUY_LIST_SCRIPT + ",s600")
                val ditto = emu.monsInRam(132).filter { it.level == 61 }
                val eevee = emu.monsInRam(133).filter { it.level == 62 }
                println("VERSION fall-through (Ditto 61): ${ditto.size}, taken branch (Eevee 62): ${eevee.size}")
                assertTrue(ditto.isNotEmpty() && eevee.isEmpty(), "HeartGold is version 7: it falls through")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
