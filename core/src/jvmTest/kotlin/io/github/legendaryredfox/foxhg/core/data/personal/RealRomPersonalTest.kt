package io.github.legendaryredfox.foxhg.core.data.personal

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.nds.NdsRom
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomPersonalTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    private fun withPersonal(block: (List<PersonalData>, Narc) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val rom = (NdsRom.open(source) as FoxResult.Ok).value
            val narc = (Narc.parse(rom.read(HgssEnglishLayout.personal)!!) as FoxResult.Ok).value
            block(narc.files.map { (PersonalData.parse(it) as FoxResult.Ok).value }, narc)
        }
    }

    @Test
    fun everyRecordRoundTrips() = withPersonal { records, narc ->
        println("personal records=${records.size}")
        records.forEachIndexed { i, r -> assertContentEquals(narc.files[i], r.toBytes(), "record $i") }
        assertEquals(true, records.size >= 496)
    }

    @Test
    fun bulbasaurAndPikachuMatchTheGame() = withPersonal { records, _ ->
        val bulbasaur = records[1]
        assertEquals(listOf(45, 49, 49, 45, 65, 65), with(bulbasaur) { listOf(baseHp, baseAttack, baseDefense, baseSpeed, baseSpAttack, baseSpDefense) })
        assertEquals(listOf(12, 3), listOf(bulbasaur.type1, bulbasaur.type2)) // Grass / Poison
        assertEquals(45, bulbasaur.catchRate)
        assertEquals(65, bulbasaur.ability1) // Overgrow
        assertEquals(31, bulbasaur.genderRatio)

        val pikachu = records[25]
        assertEquals(listOf(35, 55, 30, 90, 50, 40), with(pikachu) { listOf(baseHp, baseAttack, baseDefense, baseSpeed, baseSpAttack, baseSpDefense) })
        assertEquals(listOf(13, 13), listOf(pikachu.type1, pikachu.type2)) // Electric
        assertEquals(9, pikachu.ability1) // Static
        assertEquals(190, pikachu.catchRate)
        assertEquals(2, pikachu.evSpeed)
    }
}
