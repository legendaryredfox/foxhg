package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.townmap.TownMap
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class TownMapRealRomTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun theTownMapTablesRoundTripAndEditsStick() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val overlay = (project.readOverlay(HgssLayout.TOWN_MAP_OVERLAY) as FoxResult.Ok).value
            val map = (TownMap.parse(overlay) as FoxResult.Ok).value
            assertContentEquals(overlay, (map.applyTo(overlay) as FoxResult.Ok).value, "parse then write reproduces overlay 101")
            assertEquals(27, map.spots.size)
            assertEquals(100, map.selections.size)
            // wiki: entry 0 is Pallet Town (map header 0x31, red 0x20,0x0B, gray 0x00,0x14), entry 0x0D Violet City (0x49)
            assertEquals(listOf(0x31, 0x20, 0x0B, 0x00, 0x14, 1, 1), map.spots[0].let { listOf(it.mapHeader, it.redX, it.redY, it.grayX, it.grayY, it.townDimX, it.townDimY) })
            assertEquals(listOf(0x49, 2, 2, 3, 4), map.spots[13].let { listOf(it.mapHeader, it.townDimX, it.townDimY, it.replacementDimX, it.replacementDimY) })
            assertEquals(0x209, map.selections[0].mapHeader)
            assertEquals(listOf(0x31, 0x20, 0x0D, 1, 1, 0x0A, 1), map.selections[28].let { listOf(it.mapHeader, it.baseX, it.baseY, it.dimX, it.dimY, it.textEntry, it.flySpot) })
            assertEquals(listOf(0x20, 3, 3), map.selections[28].let { listOf(it.orangeY, it.orangeDimX, it.orangeDimY) })

            assertTrue(TownMap.save(project, map.withSpot(0, map.spots[0].copy(redX = 0x22, townDimY = 2)).withSelection(5, map.selections[5].copy(baseX = 9, flySpot = 0))) is FoxResult.Ok)
            val after = (TownMap.load(project) as FoxResult.Ok).value
            assertEquals(listOf(0x22, 2), after.spots[0].let { listOf(it.redX, it.townDimY) })
            assertEquals(listOf(9, 0), after.selections[5].let { listOf(it.baseX, it.flySpot) })
            assertEquals(map.spots.drop(1), after.spots.drop(1))
            assertEquals(map.selections.filterIndexed { i, _ -> i != 5 }, after.selections.filterIndexed { i, _ -> i != 5 })
            assertTrue(project.isDirty)
            assertTrue(TownMap.save(project, map.withSpot(0, map.spots[0].copy(townDimX = 16))) is FoxResult.Err, "a size is a nibble")
            assertTrue(TownMap.save(project, map.withSelection(0, map.selections[0].copy(baseX = 256))) is FoxResult.Err, "a position is a byte")
        }
    }
}
