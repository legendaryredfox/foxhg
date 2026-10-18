package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamer
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamers
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The roamers in the game: the four are changed to species 150, 151, 192 and 200 at levels 77 to 80, the clerk of Cherrygrove's Poké Mart runs
 * `CreateRoamer 0` to `3`, and main RAM holds each as the game stores it (decomp `Roamer`: species u16 at 12, hp u16, level u8, status u8, active u8 = 1).
 */
class RoamersInGameEmulatorTest {
    private fun EmulatorDriver.roamersInRam(): List<Pair<Int, Int>> {
        val ram = read(0x02000000L, 0x400000)
        val out = ArrayList<Pair<Int, Int>>()
        var at = 0
        while (at + 8 <= ram.size) {
            val species = (ram[at].toInt() and 0xFF) or ((ram[at + 1].toInt() and 0xFF) shl 8)
            val hp = (ram[at + 2].toInt() and 0xFF) or ((ram[at + 3].toInt() and 0xFF) shl 8)
            if (species in 1..493 && hp in 1..999 && ram[at + 5].toInt() == 0 && ram[at + 6].toInt() == 1 && (ram[at + 4].toInt() and 0xFF) in 1..100) out += species to (ram[at + 4].toInt() and 0xFF)
            at += 4
        }
        return out
    }

    @Test
    fun theEditedRoamersStartWithTheirNewSpeciesAndLevels() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-roamer")
        try {
            val wanted = listOf(Roamer(150, 77), Roamer(151, 78), Roamer(192, 79), Roamer(200, 80))
            val rom = MartInGame.build(work, "roamer.nds") { project ->
                check(Roamers.save(project, Roamers(wanted)) is FoxResult.Ok)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.addAll(first + 1, (0..3).map { "        CreateRoamer $it" })
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                val before = emu.run("s1500,START,s200,A*3,s400,A,s200").let { emu.roamersInRam() }
                emu.run("A*2,s300,A,s300")
                val after = emu.roamersInRam()
                println("ROAMERS in RAM after: " + wanted.map { r -> r.species to r.level }.filter { it in after })
                for (r in wanted) {
                    assertTrue(r.species to r.level !in before, "roamer ${r.species} at level ${r.level} is not in RAM before the script")
                    assertTrue(r.species to r.level in after, "roamer ${r.species} at level ${r.level} is in RAM")
                }
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
