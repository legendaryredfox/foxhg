package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.save.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A PC written by foxhg (a Pokemon moved from the party to box 6, slot 8, and box 6 renamed) is loaded by the vanilla ROM: after Continue the game keeps
 * the box in RAM as the PC chunk foxhg wrote, so the game accepted the storage block (a damaged block would have made it fall back to the other slot or
 * to a new game).
 */
class EditedPcInGameEmulatorTest {
    @Test
    fun theGameLoadsTheEditedPc() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-edited-pc")
        try {
            val original = (SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("party"))) as FoxResult.Ok).value
            val party = (PartyData.read(original) as FoxResult.Ok).value
            val pc = (PcStorageData.read(original) as FoxResult.Ok).value
            val pikachu = party.members[1]
            val edited = (pc.placing(5, 7, pikachu).withBoxName(5, "FOXBOX") as FoxResult.Ok).value
            val written = (original.withParty(party.without(1)) as FoxResult.Ok).value.let { (it.withPcStorage(edited) as FoxResult.Ok).value }
            val saves = work.resolve("saves")
            Files.createDirectories(saves)
            Files.write(saves.resolve(romPath.fileName.toString().substringBeforeLast('.') + ".dsv"), written.toFileBytes())

            EmulatorDriver(romPath, saves).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s500,B,s60")
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("edited-pc.png"))
                val found = emu.findU16s(listOf((pikachu.personality and 0xFFFF).toInt(), (pikachu.personality shr 16).toInt()))
                val at = found.firstOrNull { emu.read(it, Pokemon.BOX_SIZE).contentEquals(pikachu.toBoxBytes()) } ?: error("the Pokémon in the box is not in RAM as foxhg wrote it")
                val boxStart = at - 5L * PcStorageData.BOX_STRIDE - 7L * Pokemon.BOX_SIZE
                val inRam = (PcStorageData.parse(emu.read(boxStart, PcStorageData.SIZE)) as? FoxResult.Ok)?.value ?: error("the PC in RAM does not parse")
                assertEquals("FOXBOX", inRam.boxName(5))
                assertEquals(25, inRam.boxes[5][7].species)
                assertEquals(edited, inRam, "the game keeps the whole PC as foxhg wrote it")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
