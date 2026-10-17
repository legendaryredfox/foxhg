package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.staticmon.StaticEncounters
import io.github.legendaryredfox.foxhg.core.data.staticmon.StaticKind
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class StaticEncountersRealRomTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun theScriptsOfTheGameHoldItsFixedPokemonAndEditsStick() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val found = (StaticEncounters.find(project) as FoxResult.Ok).value
            println("STATICS " + found.groupBy { it.kind }.mapValues { it.value.size })
            for (e in found) println("STATIC ${e.kind} file ${e.file} item ${e.index}${if (e.viaVariable) " via SetVar for command ${e.commandIndex}" else ""} species ${e.species}${if (e.speciesIsVariable) " (var)" else ""} level ${e.level}${if (e.levelIsVariable) " (var)" else ""}")
            assertTrue(found.any { it.kind == StaticKind.BATTLE }, "scripted battles")
            assertTrue(found.any { it.kind == StaticKind.GIFT }, "gift Pokémon")
            assertTrue(found.any { it.kind == StaticKind.EGG }, "eggs")
            // vanilla: Mewtwo L70, the Eevee gift L5, Tyrogue L10, the three eggs of the Day-Care man's gift
            assertTrue(found.any { it.kind == StaticKind.BATTLE && it.species == 150 && it.level == 70 })
            val eevee = found.first { it.kind == StaticKind.GIFT && it.species == 133 && !it.viaVariable }
            assertEquals(5, eevee.level)
            assertEquals(listOf(179, 194, 218), found.filter { it.kind == StaticKind.EGG }.map { it.species })

            // an edit lands in the script, nothing else changes, and the project says so
            val before = StaticEncounters.find(project).let { (it as FoxResult.Ok).value }
            assertTrue(StaticEncounters.change(project, eevee, 4, 8) is FoxResult.Ok)
            val after = (StaticEncounters.find(project) as FoxResult.Ok).value
            assertEquals(before.size, after.size)
            val changed = after.first { it.file == eevee.file && it.index == eevee.index }
            assertEquals(listOf(4, 8), listOf(changed.species, changed.level))
            assertEquals(before.filter { it != eevee }, after.filter { it.file != eevee.file || it.index != eevee.index }, "the other Pokémon are left alone")
            assertTrue(project.isDirty)
            assertTrue(StaticEncounters.change(project, eevee, 0, 8) is FoxResult.Err, "no species 0")
            assertTrue(StaticEncounters.change(project, eevee, 4, 101) is FoxResult.Err, "no level 101")
            val variable = found.first { it.speciesIsVariable }
            assertTrue(StaticEncounters.change(project, variable, 4, 8) is FoxResult.Err, "a species from a variable nobody sets with a number is not edited here")

            // species that a script puts in a variable first: Ho-Oh and Lugia, Latias and Latios, the Game Corner prizes, the gift starters
            fun viaVariable(file: Int) = found.filter { it.file == file && it.viaVariable }
            assertEquals(listOf(250), viaVariable(21).map { it.species }, "Ho-Oh")
            assertEquals(listOf(249), viaVariable(104).map { it.species }, "Lugia")
            assertEquals(listOf(381, 380), viaVariable(750).map { it.species }, "Latios and Latias")
            assertEquals(listOf(122, 133, 137), viaVariable(804).map { it.species }, "Game Corner prizes")
            assertEquals(listOf(1, 7, 4), viaVariable(740).map { it.species }, "Kanto starters")
            assertEquals(listOf(252, 255, 258), viaVariable(837).map { it.species }, "Hoenn starters")
            val latios = viaVariable(750).first()
            assertTrue(!latios.speciesIsVariable && latios.index != latios.commandIndex)
            assertTrue(StaticEncounters.change(project, latios, 245, 41) is FoxResult.Ok)
            val moved = (StaticEncounters.find(project) as FoxResult.Ok).value
            assertEquals(listOf(245 to 41, 380 to 41), moved.filter { it.file == 750 && it.viaVariable }.map { it.species to it.level }, "the level is the command's, so both rows change")
            assertEquals(moved.size, after.size, "no encounter appears or goes")
            val egg = found.first { it.kind == StaticKind.EGG }
            assertTrue(StaticEncounters.change(project, egg, 25, null) is FoxResult.Ok)
            assertEquals(25, (StaticEncounters.find(project) as FoxResult.Ok).value.first { it.file == egg.file && it.index == egg.index }.species)
        }
    }
}
