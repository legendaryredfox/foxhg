package io.github.legendaryredfox.foxhg.core.data.mapheader

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomMapHeaderTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    @Test
    fun writingTheTableBackThroughTheArm9Works() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val project = RomProject.open(source).ok()
            val names = project.names!!
            val headers = MapHeaderTable.load(project).ok()
            assertEquals(540, headers.size)

            // Unchanged table = no edit.
            MapHeaderTable.write(project, headers).ok()
            assertFalse(project.isDirty, "rewriting an identical table must not dirty the project")

            // What the packed fields say for known maps.
            fun find(name: String) = headers.indexOfFirst { names.locations.getOrNull(it.locationName) == name }
            val route29 = headers[find("Route 29")]
            println("Route 29: weather=${route29.weather} camera=${route29.camera} bg=${route29.battleBackground} type=${route29.locationType} perms=${route29.permissions.toString(2)} xy=(${route29.worldMapX},${route29.worldMapY})")
            val withBike = headers.count { it.permissions and 1 != 0 }
            val withFly = headers.count { it.permissions and (1 shl MapHeader.Permission.FLY.bit) != 0 }
            println("headers allowing bicycle=$withBike fly=$withFly")
            assertTrue(withBike > 50 && withFly > 50)
            assertTrue(route29.permissions and (1 shl MapHeader.Permission.FLY.bit) != 0, "you can Fly from Route 29")

            // Edit one header, read it back from the project and from a rebuilt ROM.
            val id = find("Route 29")
            val edited = headers.toMutableList().also { it[id] = it[id].withWeather(5).withPermission(MapHeader.Permission.BICYCLE, false) }
            MapHeaderTable.write(project, edited).ok()
            assertTrue(project.isDirty)
            val again = MapHeaderTable.load(project).ok()
            assertEquals(5, again[id].weather)
            assertContentEquals(edited.map { it.toBytes().toList() }, again.map { it.toBytes().toList() })
            assertContentEquals(headers.filterIndexed { i, _ -> i != id }.map { it.toBytes().toList() }, again.filterIndexed { i, _ -> i != id }.map { it.toBytes().toList() })
            assertEquals(headers.map { it.toBytes().toList() }, MapHeaderTable.original(project).map { it.toBytes().toList() }, "original() ignores the edit")
        }
    }

    @Test
    fun csvRoundTripsEveryHeaderAndAppliesAnEdit() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val project = RomProject.open(source).ok()
            val names = project.names!!
            val headers = MapHeaderTable.load(project).ok()
            val csv = MapHeader.CSV.export(headers) { names.locations.getOrElse(headers[it].locationName) { "" } }
            val back = MapHeader.CSV.import(csv, headers).ok()
            assertEquals(headers.map { it.toBytes().toList() }, back.map { it.toBytes().toList() }, "export then import keeps every byte of every header")

            val lines = csv.split("\r\n").filter { it.isNotEmpty() }.toMutableList()
            val cols = lines[0].split(",")
            val weather = cols.indexOf("weather")
            val row = lines[3].split(",").toMutableList().also { it[weather] = "5" }
            lines[3] = row.joinToString(",")
            val edited = MapHeader.CSV.import(lines.joinToString("\r\n"), headers).ok()
            assertEquals(5, edited[2].weather)
            assertEquals(headers[2].withWeather(5).toBytes().toList(), edited[2].toBytes().toList(), "only the weather bits change")
            assertTrue(MapHeader.CSV.import(csv.replaceFirst(",weather", ",nonsense"), headers) is FoxResult.Err, "an unknown column rejects the file")
        }
    }
}
