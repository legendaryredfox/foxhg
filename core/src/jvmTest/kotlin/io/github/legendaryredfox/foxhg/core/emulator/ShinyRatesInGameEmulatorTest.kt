package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.patch.ShinyRates
import io.github.legendaryredfox.foxhg.core.patch.ShinyRatesPatch
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.save.PartyData
import io.github.legendaryredfox.foxhg.core.save.Pokemon
import io.github.legendaryredfox.foxhg.core.save.SaveFile
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the "Shiny rates" emulator tests share. Each test class boots the game once (a libretro core can not be loaded twice in a process). */
internal object ShinyEmulator {
    fun install(project: RomProject, rates: ShinyRates) {
        check(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
        check(ShinyRatesPatch.apply(project) is FoxResult.Ok)
        check(ShinyRatesPatch.save(project, rates) is FoxResult.Ok)
    }

    fun Pokemon.shiny() = (((otId shr 16) xor (otId and 0xFFFF) xor (personality shr 16) xor (personality and 0xFFFF)) and 0xFFFF) < 8

    /** The party the save made by playing the new game of a ROM that gives three Pokémon (Cyndaquil, Pikachu, Pidgey) has, with [rates] installed. */
    fun giftParty(rates: ShinyRates): List<Pokemon> {
        val work = Files.createTempDirectory("foxhg-shiny")
        try {
            val rom = work.resolve("shiny-start.nds")
            MartStartRom.build(romPath, rom, MartStartRom.CHERRYGROVE_WITH_PARTY) { install(it, rates) }
            EmulatorDriver(rom, work.resolve("saves")).use { emu -> emu.run(EmulatorSaves.MAKE_SAVE_SCRIPT) }
            val bytes = Files.readAllBytes(work.resolve("saves").resolve("shiny-start.dsv"))
            val save = (SaveFile.parse(bytes) as FoxResult.Ok).value
            return (PartyData.read(save) as FoxResult.Ok).value.members
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    /** From the title screen through the Professor's speech: the init script runs when the speech ends. */
    const val TO_FIRST_MAP = "s1500,START,s200,A*3,s300,DOWN*4,A,s400,A*100,A*100,A*100,A*100"

    /** Every Pokémon of [species] that main RAM holds as a whole, checksummed structure (the battle's copy of an enemy among them). */
    fun EmulatorDriver.monsInRam(species: Int): List<Pokemon> {
        val ram = read(0x02000000L, 0x400000)
        val out = ArrayList<Pokemon>()
        var at = 0
        while (at + Pokemon.SIZE <= ram.size) {
            (Pokemon.parse(ram.copyOfRange(at, at + Pokemon.SIZE)) as? FoxResult.Ok)?.value?.takeIf { it.species == species }?.let { out += it }
            at += 4
        }
        return out
    }

    fun scriptedBattleMons(rates: ShinyRates, species: Int, level: Int): List<Pokemon>? = battleMons(rates, "WildBattle $species, $level, 0", setOf(species))

    /** The Pokémon of trainer [trainerId], from the vanilla ROM: the species of its party. */
    fun trainerSpecies(trainerId: Int): Set<Int> = FileRomSource(romPath).use { source ->
        val project = (RomProject.open(source) as FoxResult.Ok).value
        (TrainerTable.load(project) as FoxResult.Ok).value.trainers[trainerId].party.map { it.species }.toSet()
    }

    /** A trainer of the vanilla ROM whose header type is [type] (0 plain, 1 own moves, 2 held items, 3 both), single battle, a party of 1 to 3 that has none of the player's species. */
    fun trainerOfType(type: Int): Int = FileRomSource(romPath).use { source ->
        val project = (RomProject.open(source) as FoxResult.Ok).value
        (TrainerTable.load(project) as FoxResult.Ok).value.trainers.withIndex().first { (i, t) ->
            i > 0 && t.header.type == type && t.header.doubleBattle == 0L && t.party.size in 1..3 && t.party.none { it.species in setOf(155, 25, 16) } && t.party.map { it.species }.toSet().size == t.party.size
        }.index
    }

    /**
     * What main RAM holds of the Pokémon of [species] after the clerk of Cherrygrove's Poké Mart starts [command] (a script line) when the player
     * talks to them: the clerk's script (file 851, script 1) is edited to run it first, [rates] are installed, and the game is booted with the
     * saved game that stands next to the clerk (the `party` save). Null when the save is missing.
     */
    fun battleMons(rates: ShinyRates, command: String, species: Set<Int>): List<Pokemon>? {
        if (!EmulatorSaves.available("party")) return null
        val work = Files.createTempDirectory("foxhg-shiny")
        try {
            val rom = MartInGame.build(work, "shiny-battle.nds") { project ->
                install(project, rates)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.add(first + 1, "        $command")
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run(EmulatorSaves.OPEN_BUY_LIST_SCRIPT + ",s1200")
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("shiny-battle.png"))
                return species.flatMap { emu.monsInRam(it) }
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}

/** The "Shiny rates" patch in the game: gift Pokémon are all shiny at a gift rate of 100 %, and otherwise as they were given. */
class ShinyGiftsFullRateEmulatorTest {
    @Test
    fun everyGiftIsShinyAtAFullRate() {
        if (!available()) return
        val party = ShinyEmulator.giftParty(ShinyRates(0, 0, ShinyRates.MAX, 0))
        val shiny = with(ShinyEmulator) { party.map { it.shiny() } }
        println("SHINY gifts at 100%: " + party.map { "${it.species}/${it.level}" } + shiny)
        assertEquals(listOf(155, 25, 16), party.map { it.species })
        assertEquals(listOf(5, 20, 7), party.map { it.level })
        assertTrue(shiny.all { it }, "every gift is shiny at a rate of 100 %")
        assertTrue(party.all { it.experience > 0 && it.maxHp > 0 }, "the Pokémon are whole")
    }
}

/** The control: with every rate at 0 the patch changes nothing. */
class ShinyGiftsNoRateEmulatorTest {
    @Test
    fun noGiftIsShinyWithTheRatesAtZero() {
        if (!available()) return
        val party = ShinyEmulator.giftParty(ShinyRates.NONE)
        assertEquals(listOf(155, 25, 16), party.map { it.species })
        assertTrue(with(ShinyEmulator) { party.none { it.shiny() } }, "no gift is shiny with the rates at 0")
    }
}

/** A scripted wild battle (`WildBattle`, a Rattata) is shiny at a scripted rate of 100 %. */
class ShinyScriptedFullRateEmulatorTest {
    @Test
    fun theScriptedWildPokemonIsShinyAtAFullRate() {
        if (!available()) return
        val mons = ShinyEmulator.scriptedBattleMons(ShinyRates(0, ShinyRates.MAX, 0, 0), 19, 5) ?: return
        println("SHINY scripted battle at 100%: " + mons.map { "${it.species}/${it.level}" })
        assertTrue(mons.isNotEmpty(), "the wild Rattata is in RAM")
        assertTrue(with(ShinyEmulator) { mons.all { it.shiny() } }, "it is shiny")
    }
}

/** The control: a scripted battle is not shiny when only the wild rate is set (the scripted rate is its own). */
class ShinyScriptedOtherRateEmulatorTest {
    @Test
    fun theScriptedWildPokemonIsNotShinyWhenOnlyTheWildRateIsSet() {
        if (!available()) return
        val mons = ShinyEmulator.scriptedBattleMons(ShinyRates(ShinyRates.MAX, 0, ShinyRates.MAX, ShinyRates.MAX), 19, 5) ?: return
        assertTrue(mons.isNotEmpty(), "the wild Rattata is in RAM")
        assertTrue(with(ShinyEmulator) { mons.none { it.shiny() } }, "it is not shiny")
    }
}

private fun trainerTest(type: Int) {
    if (!available()) return
    val id = ShinyEmulator.trainerOfType(type)
    val species = ShinyEmulator.trainerSpecies(id)
    val mons = ShinyEmulator.battleMons(ShinyRates(0, 0, 0, ShinyRates.MAX), "TrainerBattle $id, 0, 0, 0", species) ?: return
    println("SHINY trainer $id of type $type at 100%: species $species found " + mons.map { "${it.species}/${it.level}" })
    assertTrue(mons.isNotEmpty(), "the trainer's Pokémon are in RAM")
    assertTrue(with(ShinyEmulator) { mons.all { it.shiny() } }, "they are shiny")
}

/** A trainer's Pokémon (plain party format) are shiny at a trainer rate of 100 %. */
class ShinyTrainerPlainEmulatorTest { @Test fun theTrainersPokemonAreShiny() = trainerTest(0) }

/** The same for a trainer whose party has held items and own moves (the fourth party format has its own call in the game's code). */
class ShinyTrainerItemMovesEmulatorTest { @Test fun theTrainersPokemonAreShiny() = trainerTest(3) }
