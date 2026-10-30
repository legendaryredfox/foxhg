package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainer.NewTrainer
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import io.github.legendaryredfox.foxhg.core.text.MessageText
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A trainer added with [NewTrainer] in the game: it is the 739th trainer (number 738), named FOXHG, and the clerk of Cherrygrove's Poke Mart starts a
 * battle against it (`TrainerBattle 738`). The game reads its name from the grown name bank: the name's codes are in RAM while the battle starts
 * and were not before (picture `new-trainer.png`).
 */
class NewTrainerInGameEmulatorTest {
    @Test
    fun theGameBattlesTheTrainerThatWasAdded() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-new-trainer")
        try {
            var id = -1
            val rom = MartInGame.build(work, "new-trainer.nds") { project ->
                // a Youngster: the Rival's name does not come from the name bank but from the save
                val classes = (TextArchive.load(project) as FoxResult.Ok).value.records[HgssEnglishLayout.textBanks.trainerClasses].entries.map { it.text() }
                val template = (TrainerTable.load(project) as FoxResult.Ok).value.trainers.indexOfFirst { classes.getOrNull(it.header.trainerClass) == "Youngster" }
                check(template > 0) { "no Youngster in the ROM" }
                id = (NewTrainer.add(project, "FOXHG", template) as FoxResult.Ok).value
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
            val name = (MessageText.encode("FOXHG") as FoxResult.Ok).value.toList()
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
                    if (after == 0) emu.findU16s(name).size.let { n -> if (n > 0) { after = n; emu.step(480); emu.savePicture(MartInGame.shotDir.resolve("new-trainer.png")) } }
                }
                println("NEWTRAINER trainer $id, copies of the name FOXHG in RAM before: $before, while the battle starts: $after")
                assertTrue(after > before, "the battle against the added trainer shows its name")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
