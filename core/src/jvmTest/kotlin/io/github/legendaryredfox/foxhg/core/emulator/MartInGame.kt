package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mart.Marts
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.nds.FileRomSink
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path

/** What the in-game mart tests share: a ROM variant built from the vanilla one, booted with a saved game that stands at a clerk, with the buy list open. */
object MartInGame {
    val shotDir: Path = Path.of("build", "emulator-shots")

    /** Builds a ROM from the vanilla one with [edit] applied to its project, under [name], in [dir]. */
    fun build(dir: Path, name: String, edit: (RomProject) -> Unit): Path {
        val target = dir.resolve(name)
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            edit(project)
            FileRomSink(target).use { sink -> check(project.build(sink) is FoxResult.Ok) { "build failed" } }
        }
        return target
    }

    /** Boots [rom] with the save [save], opens the buy list of the clerk next to the player, saves a picture as [picture] and runs [check] on the open game. */
    fun withBuyListOpen(save: String, rom: Path, picture: String, check: (EmulatorDriver) -> Unit) {
        val work = Files.createTempDirectory("foxhg-mart")
        try {
            EmulatorSaves.install(save, rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run(EmulatorSaves.OPEN_BUY_LIST_SCRIPT)
                Files.createDirectories(shotDir)
                emu.savePicture(shotDir.resolve(picture))
                check(emu)
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    fun vanillaMarts(): Marts = FileRomSource(romPath).use { source -> (Marts.load((RomProject.open(source) as FoxResult.Ok).value) as FoxResult.Ok).value }
}
