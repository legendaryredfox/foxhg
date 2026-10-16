package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.save.Location
import io.github.legendaryredfox.foxhg.core.save.SaveFile
import io.github.legendaryredfox.foxhg.core.save.movedTo
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The save made in Cherrygrove's Poke Mart, moved by foxhg to Viridian's (map 500, tile 3 4, facing west) and continued in the vanilla ROM: the
 * clerk the player now stands next to is Viridian's second one, so the buy list is special mart 26, not Cherrygrove's badge list. Cherrygrove's save
 * never saw Viridian: only the position, the dynamic warp and flag 0x966 changed. (A changed position alone does not move the player: the game restores
 * the player and the NPCs from the saved map objects, so he stood at the old tile next to the old clerk; with the objects cleared the screen stayed black.)
 */
class MovedSaveInGameEmulatorTest {
    @Test
    fun theGameContinuesWhereTheSaveWasMoved() {
        if (!available() || !EmulatorSaves.available("cherrygrove")) return
        val work = Files.createTempDirectory("foxhg-moved-save")
        try {
            val original = (SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("cherrygrove"))) as FoxResult.Ok).value
            val moved = (original.movedTo(Location(500, -1, 3, 4, 2)) as FoxResult.Ok).value
            val saves = work.resolve("saves")
            Files.createDirectories(saves)
            Files.write(saves.resolve(romPath.fileName.toString().substringBeforeLast('.') + ".dsv"), moved.toFileBytes())
            val special26 = MartInGame.vanillaMarts().specials[26]
            EmulatorDriver(romPath, saves).use { emu ->
                emu.run(EmulatorSaves.OPEN_BUY_LIST_SCRIPT)
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("moved-save-viridian.png"))
                assertTrue(emu.findU16s(special26).size >= 2, "the clerk's list is special mart 26 $special26 (in the ARM9 and copied by the shop)")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
