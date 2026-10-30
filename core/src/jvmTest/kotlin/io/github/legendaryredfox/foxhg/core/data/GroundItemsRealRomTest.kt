package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.data.grounditem.GroundItems
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.LabelRef
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptItem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class GroundItemsRealRomTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

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

    @Test
    fun aBallIsAddedAtTheEndAndTheLastOneCanGoAgain() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val before = (GroundItems.load(project) as FoxResult.Ok).value
            val scriptsBefore = (ScriptEntry.load(project) as FoxResult.Ok).value.records[GroundItems.SCRIPT_FILE]
            assertTrue(GroundItems.add(project, 0, 1) is FoxResult.Err, "no item 0")
            assertTrue(GroundItems.add(project, 17, 0) is FoxResult.Err, "no quantity 0")
            assertTrue(!project.isDirty, "a refused add writes nothing")

            val number = (GroundItems.add(project, 77, 3, itemCount = 537) as FoxResult.Ok).value
            assertEquals(7255, number, "the new ball takes the number the shared function had")
            val after = (GroundItems.load(project) as FoxResult.Ok).value
            assertEquals(256, after.size)
            assertEquals(before, after.take(255), "the other balls are left alone")
            assertEquals(listOf(7255, 77, 3), after.last().let { listOf(it.scriptNumber, it.item, it.quantity) })
            assertTrue(after.last().usedBy.isEmpty())
            val file = ((ScriptEntry.load(project) as FoxResult.Ok).value.records[GroundItems.SCRIPT_FILE] as ScriptEntry.Script).file
            assertEquals(257, file.scriptCount, "the shared function is now script_257")
            assertNull(file.problem())
            // every ball, the new one too, jumps to the shared function at its new number
            val jumps = file.items.filterIsInstance<ScriptItem.Instruction>().mapNotNull { it.command.target }.filter { it.kind == LabelRef.Kind.SCRIPT }
            assertEquals(256, jumps.size)
            assertTrue(jumps.all { it == LabelRef(LabelRef.Kind.SCRIPT, 257) })

            // a second one, then the ball can be edited like any other
            assertEquals(7256, (GroundItems.add(project, 4, 1, itemCount = 537) as FoxResult.Ok).value)
            assertTrue(GroundItems.change(project, 7255, 5, 2, itemCount = 537) is FoxResult.Ok)
            assertEquals(listOf(5, 2), (GroundItems.load(project) as FoxResult.Ok).value[255].let { listOf(it.item, it.quantity) })

            // the last ball goes back, one at a time; a ball an event runs stays
            assertTrue(GroundItems.removeLast(project) is FoxResult.Ok)
            assertTrue(GroundItems.removeLast(project) is FoxResult.Ok)
            assertEquals(before, (GroundItems.load(project) as FoxResult.Ok).value)
            val scriptsAfter = (ScriptEntry.load(project) as FoxResult.Ok).value.records[GroundItems.SCRIPT_FILE]
            assertEquals(scriptsBefore, scriptsAfter, "adding and removing leaves script file 141 as it was")
            val used = before.last().usedBy.isNotEmpty()
            val refusal = GroundItems.removeLast(project)
            if (used) assertTrue(refusal is FoxResult.Err, "the last vanilla ball is run by a map") else println("the last vanilla ball is unused, so it was removed: ${refusal::class.simpleName}")
        }
    }

    @Test
    fun aBallInTheMiddleGoesAndTheNumbersAfterItMoveDownInEventsAndScripts() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val before = (GroundItems.load(project) as FoxResult.Ok).value
            val eventsBefore = (EventFile.load(project) as FoxResult.Ok).value.records

            // a ball an event runs stays, and nothing is written
            val used = before.first { it.usedBy.isNotEmpty() }
            assertTrue(GroundItems.remove(project, used.scriptNumber) is FoxResult.Err)
            assertTrue(GroundItems.remove(project, 6999) is FoxResult.Err, "not a ball")
            assertTrue(GroundItems.remove(project, 7255) is FoxResult.Err, "the shared function is not a ball")
            assertTrue(!project.isDirty)

            // the unused vanilla ball goes: the others keep their item and quantity, and every event that ran a later ball runs it one number down
            val unused = before.first { it.usedBy.isEmpty() }
            assertTrue(GroundItems.remove(project, unused.scriptNumber) is FoxResult.Ok)
            val after = (GroundItems.load(project) as FoxResult.Ok).value
            val expected = before.filter { it.scriptNumber != unused.scriptNumber }
            assertEquals(expected.map { it.item to it.quantity }, after.map { it.item to it.quantity })
            assertEquals(expected.map { it.usedBy }, after.map { it.usedBy }, "the maps follow their balls")
            assertEquals(before.size - 1, after.size)
            assertEquals((7000 until 7000 + after.size).toList(), after.map { it.scriptNumber })
            val eventsAfter = (EventFile.load(project) as FoxResult.Ok).value.records
            eventsBefore.zip(eventsAfter).forEach { (a, b) ->
                assertEquals(a.overworlds.map { if (it.script in unused.scriptNumber + 1..7254) it.script - 1 else it.script }, b.overworlds.map { it.script })
                assertEquals(a.triggers.map { if (it.script in unused.scriptNumber + 1..7254) it.script - 1 else it.script }, b.triggers.map { it.script })
            }
            val file = ((ScriptEntry.load(project) as FoxResult.Ok).value.records[GroundItems.SCRIPT_FILE] as ScriptEntry.Script).file
            assertNull(file.problem())
            assertEquals(255, file.scriptCount, "one entry fewer")
        }
    }

    @Test
    fun removingABallMovesTheEventsAndCallStdOfTheBallsAfterIt() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            assertEquals(7255, (GroundItems.add(project, 4, 1, itemCount = 537) as FoxResult.Ok).value)
            assertEquals(7256, (GroundItems.add(project, 5, 2, itemCount = 537) as FoxResult.Ok).value)
            // an object runs the last new ball
            val events = (EventFile.load(project) as FoxResult.Ok).value
            val fileAt = events.records.indexOfFirst { it.overworlds.any { o -> !o.isAlias } }
            val ev = events.records[fileAt]
            val objectAt = ev.overworlds.indexOfFirst { !it.isAlias }
            val ran = ev.overworlds[objectAt].script
            events.with(fileAt, ev.copy(overworlds = ev.overworlds.mapIndexed { i, o -> if (i == objectAt) o.copy(script = 7256) else o })).save(project)
            assertTrue(GroundItems.remove(project, 7255) is FoxResult.Ok)
            val moved = (EventFile.load(project) as FoxResult.Ok).value.records[fileAt].overworlds[objectAt]
            assertEquals(7255, moved.script, "the object follows its ball")
            val balls = (GroundItems.load(project) as FoxResult.Ok).value
            assertEquals(listOf(7255, 5, 2), balls.last().let { listOf(it.scriptNumber, it.item, it.quantity) })
            assertEquals(listOf(fileAt), balls.last().usedBy)
            assertTrue(ran != 7256)
        }
    }
}
