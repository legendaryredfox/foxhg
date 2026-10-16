package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.data.mart.Marts
import java.nio.file.Files
import java.nio.file.Path

/** What the emulator tests share: the ROM, whether they can run, and a list of items read from RAM. */
object EmulatorSupport {
    val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    /** True when the ROM and the DeSmuME core are there; otherwise says so and the test skips. */
    fun available(): Boolean {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return false }
        if (EmulatorDriver.findCore() == null) { println("SKIPPED: no DeSmuME libretro core (set DESMUME_CORE)"); return false }
        return true
    }

    /** The items of the list at [address] in RAM, up to the 0xFFFF end. */
    fun EmulatorDriver.list(address: Long): List<Int> {
        val items = ArrayList<Int>()
        while (true) {
            val item = readU16(address + 2 * items.size)
            if (item == Marts.END) return items
            items += item
        }
    }
}
