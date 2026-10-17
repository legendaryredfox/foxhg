package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.save.*
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/** A save written by [NewSave], not by the game, at Cherrygrove's Poke Mart: the vanilla ROM continues there and the clerk's list is the badge mart's. */
class NewSaveInGameEmulatorTest {
    @Test
    fun theGameContinuesAFromScratchSave() {
        if (!available() || !EmulatorSaves.available("cherrygrove")) return
        val work = Files.createTempDirectory("foxhg-new-save")
        try {
            val footer = (SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("cherrygrove"))) as FoxResult.Ok).value.dsvFooter!!
            val made = (NewSave.create("FOX", 0, Location(68, -1, 3, 6, 2)) as FoxResult.Ok).value
            val saves = work.resolve("saves")
            Files.createDirectories(saves)
            Files.write(saves.resolve(romPath.fileName.toString().substringBeforeLast('.') + ".dsv"), made.bytes + footer)
            val shown = MartInGame.vanillaMarts().badge.let { all -> all.filter { it.tier == all.minOf { e -> e.tier } } }.take(2).map { it.item }
            check(shown.size == 2)
            EmulatorDriver(romPath, saves).use { emu ->
                emu.run(EmulatorSaves.OPEN_BUY_LIST_SCRIPT)
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("new-save.png"))
                assertTrue(emu.findU16s(shown).isNotEmpty(), "the clerk's list is the badge mart's first entries $shown")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
