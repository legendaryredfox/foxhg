package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.grounditem.GroundItems
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class GroundItemsRealRomTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun theItemBallsOfTheGameAreListedWithTheMapsThatUseThemAndEditsStick() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val balls = (GroundItems.load(project) as FoxResult.Ok).value
            println("GROUND ITEMS ${balls.size}, unused ${balls.count { it.usedBy.isEmpty() }}")
            assertEquals(255, balls.size, "script_1 to script_255; script_256 is the shared function")
            assertEquals((7000..7254).toList(), balls.map { it.scriptNumber })
            assertEquals(listOf(7000, 17, 1), balls.first().let { listOf(it.scriptNumber, it.item, it.quantity) }, "Route 29 Potion")
            assertTrue(balls.first().usedBy.isNotEmpty(), "a map has the Route 29 Potion")
            assertTrue(balls.count { it.usedBy.isNotEmpty() } > 200, "nearly every ball is on a map")

            val target = balls[5]
            assertTrue(GroundItems.change(project, target.scriptNumber, 3, 2, itemCount = 537) is FoxResult.Ok)
            val after = (GroundItems.load(project) as FoxResult.Ok).value
            assertEquals(listOf(3, 2), after[5].let { listOf(it.item, it.quantity) })
            assertEquals(balls.filterIndexed { i, _ -> i != 5 }, after.filterIndexed { i, _ -> i != 5 }, "the other balls are left alone")
            assertTrue(project.isDirty)
            assertTrue(GroundItems.change(project, 7000, 0, 1) is FoxResult.Err, "no item 0")
            assertTrue(GroundItems.change(project, 7000, 600, 1, itemCount = 537) is FoxResult.Err, "no item 600")
            assertTrue(GroundItems.change(project, 7000, 17, 0) is FoxResult.Err, "no quantity 0")
            assertTrue(GroundItems.change(project, 7255, 17, 1) is FoxResult.Err, "the shared function is not a ball")
            assertTrue(GroundItems.change(project, 6999, 17, 1) is FoxResult.Err, "not a ball script")
        }
    }
}
