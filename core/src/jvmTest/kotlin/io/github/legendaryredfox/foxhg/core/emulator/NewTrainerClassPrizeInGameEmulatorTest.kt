package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainer.NewTrainer
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
import io.github.legendaryredfox.foxhg.core.data.trainerclass.NewTrainerClass
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import io.github.legendaryredfox.foxhg.core.text.MessageText
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/** One core per test class: the DeSmuME core crashes in `retro_load_game` when a second one loads in the same process. */
class NewTrainerClassPrizeInGameEmulatorTest {
    private val world = NewTrainerClassWorld()

    /** Wins the battle against the new class and reads the money in RAM: the prize is the last level times 4 times the class multiplier (times 1 for a single battle). */
    @Test
    fun winningPaysThePrizeOfTheNewClass() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-new-trainer-class-prize")
        try {
            val (rom, id) = world.buildRom(work, 20, 0)
            val level = world.lastLevel
            val player = (io.github.legendaryredfox.foxhg.core.save.PlayerData.read((io.github.legendaryredfox.foxhg.core.save.SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("party"))) as FoxResult.Ok).value) as FoxResult.Ok).value
            val playerName = player.nameCodes.takeWhile { it != io.github.legendaryredfox.foxhg.core.save.PlayerData.END } + io.github.legendaryredfox.foxhg.core.save.PlayerData.END
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s200,A*2")
                val profile = emu.findU16s(playerName).first()
                val before = emu.readU32(profile + 0x14)
                var after = before
                repeat(150) {
                    if (after == before) {
                        emu.run("A,s60")
                        after = emu.readU32(profile + 0x14)
                    }
                }
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("new-class-prize.png"))
                println("NEWCLASSPRIZE money before $before, after $after, last level $level")
                assertTrue(after > before, "the player won the battle and was paid")
                kotlin.test.assertEquals(level * 4 * 20L, after - before, "the prize is the last level times 4 times the multiplier 20 of the new class")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
