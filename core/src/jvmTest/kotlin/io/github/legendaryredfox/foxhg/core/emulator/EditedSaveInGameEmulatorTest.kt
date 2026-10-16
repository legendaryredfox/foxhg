package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.save.PlayerData
import io.github.legendaryredfox.foxhg.core.save.SaveFile
import io.github.legendaryredfox.foxhg.core.save.withPlayer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A save written by foxhg (the player chunk of a save the game made, edited) is loaded by the vanilla ROM: the Continue screen shows the new name,
 * badges and play time (the picture is kept in build/emulator-shots), and once Continue is chosen the game has the values in RAM, found by the name.
 */
class EditedSaveInGameEmulatorTest {
    @Test
    fun theGameLoadsTheEditedPlayer() {
        if (!available() || !EmulatorSaves.available("cherrygrove")) return
        val work = Files.createTempDirectory("foxhg-edited-save")
        try {
            val original = (SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("cherrygrove"))) as FoxResult.Ok).value
            val player = (PlayerData.read(original) as FoxResult.Ok).value
            val edited = (player.copy(money = 123_456, johtoBadges = 0b101, kantoBadges = 0b1, hours = 12, minutes = 34, seconds = 5).withName("FOXHG") as FoxResult.Ok).value
            val written = (original.withPlayer(edited) as FoxResult.Ok).value
            val saves = work.resolve("saves")
            Files.createDirectories(saves)
            Files.write(saves.resolve(romPath.fileName.toString().substringBeforeLast('.') + ".dsv"), written.toFileBytes())

            EmulatorDriver(romPath, saves).use { emu ->
                emu.run("s1500,START,s200,A*3,s400")
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("edited-save-continue.png"))
                emu.run("A,s400")
                emu.savePicture(MartInGame.shotDir.resolve("edited-save-loaded.png"))
                val found = emu.findU16s(edited.nameCodes.takeWhile { it != PlayerData.END } + PlayerData.END)
                assertTrue(found.isNotEmpty(), "the name FOXHG is in RAM once the save is loaded")
                val profile = found.first()
                assertEquals(edited.trainerId, emu.readU32(profile + 0x10), "the trainer ID")
                assertEquals(123_456L, emu.readU32(profile + 0x14), "the money")
                assertEquals(0b101, emu.read(profile + 0x1A, 1)[0].toInt() and 0xFF, "the Johto badges")
                assertEquals(0b1, emu.read(profile + 0x1F, 1)[0].toInt() and 0xFF, "the Kanto badges")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
