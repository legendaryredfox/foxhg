package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainer.NewTrainer
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.data.event.OverworldEvent
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import io.github.legendaryredfox.foxhg.core.save.SaveFile
import io.github.legendaryredfox.foxhg.core.save.SaveVarsFlags
import io.github.legendaryredfox.foxhg.core.save.flagsChunk
import io.github.legendaryredfox.foxhg.core.text.MessageText
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The map script of a trainer added after 740 (`script_741: GoTo script_1` in script file 953, see [NewTrainer]). The clerk of Cherrygrove's Poke Mart is made the map
 * object of trainer 741 (type 1, script id 3740 = 3000 + 741 - 1); talking to it runs `script_741`, which jumps to the shared trainer body, which reads the trainer
 * from the number of the running script and starts the battle against the trainer called FOXHG: its name is in RAM once the introduction shows.
 */
class NewTrainerScriptInGameEmulatorTest {
    @Test
    fun aTrainerAfter740BattlesThroughTheSharedTrainerScript() {
        if (!available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-new-trainer-script")
        try {
            val rom = MartInGame.build(work, "new-trainer-script.nds") { project ->
                var id = -1
                // the last one is a copy of a Youngster (one Rattata), so winning takes a few presses of A
                val classes = (TextArchive.load(project) as FoxResult.Ok).value.records[TextBanks.TRAINER_CLASSES].entries.map { it.text() }
                val youngster = (TrainerTable.load(project) as FoxResult.Ok).value.trainers.indexOfFirst { classes.getOrNull(it.header.trainerClass) == "Youngster" }
                check(youngster > 0) { "no Youngster in the ROM" }
                repeat(4) { i -> id = (NewTrainer.add(project, if (i == 3) "FOXHG" else "T$i", if (i == 3) youngster else 1 + i) as FoxResult.Ok).value }
                check(id == 741) { "the fourth added trainer is number $id, not 741" }
                val table = (TrainerTable.load(project) as FoxResult.Ok).value
                check(table.trainers.size == 742)
                // level 2 Pokémon: the player's Cyndaquil wins in a hit or two, so the flag check below does not depend on luck
                check(table.with(741, table.trainers[741].let { t -> t.copy(party = t.party.map { it.copy(level = 2) }) }).save(project) is FoxResult.Ok)
                // the clerk next to the player (the start is x 3, y 6 of header 68) becomes the trainer's map object: type 1 and the script id of trainer 741
                val header = (MapHeaderTable.load(project) as FoxResult.Ok).value[68]
                val events = (EventFile.load(project) as FoxResult.Ok).value
                val file = events.records[header.eventFile]
                val clerk = file.overworlds.indices.filter { file.overworlds[it].script != OverworldEvent.ALIAS_SCRIPT }.minBy { Math.abs(file.overworlds[it].x - 3) + Math.abs(file.overworlds[it].y - 6) }
                val object741 = file.overworlds[clerk].copy(type = 1, script = 3000 + 741 - 1, sightRange = 0, flag = 0)
                check(events.with(header.eventFile, file.copy(overworlds = file.overworlds.mapIndexed { i, o -> if (i == clerk) object741 else o })).save(project) is FoxResult.Ok)
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s400")
                Files.createDirectories(MartInGame.shotDir)
                // the game keeps the objects of the map in the save, so leave the mart and come back: it then reads the event file again
                emu.run("DOWN:16*5,RIGHT:16*1,DOWN:16*3,DOWN:16*3,s400")
                emu.savePicture(MartInGame.shotDir.resolve("walk-1.png"))
                emu.run("UP:16*2,s300")
                emu.savePicture(MartInGame.shotDir.resolve("walk-2.png"))
                emu.run("UP:16*5,LEFT:16*1,s60")
                emu.savePicture(MartInGame.shotDir.resolve("walk-3.png"))
                val name = (MessageText.encode("FOXHG") as FoxResult.Ok).value.toList()
                val before = emu.findU16s(name).size
                // where the game keeps the event flags in RAM: the 64 bytes of the saved game's flag bitmap with the most flags set
                val saved = (SaveFile.parse(Files.readAllBytes(EmulatorSaves.cached("party"))) as FoxResult.Ok).value
                val bitmap = (saved.flagsChunk() as FoxResult.Ok).value.copyOfRange(SaveVarsFlags.FLAG_BYTES_OFFSET, SaveVarsFlags.FLAG_BYTES_OFFSET + SaveVarsFlags.FLAG_COUNT / 8)
                val windowAt = (0..bitmap.size - 64).maxBy { w -> (w until w + 64).count { bitmap[it].toInt() != 0 } }
                val window = bitmap.copyOfRange(windowAt, windowAt + 64)
                val ram = emu.read(EmulatorDriver.RAM_BASE, 0x400000)
                // the game has changed a few flags since the save was written (temporary ones, the ones the walk set), so match most of the bytes, not all
                val anchors = (0 until 64).filter { window[it].toInt() != 0 }.take(8)
                val hits = (0..ram.size - 64).filter { i -> anchors.count { ram[i + it] == window[it] } >= anchors.size - 1 && (0 until 64).count { ram[i + it] == window[it] } >= 56 }
                println("NEWTRAINERSCRIPT the flag bitmap window (bytes $windowAt on) is in RAM at ${hits.map { "0x" + (EmulatorDriver.RAM_BASE + it).toString(16) }}")
                check(hits.size == 1) { "the flag bitmap is not in RAM exactly once" }
                val flagByte = EmulatorDriver.RAM_BASE + hits.single() - windowAt + 0x835 / 8
                val flagBit = 1 shl (0x835 % 8)
                val flagSet = { emu.read(flagByte, 1)[0].toInt() and flagBit != 0 }
                assertTrue(!flagSet(), "the beaten flag of trainer 741 (0x835) is off before the battle")
                emu.run("A*2")
                var after = 0
                repeat(80) {
                    emu.step(30)
                    if (after == 0) emu.findU16s(name).size.let { n -> if (n > 0) { after = n; emu.step(240); emu.savePicture(MartInGame.shotDir.resolve("new-trainer-script.png")) } }
                }
                if (after == 0) emu.savePicture(MartInGame.shotDir.resolve("new-trainer-script.png"))
                println("NEWTRAINERSCRIPT copies of the name FOXHG in RAM before: $before, while the battle starts: $after")
                assertTrue(after > before, "the shared trainer script started the battle against trainer 741")
                // win it (Cyndaquil against level 2 Pokémon) and the flag 0x550 + 741 = 0x835 is set
                repeat(200) { if (!flagSet()) emu.run("A,s60") }
                Files.createDirectories(MartInGame.shotDir)
                emu.savePicture(MartInGame.shotDir.resolve("new-trainer-script-won.png"))
                println("NEWTRAINERSCRIPT flag 0x835 after the battle: ${flagSet()}")
                assertTrue(flagSet(), "winning against trainer 741 set its beaten flag 0x835")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
