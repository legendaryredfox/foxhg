package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainer.NewTrainer
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
import io.github.legendaryredfox.foxhg.core.data.trainerclass.NewTrainerClass
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import io.github.legendaryredfox.foxhg.core.text.MessageText
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A trainer class added with [NewTrainerClass] in the game: the clerk of Cherrygrove's Poke Mart starts a battle against a new trainer (FOXHG) of the new class
 * FOXCLS (a copy of the Youngster's picture). The game reads the class name from the grown name bank: its codes are in RAM while the battle starts and were not before.
 */
class NewTrainerClassInGameEmulatorTest {
    private val world = NewTrainerClassWorld()

    @Test
    fun theGameBattlesTheTrainerThatWasAdded() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-new-trainer-class")
        try {
            val (rom, id) = world.buildRom(work, 20, 0)
            val name = (MessageText.encode("FOXCLS") as FoxResult.Ok).value.toList()
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s200")
                val before = emu.findU16s(name).size
                emu.run("A*2")
                // the name is in RAM while the introduction ("... wants to battle!") shows: look every half second until it appears
                var after = 0
                Files.createDirectories(MartInGame.shotDir)
                repeat(60) {
                    emu.step(30)
                    if (after == 0) emu.findU16s(name).size.let { n -> if (n > 0) { after = n; for (k in 0 until 6) { emu.step(90); emu.savePicture(MartInGame.shotDir.resolve("new-trainer-class-$k.png")) } } }
                }
                println("NEWTRAINERCLASS trainer $id, copies of the class name FOXCLS in RAM before: $before, while the battle starts: $after")
                assertTrue(after > before, "the battle shows the name of the added class")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    /** A ROM where the clerk of Cherrygrove's Poke Mart battles trainer 738, a Youngster of the new class FOXCLS with prize multiplier [multiplier]. */
}

/** What the in-game tests of a new trainer class share. One core per test class: a second core in the same process crashes the DeSmuME core in `retro_load_game`. */
class NewTrainerClassWorld {
    var lastLevel = 0

    fun buildRom(work: java.nio.file.Path, multiplier: Int, gender: Int): Pair<java.nio.file.Path, Int> {
            var id = -1
            val rom = MartInGame.build(work, "new-trainer-class.nds") { project ->
                // a Youngster: the Rival's name does not come from the name bank but from the save
                val classes = (TextArchive.load(project) as FoxResult.Ok).value.records[HgssEnglishLayout.textBanks.trainerClasses].entries.map { it.text() }
                val template = (TrainerTable.load(project) as FoxResult.Ok).value.trainers.indexOfFirst { classes.getOrNull(it.header.trainerClass) == "Youngster" }
                check(template > 0) { "no Youngster in the ROM" }
                check(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
                val youngster = classes.indexOf("Youngster")
                val classId = (NewTrainerClass.add(project, "FOXCLS", youngster, gender, multiplier) as FoxResult.Ok).value
                id = (NewTrainer.add(project, "FOXHG", template) as FoxResult.Ok).value
                val table = (TrainerTable.load(project) as FoxResult.Ok).value
                val made = table.trainers[id]
                // level 2 Pokémon: the player's Cyndaquil wins in a hit or two whatever the random numbers are, so the prize test does not depend on luck
                lastLevel = 2
                check(table.with(id, made.copy(header = made.header.copy(trainerClass = classId), party = made.party.map { it.copy(level = lastLevel) })).save(project) is FoxResult.Ok)
                check(id == (TrainerTable.load(project) as FoxResult.Ok).value.trainers.size - 1)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.add(first + 1, "        TrainerBattle $id, 0, 0, 0")
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
        return rom to id
    }

}
