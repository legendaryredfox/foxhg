package io.github.legendaryredfox.foxhg.core.text

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.nds.NdsRom
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomTextTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun everyMessageArchiveRoundTripsByteForByte() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val rom = (NdsRom.open(source) as FoxResult.Ok).value
            val narc = (Narc.parse(rom.read("a/0/2/7")!!) as FoxResult.Ok).value
            var entries = 0
            var compressed = 0
            val failures = mutableListOf<String>()
            narc.files.forEachIndexed { i, bytes ->
                val parsed = TextArchive.parse(bytes)
                if (parsed !is FoxResult.Ok) {
                    failures += "archive $i: ${(parsed as FoxResult.Err).message}"
                    return@forEachIndexed
                }
                entries += parsed.value.entries.size
                compressed += parsed.value.entries.count { it.compressed }
                if (!bytes.contentEquals(parsed.value.toBytes())) failures += "archive $i: bytes differ"
            }
            println("archives=${narc.files.size} entries=$entries compressed=$compressed failures=${failures.size}")
            failures.take(10).forEach(::println)
            assertEquals(emptyList(), failures)
            assertTrue(narc.files.size > 100 && entries > 10_000)
        }
    }

    @Test
    fun speciesNamesAreReadable() {
        if (!Files.exists(romPath)) return
        FileRomSource(romPath).use { source ->
            val rom = (NdsRom.open(source) as FoxResult.Ok).value
            val narc = (Narc.parse(rom.read("a/0/2/7")!!) as FoxResult.Ok).value
            // Find the bank that holds the species names: it contains Bulbasaur at index 1.
            val bank = narc.files.map { (TextArchive.parse(it) as? FoxResult.Ok)?.value }
                .firstOrNull { it != null && it.entries.size > 490 && it.entries[1].text() == "Bulbasaur" }
            println("species bank found=${bank != null}, #25=${bank?.entries?.get(25)?.text()}")
            assertContentEquals(listOf("Bulbasaur", "Pikachu"), listOf(bank!!.entries[1].text(), bank.entries[25].text()))
        }
    }
}
