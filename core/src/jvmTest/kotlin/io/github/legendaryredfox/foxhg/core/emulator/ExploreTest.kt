package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

/**
 * A scripted run for finding out where the game is: does nothing unless `FOXHG_SCRIPT` is set (the steps are those of [EmulatorDriver.run];
 * `save:name` is not needed: the core writes the in-game save into the save directory when it closes). `FOXHG_EXPLORE_DIR` is where pictures
 * and states go, `FOXHG_EXPLORE_ROM` replaces the ROM and `FOXHG_EXPLORE_SAVE_DIR` is where the core keeps and finds saves (`<rom name>.dsv`).
 */
class ExploreTest {
    @Test
    fun run() {
        val script = System.getenv("FOXHG_SCRIPT") ?: return
        if (!available()) return
        val out = Path.of(System.getenv("FOXHG_EXPLORE_DIR") ?: error("set FOXHG_EXPLORE_DIR"))
        Files.createDirectories(out)
        val rom = System.getenv("FOXHG_EXPLORE_ROM")?.takeIf { it.isNotBlank() }?.let(Path::of) ?: romPath
        val saves = System.getenv("FOXHG_EXPLORE_SAVE_DIR")?.takeIf { it.isNotBlank() }?.let(Path::of) ?: Files.createTempDirectory("foxhg-explore")
        EmulatorDriver(rom, saves).use { emu ->
            emu.run(script, out)
            println("EXPLORE done after ${emu.frames} frames")
        }
    }
}
