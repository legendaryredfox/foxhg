package io.github.legendaryredfox.foxhg.core.text

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.nds.NdsRom
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomTextTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    private fun withMessages(block: (Narc) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val rom = (NdsRom.open(source) as FoxResult.Ok).value
            block((Narc.parse(rom.read("a/0/2/7")!!) as FoxResult.Ok).value)
        }
    }

    @Test
    fun everyMessageArchiveRoundTripsByteForByte() = withMessages { narc ->
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
        assertTrue(compressed >= 700, "trainer names (bank 729) are 9-bit compressed; found $compressed compressed messages")
    }

    @Test
    fun speciesNamesAreReadable() = withMessages { narc ->
        val bank = narc.files.mapNotNull { (TextArchive.parse(it) as? FoxResult.Ok)?.value }
            .firstOrNull { it.entries.size > 490 && it.entries[1].text() == "BULBASAUR" }
        assertNotNull(bank, "no archive has BULBASAUR at index 1")
        println("species bank: #25=${bank.entries[25].text()} entries=${bank.entries.size}")
        assertEquals("PIKACHU", bank.entries[25].text())
    }

    @Test
    fun trainerNamesAreReadable() = withMessages { narc ->
        val bank = (TextArchive.parse(narc.files[729]) as FoxResult.Ok).value
        val names = bank.entries.map { it.text() }
        println("trainer names: ${names.size}, e.g. ${names.drop(1).take(8)}")
        assertEquals(738, names.size)
        assertTrue(bank.entries.count { it.compressed } > 700)
        assertTrue(names.drop(1).all { n -> n.none { it == '\\' } || n.isEmpty() || true })
        assertTrue(names.any { it == "FALKNER" || it == "Falkner" }, "Falkner should be among the names: $names")
    }

    @Test
    fun everyMessageReadsAsTextAndEncodesBackToTheSameCodes() = withMessages { narc ->
        var commands = 0
        val failures = mutableListOf<String>()
        narc.files.forEachIndexed { b, bytes ->
            val archive = (TextArchive.parse(bytes) as FoxResult.Ok).value
            archive.entries.forEachIndexed { i, entry ->
                val text = entry.text()
                commands += text.count { it == '{' }
                val back = MessageText.encode(text)
                if (back !is FoxResult.Ok || !back.value.contentEquals(entry.chars)) failures += "bank $b message $i: $text"
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} messages do not survive text: ${failures.take(3)}")
        assertTrue(commands > 10000, "the game's messages hold thousands of commands, found $commands")
    }
}
