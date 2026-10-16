package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import java.nio.file.Files
import java.nio.file.Path

/**
 * The saves the emulator tests start from. They are made by playing the new game on a ROM that starts in a Poke Mart ([MartStartRom]) and saving
 * there, once, into `build/emulator-saves` (never committed, they come from a commercial ROM); a test copies one next to the ROM it boots under
 * the name the core looks for (`<rom name>.dsv`, DeSmuME's save: 512 KB and a footer).
 */
object EmulatorSaves {
    val dir: Path = Path.of("build", "emulator-saves")

    /** Playing from the title screen to a saved game in the mart: the info screens, the Professor's speech (mashing A), the clerk's menu, the start menu and Save. */
    const val MAKE_SAVE_SCRIPT =
        "s1500,START,s200,A*3,s300,DOWN*4,A,s400,A*100,A*100,A*100,A*100,s300,B,s120,B,s200,B,s200,A,s90,X,s60,RIGHT,UP,s20,A,s200,A*3,s2400"

    /** From the title screen of a ROM that has the save: Continue, talk to the clerk next to the player and open the buy list. */
    const val OPEN_BUY_LIST_SCRIPT = "s1500,START,s200,A*3,s400,A,s200,A*2,s300,A,s150"

    fun cached(name: String): Path = dir.resolve("$name.dsv")

    /** Copies the save [name] to where the core looks for the save of the ROM at [rom]. */
    fun install(name: String, rom: Path, saveDirectory: Path) {
        Files.createDirectories(saveDirectory)
        Files.copy(cached(name), saveDirectory.resolve(rom.fileName.toString().substringBeforeLast('.') + ".dsv"))
    }

    /** True when the save [name] was made; otherwise says so and the test skips. */
    fun available(name: String): Boolean {
        if (Files.exists(cached(name))) return true
        println("SKIPPED: no save '$name' in ${dir.toAbsolutePath()} (the emulatorSetup task makes it)")
        return false
    }

    /** Plays the new game on [start]'s ROM and keeps the save as [name]; does nothing when it exists. */
    fun make(name: String, start: MartStartRom.Start) {
        if (!EmulatorSupport.available() || Files.exists(cached(name))) return
        val work = Files.createTempDirectory("foxhg-save")
        try {
            val rom = work.resolve("$name-start.nds")
            MartStartRom.build(romPath, rom, start)
            EmulatorDriver(rom, work.resolve("saves")).use { emu -> emu.run(MAKE_SAVE_SCRIPT) }
            val made = work.resolve("saves").resolve("$name-start.dsv")
            val bytes = Files.readAllBytes(made)
            check(bytes.size == 524288 + 122) { "the save is ${bytes.size} bytes" }
            check(bytes.take(524288).count { it != 0xFF.toByte() } > 100_000) { "the save was not written completely" }
            Files.createDirectories(dir)
            Files.write(cached(name), bytes)
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
