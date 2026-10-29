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

/**
 * Trainer Pokémon of an even gender ratio take their trainer's gender (decomp `CreateNPCTrainerParty`): the Youngster's Rattata is a male for class gender 0 and a
 * female for 1. The picture of the battle goes to `class-gender-<gender>.png`, where the enemy's name bar shows the symbol. One core per test class: the DeSmuME core
 * crashes in `retro_load_game` when a second one loads in the same process.
 */
class NewTrainerClassMaleInGameEmulatorTest {
    private val world = NewTrainerClassWorld()

    private fun battleWithClassOfGender(gender: Int) {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-new-trainer-class-gender")
        try {
            val (rom, _) = world.buildRom(work, 20, gender)
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s200,A*2,s1500")
                Files.createDirectories(MartInGame.shotDir)
                emu.step(60)
                emu.savePicture(MartInGame.shotDir.resolve("class-gender-$gender.png"))
                // the symbol after the enemy's name: blue for a male, red for a female
                val shot = emu.screenshot() ?: error("no picture")
                var blue = 0
                var red = 0
                for (x in 60..76) for (y in 24..38) {
                    val c = shot.getRGB(x, y)
                    val r = (c shr 16) and 0xFF; val b = c and 0xFF
                    if (b > r + 60) blue++ else if (r > b + 60) red++
                }
                println("NEWCLASSGENDER class gender $gender: blue pixels $blue, red pixels $red")
                if (gender == 0) assertTrue(blue > 5 && red == 0, "a male trainer's even-ratio Pokémon is a male") else assertTrue(red > 5 && blue == 0, "a female trainer's even-ratio Pokémon is a female")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    @Test
    fun aMaleClassBattle() = battleWithClassOfGender(0)
}
