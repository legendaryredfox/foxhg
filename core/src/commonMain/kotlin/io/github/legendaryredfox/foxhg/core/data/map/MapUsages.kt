package io.github.legendaryredfox.foxhg.core.data.map

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeader
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.data.matrix.GameMatrix
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** A map file as used by an area: the cell (x, y) of [matrix], whose own map header is [header]. */
data class MapUsage(val header: Int, val matrix: Int, val x: Int, val y: Int)

/**
 * Where each map file is used. A cell has its own header when its matrix stores headers per cell (outdoor areas);
 * otherwise the header whose matrix it is. A map file can be used several times (a house interior copied around).
 */
class MapUsages(project: RomProject) {
    val headers: List<MapHeader> = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
    private val byMap = HashMap<Int, MutableList<MapUsage>>()
    private val byHeader = HashMap<Int, Int>()

    init {
        val matrices = (GameMatrix.load(project) as? FoxResult.Ok)?.value?.records ?: emptyList()
        val matrixOwner = HashMap<Int, Int>()
        headers.forEachIndexed { i, h -> matrixOwner.putIfAbsent(h.matrix, i) }
        matrices.forEachIndexed { mi, m ->
            for (y in 0 until m.height) for (x in 0 until m.width) {
                val map = m.map(x, y)
                if (map == GameMatrix.EMPTY) continue
                val header = m.header(x, y) ?: matrixOwner[mi] ?: continue
                byMap.getOrPut(map) { ArrayList() } += MapUsage(header, mi, x, y)
                byHeader.putIfAbsent(header, map)
            }
        }
    }

    fun of(map: Int): List<MapUsage> = byMap[map] ?: emptyList()

    /** The map file shown for [header]: the first cell that uses the header. Null when no cell does. */
    fun mapOf(header: Int): Int? = byHeader[header]
}
