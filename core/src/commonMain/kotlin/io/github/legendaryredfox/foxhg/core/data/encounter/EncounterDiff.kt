package io.github.legendaryredfox.foxhg.core.data.encounter

/** One difference between two versions of the same area, in words the editor can show. */
data class EncounterChange(val what: String, val from: Int, val to: Int)

/**
 * Compares an area's wild Pokémon between two ROM versions (HeartGold vs SoulSilver tables). Species and levels are
 * compared slot by slot; encounter rates too. [speciesName] turns ids into names for the description.
 */
object EncounterDiff {
    fun between(a: EncounterData, b: EncounterData): List<EncounterChange> {
        val out = ArrayList<EncounterChange>()
        fun cmp(what: String, x: Int, y: Int) { if (x != y) out += EncounterChange(what, x, y) }
        cmp("rate walking", a.rateWalking, b.rateWalking); cmp("rate surfing", a.rateSurfing, b.rateSurfing)
        cmp("rate rock smash", a.rateRockSmash, b.rateRockSmash); cmp("rate old rod", a.rateOldRod, b.rateOldRod)
        cmp("rate good rod", a.rateGoodRod, b.rateGoodRod); cmp("rate super rod", a.rateSuperRod, b.rateSuperRod)
        for (i in 0 until EncounterData.LAND) {
            cmp("land ${i + 1} level", a.landLevels[i], b.landLevels[i])
            cmp("land ${i + 1} morning", a.landMorning[i], b.landMorning[i])
            cmp("land ${i + 1} day", a.landDay[i], b.landDay[i])
            cmp("land ${i + 1} night", a.landNight[i], b.landNight[i])
        }
        for (i in 0 until 2) {
            cmp("Hoenn sound ${i + 1}", a.hoennSounds[i], b.hoennSounds[i])
            cmp("Sinnoh sound ${i + 1}", a.sinnohSounds[i], b.sinnohSounds[i])
        }
        fun slots(name: String, x: List<EncounterSlot>, y: List<EncounterSlot>) = x.indices.forEach { i ->
            cmp("$name ${i + 1} species", x[i].species, y[i].species)
            cmp("$name ${i + 1} min level", x[i].levelMin, y[i].levelMin)
            cmp("$name ${i + 1} max level", x[i].levelMax, y[i].levelMax)
        }
        slots("surf", a.surf, b.surf); slots("rock smash", a.rockSmash, b.rockSmash)
        slots("old rod", a.oldRod, b.oldRod); slots("good rod", a.goodRod, b.goodRod); slots("super rod", a.superRod, b.superRod)
        cmp("land swarm", a.swarmLand, b.swarmLand); cmp("surf swarm", a.swarmSurf, b.swarmSurf)
        cmp("night fishing", a.nightFish, b.nightFish); cmp("fishing swarm", a.swarmFish, b.swarmFish)
        return out
    }
}
