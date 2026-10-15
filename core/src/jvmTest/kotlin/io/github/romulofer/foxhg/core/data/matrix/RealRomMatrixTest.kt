package io.github.romulofer.foxhg.core.data.matrix

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.mapheader.MapHeaderTable
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomMatrixTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    @Test
    fun everyMatrixRoundTripsAndTheOverworldIsTheBigOne() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val project = RomProject.open(source).ok()
            val table = GameMatrix.load(project).ok()
            assertEquals(288, table.records.size)
            assertContentEquals(project.read("a/0/4/1"), table.toNarc().toBytes())
            table.records.forEachIndexed { i, m -> assertContentEquals(project.rom.read("a/0/4/1")?.let { table.toNarc().files[i] }, m.toBytes(), "matrix $i") }

            val overworld = table.records[0]
            println("matrix 0 '${overworld.name}' ${overworld.width}x${overworld.height} headers=${overworld.hasHeaders} altitudes=${overworld.hasAltitudes}")
            assertEquals(47, overworld.width)
            assertEquals(17, overworld.height)
            assertTrue(overworld.hasHeaders && overworld.hasAltitudes)
            assertTrue(overworld.maps.count { it != GameMatrix.EMPTY } > 100, "the overworld has many maps")

            // Every matrix a map header points at exists.
            val headers = MapHeaderTable.load(project).ok()
            assertTrue(headers.all { it.matrix < table.records.size }, "header matrix ids are valid")
            // Headers point at real header ids in the overworld grid.
            assertTrue(overworld.headers!!.all { it < headers.size }, "header grid ids are valid map headers")
        }
    }
}
