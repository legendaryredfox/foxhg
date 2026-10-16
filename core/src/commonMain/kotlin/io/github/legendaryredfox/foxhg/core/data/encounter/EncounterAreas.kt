package io.github.legendaryredfox.foxhg.core.data.encounter

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeader
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** Which maps use each encounter file, so the editor can say "Route 29" instead of "file 12". */
class EncounterAreas(private val mapsByFile: Map<Int, List<Int>>, private val locationOf: (Int) -> String) {

    /** Header ids that use [file]. */
    fun maps(file: Int): List<Int> = mapsByFile[file] ?: emptyList()

    fun label(file: Int): String {
        val ids = maps(file)
        if (ids.isEmpty()) return "%03d  (unused)".format(file)
        val names = ids.map(locationOf).distinct()
        return "%03d  %s".format(file, names.first() + if (names.size > 1) " +${names.size - 1}" else "")
    }

    companion object {
        fun load(project: RomProject, names: GameNames?): EncounterAreas {
            val headers = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
            val byFile = HashMap<Int, MutableList<Int>>()
            headers.forEachIndexed { id, h -> if (h.wildEncounterFile != MapHeader.NO_ENCOUNTERS) byFile.getOrPut(h.wildEncounterFile) { mutableListOf() } += id }
            return EncounterAreas(byFile) { id -> names?.locations?.getOrNull(headers[id].locationName) ?: "Map $id" }
        }
    }
}
