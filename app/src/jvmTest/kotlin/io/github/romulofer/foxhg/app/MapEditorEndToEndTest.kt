package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Paints tiles through the map editor model on the real ROM, builds, reopens, reads back. Skips without the ROM. */
class MapEditorEndToEndTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    @Test
    fun paintedTilesSurviveABuild() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        val app = testModel()
        val built = Files.createTempFile("foxhg-e2e-map", ".nds")
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
