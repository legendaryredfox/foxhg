package io.github.legendaryredfox.foxhg.core.data.safari

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/** One Safari Zone Pokémon: species and level, and the byte after them, kept so an unedited file writes back the same bytes. */
data class SafariEncounter(val species: Int, val level: Int, val padding: Int = 0)

/** What the player must have placed in the area, and how many: type 0 is no requirement. */
data class SafariRequirement(val type: Int, val quantity: Int)

/**
 * The Pokémon of one kind of ground or water: [SafariZoneFile.SLOTS] per time of day, and then, for each of the
 * [objectSlots] arrangements of objects the player can place, one more Pokémon per time of day with the two
 * requirements ([required], [optional]) that bring it in. `objectSlots` of 0 is a group with no arrangements.
 */
data class SafariGroup(
    val objectSlots: Int,
    val morning: List<SafariEncounter>,
    val day: List<SafariEncounter>,
    val night: List<SafariEncounter>,
    val morningObject: List<SafariEncounter>,
    val dayObject: List<SafariEncounter>,
    val nightObject: List<SafariEncounter>,
    val required: List<SafariRequirement>,
    val optional: List<SafariRequirement>,
) {
    init {
        require(listOf(morning, day, night).all { it.size == SafariZoneFile.SLOTS }) { "${SafariZoneFile.SLOTS} Pokémon per time of day" }
        require(listOf(morningObject, dayObject, nightObject, required, optional).all { it.size == objectSlots }) { "$objectSlots object arrangements" }
    }

    /** The Pokémon list of a time of day (0 morning, 1 day, 2 night). */
    fun base(time: Int): List<SafariEncounter> = listOf(morning, day, night)[time]

    /** The arrangement Pokémon of a time of day, one per arrangement. */
    fun objects(time: Int): List<SafariEncounter> = listOf(morningObject, dayObject, nightObject)[time]

    fun withBase(time: Int, slot: Int, encounter: SafariEncounter): SafariGroup = when (time) {
        0 -> copy(morning = morning.replaced(slot, encounter))
        1 -> copy(day = day.replaced(slot, encounter))
        else -> copy(night = night.replaced(slot, encounter))
    }

    fun withObject(time: Int, slot: Int, encounter: SafariEncounter): SafariGroup = when (time) {
        0 -> copy(morningObject = morningObject.replaced(slot, encounter))
        1 -> copy(dayObject = dayObject.replaced(slot, encounter))
        else -> copy(nightObject = nightObject.replaced(slot, encounter))
    }

    fun withRequirement(slot: Int, optionalOne: Boolean, requirement: SafariRequirement): SafariGroup =
        if (optionalOne) copy(optional = optional.replaced(slot, requirement)) else copy(required = required.replaced(slot, requirement))

    private fun <T> List<T>.replaced(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

    companion object {
        val EMPTY = SafariGroup(
            0, List(SafariZoneFile.SLOTS) { SafariEncounter(0, 0) }, List(SafariZoneFile.SLOTS) { SafariEncounter(0, 0) },
            List(SafariZoneFile.SLOTS) { SafariEncounter(0, 0) }, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        )
    }
}

/**
 * One file of `a/2/3/0` (the Safari Zone's twelve kinds of area, see [NAMES]), from DSPRE's `SafariZoneEncounterFile`:
 * five bytes with the object arrangement count of each group, three bytes of padding, then the groups in [GROUPS] order.
 * A group is three times ten encounters (u16 species, u8 level, one byte of padding), three times `objectSlots`
 * encounters, and `objectSlots` pairs of requirements (u8 type, u8 quantity: required, then optional).
 * A file shorter than the header is [stub] and has no data.
 */
data class SafariZoneFile(val groups: List<SafariGroup>, val padding: List<Int> = listOf(0, 0, 0), val stub: Boolean = false) {
    init { require(groups.size == GROUPS.size && padding.size == 3) { "${GROUPS.size} groups" } }

    fun withGroup(index: Int, group: SafariGroup): SafariZoneFile = copy(groups = groups.toMutableList().also { it[index] = group }, stub = false)

    fun toBytes(): ByteArray {
        if (stub) return ByteArray(0)
        val size = HEADER + groups.sumOf { GROUP_BASE + 3 * ENCOUNTER * it.objectSlots + 4 * it.objectSlots }
        val out = ByteArray(size)
        groups.forEachIndexed { i, g -> out[i] = g.objectSlots.toByte() }
        padding.forEachIndexed { i, b -> out[GROUPS.size + i] = b.toByte() }
        var at = HEADER
        fun encounters(list: List<SafariEncounter>) {
            for (e in list) {
                out.putU16(at, e.species)
                out[at + 2] = e.level.toByte()
                out[at + 3] = e.padding.toByte()
                at += ENCOUNTER
            }
        }
        for (g in groups) {
            encounters(g.morning); encounters(g.day); encounters(g.night)
            encounters(g.morningObject); encounters(g.dayObject); encounters(g.nightObject)
            for (i in 0 until g.objectSlots) {
                out[at] = g.required[i].type.toByte(); out[at + 1] = g.required[i].quantity.toByte()
                out[at + 2] = g.optional[i].type.toByte(); out[at + 3] = g.optional[i].quantity.toByte()
                at += 4
            }
        }
        return out
    }

    companion object {
        /** Pokémon per time of day in a group. */
        const val SLOTS = 10
        const val HEADER = 8
        const val ENCOUNTER = 4
        private const val GROUP_BASE = 3 * SLOTS * ENCOUNTER

        /** The groups of a file, in the order the file stores them. */
        val GROUPS = listOf("Tall grass", "Surfing", "Old Rod", "Good Rod", "Super Rod")

        val TIMES = listOf("Morning", "Day", "Night")

        /** The names of the twelve files (area types of the Safari Zone), from DSPRE. */
        val NAMES = listOf("Plains", "Meadow", "Savannah", "Peak", "Rocky Beach", "Wetland", "Forest", "Swamp", "Marshland", "Wasteland", "Mountain", "Desert")

        /** What an object requirement of each type means, from DSPRE. */
        val OBJECT_TYPES = listOf("No requirement", "Plains", "Forest", "Peak", "Waterside")

        val EMPTY = SafariZoneFile(List(GROUPS.size) { SafariGroup.EMPTY }, stub = true)

        fun parse(bytes: ByteArray): FoxResult<SafariZoneFile> {
            if (bytes.size < GROUPS.size) return FoxResult.Ok(EMPTY)
            if (bytes.size < HEADER) return FoxResult.Err("A Safari Zone file is at least $HEADER bytes, got ${bytes.size}")
            val counts = List(GROUPS.size) { bytes[it].toInt() and 0xFF }
            val expected = HEADER + counts.sumOf { GROUP_BASE + 3 * ENCOUNTER * it + 4 * it }
            if (bytes.size != expected) return FoxResult.Err("A Safari Zone file with $counts object arrangements is $expected bytes, got ${bytes.size}")
            var at = HEADER
            fun encounters(n: Int) = List(n) { SafariEncounter(bytes.u16(at), bytes[at + 2].toInt() and 0xFF, bytes[at + 3].toInt() and 0xFF).also { at += ENCOUNTER } }
            val groups = counts.map { n ->
                val morning = encounters(SLOTS); val day = encounters(SLOTS); val night = encounters(SLOTS)
                val morningObject = encounters(n); val dayObject = encounters(n); val nightObject = encounters(n)
                val required = ArrayList<SafariRequirement>(n)
                val optional = ArrayList<SafariRequirement>(n)
                repeat(n) {
                    required += SafariRequirement(bytes[at].toInt() and 0xFF, bytes[at + 1].toInt() and 0xFF)
                    optional += SafariRequirement(bytes[at + 2].toInt() and 0xFF, bytes[at + 3].toInt() and 0xFF)
                    at += 4
                }
                SafariGroup(n, morning, day, night, morningObject, dayObject, nightObject, required, optional)
            }
            return FoxResult.Ok(SafariZoneFile(groups, List(3) { bytes[GROUPS.size + it].toInt() and 0xFF }))
        }

        val SPEC = RecordSpec(HgssLayout.SAFARI_ZONE, ::parse, SafariZoneFile::toBytes)
    }
}
