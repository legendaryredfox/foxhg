package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.event.EventKind
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Paints tiles through the map editor model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class MapEditorEndToEndTest {
    @Test
    fun paintedTilesSurviveABuild() {
        if (!romAvailable()) return
        val app = testModel()
        val built = tempFile("foxhg-e2e-map", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val editor = MapEditorModel(project, app::projectChanged)
            editor.select(14) // Route 27
            assertTrue(editor.label(14).contains("Route 27"), editor.label(14))
            assertNotNull(editor.usage(14))
            assertNotNull(editor.eventsOf(editor.usage(14)))
            val before = editor.maps.selected!!

            editor.tool = MapTool.PAINT
            editor.paintType = true; editor.paintCollision = true
            editor.brushType = 8; editor.brushCollision = 0x80
            editor.apply(2, 3)
            assertEquals(listOf(8, 0x80), listOf(editor.maps.selected!!.type(2, 3), editor.maps.selected!!.collision(2, 3)))
            assertTrue(editor.maps.isEdited(14) && !editor.maps.isEdited(13))

            editor.tool = MapTool.PICK
            editor.apply(0, 0)
            assertEquals(before.type(0, 0), editor.brushType, "pick copies the tile into the brush")

            editor.tool = MapTool.FILL
            editor.paintCollision = false
            editor.brushType = 3
            editor.apply(30, 30)
            val filled = editor.maps.selected!!
            assertEquals(3, filled.type(30, 30))
            assertEquals(8, filled.type(2, 3), "the painted tile is not part of the filled area unless it matched")

            app.build(built)
            assertNull(app.error, app.error)
            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val map = (MapFile.load(reopened) as FoxResult.Ok).value.records[14]
                assertEquals(filled, map)
                assertEquals(before.buildings, map.buildings, "buildings are untouched")
                assertTrue(before.model.contentEquals(map.model), "the terrain model is untouched")
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}

/** Moves, adds, edits and removes events through the map editor model, builds, reopens, reads back. */
class MapEventsEndToEndTest {
    @Test
    fun eventEditsOnTheMapSurviveABuild() {
        if (!romAvailable()) return
        val app = testModel()
        val built = tempFile("foxhg-e2e-mapevents", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!
            val editor = MapEditorModel(project, app::projectChanged)
            // a map whose cell holds an NPC
            val map = (0 until editor.maps.recordCount).first { m ->
                val u = editor.usage(m)
                val f = editor.eventsOf(u)
                u != null && f != null && f.overworlds.any { !it.isAlias && it.x / 32 == u.x && it.y / 32 == u.y }
            }
            editor.select(map)
            val usage = editor.usage(map)!!
            val file = editor.eventsOf(usage)!!
            val npc = file.overworlds.indexOfFirst { !it.isAlias && it.x / 32 == usage.x && it.y / 32 == usage.y }
            val at = file.overworlds[npc]
            val tile = at.x % 32 to at.y % 32

            editor.tool = MapTool.EVENTS
            editor.pressEvent(usage, tile.first, tile.second)
            assertEquals(EventKind.OVERWORLD to file.at(at.x, at.y).first { it.first == EventKind.OVERWORLD }.second, editor.selectedEvent)
            val target = ((tile.first + 1) % 32) to tile.second
            editor.dragEvent(usage, target.first, target.second)
            editor.endDrag()
            val moved = editor.eventsOf(usage)!!.overworlds[editor.selectedEvent!!.second]
            assertEquals(usage.x * 32 + target.first to usage.y * 32 + target.second, moved.x to moved.y)

            editor.setObject(usage, moved.copy(script = 4321))
            editor.addEvent(usage, EventKind.WARP, 3 to 4)
            assertEquals(EventKind.WARP, editor.selectedEvent!!.first)
            val countWarps = editor.eventsOf(usage)!!.warps.size
            editor.duplicateSelected(usage)
            assertEquals(countWarps + 1, editor.eventsOf(usage)!!.warps.size)
            editor.removeSelected(usage)
            assertEquals(countWarps, editor.eventsOf(usage)!!.warps.size)
            assertNull(editor.selectedEvent)
            assertTrue(editor.isEventFileEdited(usage))

            val expected = editor.eventsOf(usage)!!
            app.build(built)
            assertNull(app.error, app.error)
            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val events = (io.github.romulofer.foxhg.core.data.event.EventFile.load(reopened) as FoxResult.Ok).value.records
                assertTrue(expected in events, "the edited event file is in the rebuilt ROM")
                val reread = events.first { it == expected }
                assertEquals(4321, reread.overworlds.first { it.script == 4321 }.script)
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
