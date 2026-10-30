package io.github.legendaryredfox.foxhg.core.game

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.nds.NdsRom
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomNamesTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    @Test
    fun namesComeFromTheRomText() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val rom = (NdsRom.open(source) as FoxResult.Ok).value
            val names = GameNames((GameText.open(rom, HgssEnglishLayout) as FoxResult.Ok).value, HgssEnglishLayout.textBanks)
            assertEquals(496, names.species.size)
            assertEquals("BULBASAUR", names.species[1])
            assertEquals("PIKACHU", names.species[25])
            assertEquals("MEW", names.species[151])
            assertEquals("Pound", names.moves[1])
            assertEquals("Master Ball", names.items[1])
            assertEquals("Stench", names.abilities[1])
            assertEquals("NORMAL", names.types[0])
            assertEquals("Youngster", names.trainerClasses[2])
            assertEquals("PKMN Trainer", names.trainerClasses[0])
            assertEquals("#9999", GameNames.name(names.species, 9999))
            println("locations[1]=${names.locations.getOrNull(1)} species[250]=${names.species[250]} species[386]=${names.species[386]}")
        }
    }
}
