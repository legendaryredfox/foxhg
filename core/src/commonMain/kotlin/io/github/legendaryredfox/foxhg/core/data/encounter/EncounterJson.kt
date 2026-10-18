package io.github.legendaryredfox.foxhg.core.data.encounter

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A read-only JSON dump of the wild Pokémon of every area, for tools that are not foxhg (spreadsheets, scripts, wikis). Species
 * come as an id and a name, land slots carry their chance in percent. A species id of 0 (none) is written as `null` for the name.
 */
object EncounterJson {
    private val pretty = Json { prettyPrint = true }

    fun export(areas: List<EncounterData>, areaName: (Int) -> String, speciesName: (Int) -> String): String =
        pretty.encodeToString(JsonElement.serializer(), toJson(areas, areaName, speciesName))

    fun toJson(areas: List<EncounterData>, areaName: (Int) -> String, speciesName: (Int) -> String): JsonElement {
        fun species(id: Int): JsonElement = buildJsonObject {
            put("id", id)
            put("name", if (id == 0) JsonNull else JsonPrimitive(speciesName(id)))
        }

        fun slots(list: List<EncounterSlot>): JsonArray = buildJsonArray {
            list.forEach { s ->
                add(buildJsonObject { put("level_min", s.levelMin); put("level_max", s.levelMax); put("species", species(s.species)) })
            }
        }

        return buildJsonArray {
            areas.forEachIndexed { i, d ->
                add(
                    buildJsonObject {
                        put("area", i)
                        put("name", areaName(i))
                        put(
                            "rates",
                            buildJsonObject {
                                put("walking", d.rateWalking); put("surfing", d.rateSurfing); put("rock_smash", d.rateRockSmash)
                                put("old_rod", d.rateOldRod); put("good_rod", d.rateGoodRod); put("super_rod", d.rateSuperRod)
                            },
                        )
                        put(
                            "land",
                            buildJsonArray {
                                for (slot in 0 until EncounterData.LAND) add(
                                    buildJsonObject {
                                        put("slot", slot)
                                        put("percent", EncounterData.LAND_SLOT_PERCENT[slot])
                                        put("level", d.landLevels[slot])
                                        put("morning", species(d.landMorning[slot]))
                                        put("day", species(d.landDay[slot]))
                                        put("night", species(d.landNight[slot]))
                                    },
                                )
                            },
                        )
                        put("hoenn_sounds", buildJsonArray { d.hoennSounds.forEach { add(species(it)) } })
                        put("sinnoh_sounds", buildJsonArray { d.sinnohSounds.forEach { add(species(it)) } })
                        put("surf", slots(d.surf))
                        put("rock_smash", slots(d.rockSmash))
                        put("old_rod", slots(d.oldRod))
                        put("good_rod", slots(d.goodRod))
                        put("super_rod", slots(d.superRod))
                        put(
                            "swarms",
                            buildJsonObject {
                                put("land", species(d.swarmLand)); put("surf", species(d.swarmSurf))
                                put("night_fish", species(d.nightFish)); put("swarm_fish", species(d.swarmFish))
                            },
                        )
                    },
                )
            }
        }
    }
}
