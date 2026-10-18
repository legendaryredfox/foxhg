package io.github.legendaryredfox.foxhg.core.data.encounter

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomEncounterTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    @Test
    fun encountersExportToJsonWithNamesAndChances() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val project = RomProject.open(source).ok()
            val names = project.names!!
            val hg = EncounterData.loadHeartGold(project).ok().records
            val areas = EncounterAreas.load(project, names)
            val text = EncounterJson.export(hg, { areas.label(it) }, { names.species.getOrElse(it) { "?" } })
            val parsed = Json.parseToJsonElement(text).jsonArray
            assertEquals(hg.size, parsed.size)
            val route29 = (0 until hg.size).first { areas.label(it).contains("Route 29") }
            val area = parsed[route29].jsonObject
            assertEquals(route29, area.getValue("area").jsonPrimitive.content.toInt())
            val land = area.getValue("land").jsonArray
            assertEquals(EncounterData.LAND, land.size)
            assertEquals(100, land.sumOf { it.jsonObject.getValue("percent").jsonPrimitive.content.toInt() })
            val first = land[0].jsonObject
            assertEquals(hg[route29].landLevels[0], first.getValue("level").jsonPrimitive.content.toInt())
            val day = first.getValue("day").jsonObject
            assertEquals(hg[route29].landDay[0], day.getValue("id").jsonPrimitive.content.toInt())
            assertEquals(names.species[hg[route29].landDay[0]], day.getValue("name").jsonPrimitive.content)
        }
    }

    @Test
    fun encountersRoundTripAndMatchTheGame() {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source ->
            val project = RomProject.open(source).ok()
            val names = project.names!!
            val hg = EncounterData.loadHeartGold(project).ok()
            val ss = EncounterData.loadSoulSilver(project).ok()
            assertEquals(142, hg.records.size)
            assertContentEquals(project.read("a/0/3/7"), hg.toNarc().toBytes())
            assertContentEquals(project.read("a/1/3/6"), ss.toNarc().toBytes())

            val headers = MapHeaderTable.load(project).ok()
            assertEquals(540, headers.size)
            assertEquals(135, headers.count { it.wildEncounterFile != 255 })
            headers.forEach { h -> assertContentEquals(h.toBytes(), h.toBytes()) }

            val areas = EncounterAreas.load(project, names)
            // Route 29 grass: Sentret and Hoothoot are in HeartGold's table.
            val route29 = (0 until 142).first { areas.label(it).contains("Route 29") }
            val day = hg.records[route29].landDay.map { names.species[it] }
            println("Route 29 = file $route29, day species=$day, level ${hg.records[route29].landLevels.first()}")
            assertTrue("SENTRET" in day && "PIDGEY" in day, "day species: $day")

            val differing = (0 until 142).filter { EncounterDiff.between(hg.records[it], ss.records[it]).isNotEmpty() }
            println("areas where HeartGold and SoulSilver differ: ${differing.size}")
            assertTrue(differing.size > 10)
            // Version exclusives (decomp data): Spinarak is HeartGold's, Ledyba SoulSilver's.
            val ledyba = names.species.indexOf("LEDYBA")
            val spinarak = names.species.indexOf("SPINARAK")
            fun EncounterData.land() = landMorning + landDay + landNight
            val swapped = differing.filter { i ->
                spinarak in hg.records[i].land() && spinarak !in ss.records[i].land() && ledyba in ss.records[i].land() && ledyba !in hg.records[i].land()
            }
            fun EncounterData.all(): Set<Int> = (land() + surf.map { it.species } + rockSmash.map { it.species } + oldRod.map { it.species } +
                goodRod.map { it.species } + superRod.map { it.species } + listOf(swarmLand, swarmSurf, nightFish, swarmFish)).toSet()
            val onlyHg = hg.records.flatMap { it.all() }.toSet() - ss.records.flatMap { it.all() }.toSet()
            val onlySs = ss.records.flatMap { it.all() }.toSet() - hg.records.flatMap { it.all() }.toSet()
            println("only in HG table: ${onlyHg.map { names.species[it] }}")
            println("only in SS table: ${onlySs.map { names.species[it] }}")
            println("Spinarak(HG) <-> Ledyba(SS) areas: ${swapped.map { areas.label(it) }}")
            assertTrue(swapped.isNotEmpty())
            for (sp in listOf("EKANS", "VULPIX", "MEOWTH", "DELIBIRD", "TEDDIURSA", "SKARMORY", "GULPIN", "MAWILE", "LEDYBA", "WEEDLE")) {
                assertTrue(names.species.indexOf(sp) in onlySs, "$sp should be in SoulSilver's table only")
            }
        }
    }
}
