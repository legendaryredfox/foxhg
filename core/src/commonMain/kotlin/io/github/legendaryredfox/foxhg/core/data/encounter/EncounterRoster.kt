package io.github.legendaryredfox.foxhg.core.data.encounter

/**
 * The slots of an area that hold wild Pokémon. The file has a fixed number of slots per group and the game rolls them
 * with fixed chances ([percents], from the decomp's `EncounterSlot_WildMonSlotRoll_*`), so a Pokémon cannot be added
 * or removed by growing or shrinking a list: it takes over or gives up slots, and so its chance changes.
 */
enum class EncounterGroup(val label: String, val percents: List<Int>) {
    GRASS_MORNING("Grass, morning", EncounterData.LAND_SLOT_PERCENT),
    GRASS_DAY("Grass, day", EncounterData.LAND_SLOT_PERCENT),
    GRASS_NIGHT("Grass, night", EncounterData.LAND_SLOT_PERCENT),
    SURF("Surfing", listOf(60, 30, 5, 4, 1)),
    ROCK_SMASH("Rock smash", listOf(80, 20)),
    OLD_ROD("Old rod", listOf(40, 30, 15, 10, 5)),
    GOOD_ROD("Good rod", listOf(40, 30, 15, 10, 5)),
    SUPER_ROD("Super rod", listOf(40, 30, 15, 10, 5)),
    ;

    /** Species of each slot, in slot order. */
    fun species(d: EncounterData): List<Int> = when (this) {
        GRASS_MORNING -> d.landMorning
        GRASS_DAY -> d.landDay
        GRASS_NIGHT -> d.landNight
        SURF -> d.surf.map { it.species }
        ROCK_SMASH -> d.rockSmash.map { it.species }
        OLD_ROD -> d.oldRod.map { it.species }
        GOOD_ROD -> d.goodRod.map { it.species }
        SUPER_ROD -> d.superRod.map { it.species }
    }

    /** [d] with this group's slots replaced by [slots] (a level range comes along with a slot where the group has one). */
    internal fun withSlots(d: EncounterData, slots: List<EncounterSlot>?, land: List<Int>?): EncounterData = when (this) {
        GRASS_MORNING -> d.copy(landMorning = land!!)
        GRASS_DAY -> d.copy(landDay = land!!)
        GRASS_NIGHT -> d.copy(landNight = land!!)
        SURF -> d.copy(surf = slots!!)
        ROCK_SMASH -> d.copy(rockSmash = slots!!)
        OLD_ROD -> d.copy(oldRod = slots!!)
        GOOD_ROD -> d.copy(goodRod = slots!!)
        SUPER_ROD -> d.copy(superRod = slots!!)
    }

    private fun slots(d: EncounterData): List<EncounterSlot>? = when (this) {
        SURF -> d.surf
        ROCK_SMASH -> d.rockSmash
        OLD_ROD -> d.oldRod
        GOOD_ROD -> d.goodRod
        SUPER_ROD -> d.superRod
        else -> null
    }

    /** Pokémon of this group with their total chance, most likely first. Species 0 (empty slot) is not listed. */
    fun roster(d: EncounterData): List<RosterEntry> = EncounterRoster.roster(species(d), percents)

    /** [d] without [species]; its slots go to the other Pokémon of the group. Null when [species] is absent or the last one. */
    fun remove(d: EncounterData, species: Int): EncounterData? {
        val s = slots(d)
        return if (s == null) {
            val out = EncounterRoster.remove(species(d), percents, { it }, species) ?: return null
            withSlots(d, null, out)
        } else {
            val out = EncounterRoster.remove(s, percents, { it.species }, species) ?: return null
            withSlots(d, out, null)
        }
    }

    /**
     * [d] with [species] added at about [percent] percent, taken from the Pokémon that have most. Null when the species
     * is already there or no Pokémon has a second slot to give. Species 0 is an empty slot and never added. If the group is empty (all species 0) the new Pokémon
     * takes every slot.
     */
    fun add(d: EncounterData, species: Int, percent: Int): EncounterData? {
        val s = slots(d)
        return if (s == null) {
            val out = EncounterRoster.add(species(d), percents, { it }, species, percent) { _, id -> id } ?: return null
            withSlots(d, null, out)
        } else {
            val out = EncounterRoster.add(s, percents, { it.species }, species, percent) { old, id -> old.copy(species = id) } ?: return null
            withSlots(d, out, null)
        }
    }
}

data class RosterEntry(val species: Int, val percent: Int, val slots: List<Int>)

/** Slot arithmetic behind [EncounterGroup.add] and [EncounterGroup.remove]; generic over what a slot holds. */
object EncounterRoster {
    fun roster(species: List<Int>, percents: List<Int>): List<RosterEntry> =
        species.indices.filter { species[it] != 0 }.groupBy { species[it] }
            .map { (id, at) -> RosterEntry(id, at.sumOf { percents[it] }, at) }
            .sortedWith(compareByDescending<RosterEntry> { it.percent }.thenBy { it.slots.first() })

    fun <T> remove(slots: List<T>, percents: List<Int>, key: (T) -> Int, target: Int): List<T>? {
        val entries = roster(slots.map(key), percents)
        val gone = entries.firstOrNull { it.species == target } ?: return null
        val rest = entries.filter { it.species != target }
        if (rest.isEmpty()) return null
        val totals = rest.associate { it.species to it.percent }.toMutableMap()
        val firstSlot = rest.associate { it.species to it.slots.first() }
        val out = slots.toMutableList()
        // biggest freed slot to the Pokémon that has least, so the chance spreads out instead of piling on one
        for (i in gone.slots.sortedByDescending { percents[it] }) {
            val to = rest.minWith(compareBy<RosterEntry> { totals.getValue(it.species) }.thenBy { it.slots.first() }).species
            out[i] = slots[firstSlot.getValue(to)]
            totals[to] = totals.getValue(to) + percents[i]
        }
        return out
    }

    fun <T> add(slots: List<T>, percents: List<Int>, key: (T) -> Int, species: Int, percent: Int, assign: (T, Int) -> T): List<T>? {
        val ids = slots.map(key)
        if (species == 0 || species in ids) return null
        if (ids.all { it == 0 }) return slots.map { assign(it, species) }
        val owners = ids.toMutableList()
        val taken = ArrayList<Int>()
        var remaining = percent
        while (true) {
            val counts = owners.filter { it != 0 }.groupingBy { it }.eachCount()
            // a Pokémon keeps at least one slot, and empty slots are free to take
            val free = owners.indices.filter { it !in taken && (owners[it] == 0 || counts.getValue(owners[it]) > 1) }
            if (free.isEmpty()) break
            val fit = free.filter { percents[it] <= remaining }
            val pick = (if (fit.isNotEmpty()) fit.maxWithOrNull(byPercentThenOwnerShare(percents, owners)) else if (taken.isEmpty()) free.minWithOrNull(compareBy { percents[it] }) else null) ?: break
            taken += pick
            remaining -= percents[pick]
            owners[pick] = species
            if (remaining <= 0) break
        }
        if (taken.isEmpty()) return null
        return slots.mapIndexed { i, s -> if (i in taken) assign(s, species) else s }
    }

    /** Larger slot first; among equals, the one whose owner has the biggest total chance, then the earlier slot. */
    private fun byPercentThenOwnerShare(percents: List<Int>, owners: List<Int>): Comparator<Int> {
        val share = owners.indices.groupBy { owners[it] }.mapValues { (_, at) -> at.sumOf { percents[it] } }
        return compareBy<Int> { percents[it] }.thenBy { share.getValue(owners[it]) }.thenByDescending { it }
    }
}
