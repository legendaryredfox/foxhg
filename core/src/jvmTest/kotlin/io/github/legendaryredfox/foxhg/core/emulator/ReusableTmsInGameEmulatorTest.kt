package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.patch.ReusableTmsPatch
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.save.BagData
import io.github.legendaryredfox.foxhg.core.save.BagPocket
import io.github.legendaryredfox.foxhg.core.save.SaveFile
import io.github.legendaryredfox.foxhg.core.save.withBag
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Reusable TMs patch in the game: a saved game with TM27 (Return) in the bag, the TM is used on the first Pokemon of the party through the bag
 * (X opens the menu with the cursor on BAG, R three times reaches the TM pocket, A, USE, the messages, A on the party). Copies of the pair
 * (TM27, quantity 1) in RAM, counted after the save is loaded and again after the TM was used: on the vanilla ROM there are fewer (the TM is used up,
 * the TM pocket is empty in picture `reusable-tm-vanilla.png`), on the patched ROM as many (it is still there, `reusable-tm-patched.png`).
 * Each ROM is its own test class: the core does not survive a second ROM in the same process.
 */
private object ReusableTmsInGame {
    const val TM27_RETURN = 354

    private fun saveWithTm(): ByteArray {
        val original = (SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("party"))) as FoxResult.Ok).value
        val bag = (BagData.read(original) as FoxResult.Ok).value
        val edited = (bag.adding(BagPocket.TMS_HMS, TM27_RETURN, 1) as FoxResult.Ok).value
        return (original.withBag(edited) as FoxResult.Ok).value.toFileBytes()
    }

    /** Uses the TM on a ROM edited by [edit]; returns the copies of the pair in RAM before and after. */
    fun useTheTm(picture: String, edit: (RomProject) -> Unit): Pair<Int, Int>? {
        if (!available() || !EmulatorSaves.available("party")) return null
        val work = Files.createTempDirectory("foxhg-tm")
        try {
            val rom = MartInGame.build(work, "tm.nds", edit)
            val saves = work.resolve("saves")
            Files.createDirectories(saves)
            Files.write(saves.resolve("tm.dsv"), saveWithTm())
            EmulatorDriver(rom, saves).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s200,B*3,s60")
                val before = emu.findU16s(listOf(TM27_RETURN, 1)).size
                emu.run("X,s60,A,s90,R*3,s40,A,s40,A,s90,A,s150,A,s150,A,s150,A,s150,A,s150,R*3,s60")
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve(picture))
                return before to emu.findU16s(listOf(TM27_RETURN, 1)).size
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}

class ReusableTmsVanillaEmulatorTest {
    @Test
    fun aTmIsUsedUpOnTheVanillaRom() {
        val (before, after) = ReusableTmsInGame.useTheTm("reusable-tm-vanilla.png") { } ?: return
        println("REUSABLETM vanilla: copies of (TM27, 1) in RAM before $before, after $after")
        assertTrue(before > 0, "the save has the TM")
        assertTrue(after < before, "the vanilla game used the TM up")
    }
}

class ReusableTmsPatchedEmulatorTest {
    @Test
    fun aTmIsKeptOnThePatchedRom() {
        val (before, after) = ReusableTmsInGame.useTheTm("reusable-tm-patched.png") { check(ReusableTmsPatch.apply(it) is FoxResult.Ok) } ?: return
        println("REUSABLETM patched: copies of (TM27, 1) in RAM before $before, after $after")
        assertTrue(before > 0, "the save has the TM")
        assertTrue(after >= before, "the patched game still holds the TM")
    }
}
