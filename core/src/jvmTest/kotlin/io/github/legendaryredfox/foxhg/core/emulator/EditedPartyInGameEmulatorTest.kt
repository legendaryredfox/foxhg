package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.save.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A party written by foxhg (Pokemon decrypted, edited and encrypted again) is loaded by the vanilla ROM: the party screen shows the new nickname,
 * level and HP (the picture is kept in build/emulator-shots), and the party in RAM is the bytes foxhg wrote. The flag FLAG_GOT_STARTER is set in the
 * save so the start menu has the POKeMON entry.
 */
class EditedPartyInGameEmulatorTest {
    @Test
    fun theGameLoadsTheEditedParty() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-edited-party")
        try {
            val original = (SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("party"))) as FoxResult.Ok).value
            val party = (PartyData.read(original) as FoxResult.Ok).value
            val pikachu = party.members[1].withLevel(50).withExperience(125_000).withHp(120, 120)
            val ember = (party.members[0].withNickname("EMBER") as FoxResult.Ok).value
            val withParty = (original.withParty(party.with(0, ember).with(1, pikachu)) as FoxResult.Ok).value
            val general = withParty.current(BlockKind.GENERAL)!!
            val flags = SaveChunks.byId(SaveChunks.FLAGS)
            val written = withParty.withChunk(general, flags, SaveVarsFlags.with(withParty.chunk(general, flags), FLAG_GOT_STARTER, true))
            val saves = work.resolve("saves")
            Files.createDirectories(saves)
            Files.write(saves.resolve(romPath.fileName.toString().substringBeforeLast('.') + ".dsv"), written.toFileBytes())

            EmulatorDriver(romPath, saves).use { emu ->
                // Continue, close the saved-game message, open the start menu and choose POKeMON
                emu.run("s1500,START,s200,A*3,s400,A,s500,B,s60,X,s60,A,s120")
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("edited-party.png"))
                val found = emu.findU16s(listOf((pikachu.personality and 0xFFFF).toInt(), (pikachu.personality shr 16).toInt()))
                val inRam = found.mapNotNull { at -> (Pokemon.parse(emu.read(at, Pokemon.SIZE)) as? FoxResult.Ok)?.value }.firstOrNull { it.species == 25 }
                    ?: error("the edited Pikachu is not in RAM")
                assertEquals(50, inRam.level)
                assertEquals(125_000L, inRam.experience)
                assertEquals(120, inRam.maxHp)
                assertTrue(inRam.toBytes().contentEquals(pikachu.toBytes()), "the game keeps the Pokemon as foxhg wrote it")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val FLAG_GOT_STARTER = 0x6A
    }
}
