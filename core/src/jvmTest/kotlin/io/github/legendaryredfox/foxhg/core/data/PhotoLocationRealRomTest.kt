package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.photo.PhotoLocation
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class PhotoLocationRealRomTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun thePhotoLocationsRoundTripAndMatchTheGuide() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val narc = (Narc.parse(project.read(PhotoLocation.SPEC.path(project.layout))!!) as FoxResult.Ok).value
            val records = (PhotoLocation.load(project) as FoxResult.Ok).value
            println("PHOTO locations ${records.records.size}")
            assertEquals(narc.files.size, records.records.size)
            narc.files.forEachIndexed { i, bytes -> assertContentEquals(bytes, records.records[i].toBytes(), "photo location $i round trip") }
            // the guide's example: file 9 is the Route 34 Day Care (header 38, building exterior, x 363, y 410, the Day Care Man 330)
            assertEquals(PhotoLocation(38, 5, 363, 410, 0xFF01, 330, 0, 0), records.records[9])
            assertTrue(records.records.all { it.unknown == 0xFF01 }, "the constant word")
            assertEquals(9, records.records.count { it.npc == 0 && (it.param1 != 0 || it.param2 != 0) }, "the guide's rule about parameters without an NPC does not hold in the ROM")
            assertTrue(records.records.all { it.icon in PhotoLocation.ICONS.indices })
        }
    }
}
