package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainer.NewTrainer
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.data.event.OverworldEvent
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
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
                repeat(4) { i -> id = (NewTrainer.add(project, if (i == 3) "FOXHG" else "T$i", 1 + i) as FoxResult.Ok).value }
                check(id == 741) { "the fourth added trainer is number $id, not 741" }
                check((TrainerTable.load(project) as FoxResult.Ok).value.trainers.size == 742)
                // the clerk next to the player (the start is x 3, y 6 of header 68) becomes the trainer's map object: type 1 and the script id of trainer 741
                val header = (MapHeaderTable.load(project) as FoxResult.Ok).value[68]
                val events = (EventFile.load(project) as FoxResult.Ok).value
                val file = events.records[header.eventFile]
                val clerk = file.overworlds.indices.filter { file.overworlds[it].script != OverworldEvent.ALIAS_SCRIPT }.minBy { Math.abs(file.overworlds[it].x - 3) + Math.abs(file.overworlds[it].y - 6) }
                val object741 = file.overworlds[clerk].copy(type = 1, script = 3000 + 741 - 1, sightRange = 0, flag = 0)
                check(events.with(header.eventFile, file.copy(overworlds = file.overworlds.mapIndexed { i, o -> if (i == clerk) object741 else o })).save(project) is FoxResult.Ok)
            }
            val name = (MessageText.encode("FOXHG") as FoxResult.Ok).value.toList()
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
                emu.run("A*2")
                var after = 0
                repeat(80) {
                    emu.step(30)
                    if (after == 0) emu.findU16s(name).size.let { n -> if (n > 0) { after = n; emu.step(240); emu.savePicture(MartInGame.shotDir.resolve("new-trainer-script.png")) } }
                }
                if (after == 0) emu.savePicture(MartInGame.shotDir.resolve("new-trainer-script.png"))
                println("NEWTRAINERSCRIPT copies of the name FOXHG in RAM before: $before, while the battle starts: $after")
                assertTrue(after > before, "the shared trainer script started the battle against trainer 741")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
