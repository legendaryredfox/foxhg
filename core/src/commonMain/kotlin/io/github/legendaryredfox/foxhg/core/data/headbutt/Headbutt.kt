package io.github.legendaryredfox.foxhg.core.data.headbutt

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/** One Pokémon of a Headbutt table: species (0 is an empty slot) and the level range. */
data class HeadbuttEncounter(val species: Int, val minLevel: Int, val maxLevel: Int) {
    companion object { val EMPTY = HeadbuttEncounter(0, 0, 0) }
}

/** A tile in map-global coordinates (matrix cell times 32 plus the tile inside the map); [UNUSED] is 65535 x 65535. */
data class HeadbuttTree(val x: Int, val y: Int) {
    val isUnused: Boolean get() = x == UNUSED_COORD && y == UNUSED_COORD

    companion object {
        const val UNUSED_COORD = 0xFFFF
        /** Tiles per map file along one side, the step between matrix cells. */
        const val MAP_SIZE = 32
        val UNUSED = HeadbuttTree(UNUSED_COORD, UNUSED_COORD)
    }
}

/** One physical tree: up to [SIZE] tiles it can be headbutted from; unused ones are [HeadbuttTree.UNUSED]. */
data class HeadbuttTreeGroup(val trees: List<HeadbuttTree>) {
    init { require(trees.size == SIZE) { "A tree group has $SIZE tiles" } }

    fun with(index: Int, tree: HeadbuttTree): HeadbuttTreeGroup = HeadbuttTreeGroup(trees.toMutableList().also { it[index] = tree })

    companion object {
        const val SIZE = 6
        val EMPTY = HeadbuttTreeGroup(List(SIZE) { HeadbuttTree.UNUSED })
    }
}

/**
 * The Headbutt data of one map header (member N of `a/2/5/2` is map header N), from the DS Pokémon Hacking wiki's
 * Headbutt guide and DSPRE's `HeadbuttEncounterFile`.
 *
 * Layout: u8 normal group count, pad, u8 special group count, pad; then 12 normal encounters (the common table of 6
 * followed by the rare table of 6) and 6 special ones, 4 bytes each (u16 species, u8 min level, u8 max level); then 24
 * bytes per tree group (6 tiles of u16 x, u16 y), normal groups first. A map without trees is the 4-byte header alone
 * ([stub]); the encounters are then all empty and are not stored.
 */
data class HeadbuttFile(
    val normal: List<HeadbuttEncounter>,
    val special: List<HeadbuttEncounter>,
    val normalGroups: List<HeadbuttTreeGroup>,
    val specialGroups: List<HeadbuttTreeGroup>,
    val stub: Boolean = false,
    /** Header bytes 1 and 3, unused by the game and zero in the vanilla files, kept so a file edited by another tool writes back as it was. */
    val headerPadding: Pair<Int, Int> = 0 to 0,
) {
    init {
        require(normal.size == NORMAL_SLOTS && special.size == SPECIAL_SLOTS) { "$NORMAL_SLOTS normal and $SPECIAL_SLOTS special encounters" }
        require(normalGroups.size < 256 && specialGroups.size < 256) { "At most 255 tree groups of each kind" }
    }

    /** The common table (A) of normal trees. */
    val common: List<HeadbuttEncounter> get() = normal.subList(0, TABLE_SLOTS)

    /** The rare table (B) of normal trees. */
    val rare: List<HeadbuttEncounter> get() = normal.subList(TABLE_SLOTS, 2 * TABLE_SLOTS)

    val hasTrees: Boolean get() = normalGroups.isNotEmpty() || specialGroups.isNotEmpty()

    fun withNormal(slot: Int, encounter: HeadbuttEncounter): HeadbuttFile =
        copy(normal = normal.toMutableList().also { it[slot] = encounter }, stub = false)

    fun withSpecial(slot: Int, encounter: HeadbuttEncounter): HeadbuttFile =
        copy(special = special.toMutableList().also { it[slot] = encounter }, stub = false)

    fun toBytes(): ByteArray {
        if (stub && !hasTrees && (normal + special).all { it == HeadbuttEncounter.EMPTY }) return ByteArray(HEADER).also { it[1] = headerPadding.first.toByte(); it[3] = headerPadding.second.toByte() }
        val out = ByteArray(HEADER + 4 * (NORMAL_SLOTS + SPECIAL_SLOTS) + GROUP_BYTES * (normalGroups.size + specialGroups.size))
        out[0] = normalGroups.size.toByte()
        out[1] = headerPadding.first.toByte()
        out[2] = specialGroups.size.toByte()
        out[3] = headerPadding.second.toByte()
        var at = HEADER
        for (e in normal + special) {
            out.putU16(at, e.species)
            out[at + 2] = e.minLevel.toByte()
            out[at + 3] = e.maxLevel.toByte()
            at += 4
        }
        for (g in normalGroups + specialGroups) for (t in g.trees) {
            out.putU16(at, t.x)
            out.putU16(at + 2, t.y)
            at += 4
        }
        return out
    }

    companion object {
        const val NORMAL_SLOTS = 12
        const val SPECIAL_SLOTS = 6
        const val TABLE_SLOTS = 6
        const val HEADER = 4
        const val GROUP_BYTES = 4 * HeadbuttTreeGroup.SIZE

        /** Chance in percent of each slot of a table of 6. */
        val SLOT_CHANCE = listOf(50, 15, 15, 10, 5, 5)

        val EMPTY = HeadbuttFile(
            List(NORMAL_SLOTS) { HeadbuttEncounter.EMPTY }, List(SPECIAL_SLOTS) { HeadbuttEncounter.EMPTY }, emptyList(), emptyList(), stub = true,
        )

        fun parse(bytes: ByteArray): FoxResult<HeadbuttFile> {
            if (bytes.size < HEADER) return FoxResult.Err("A Headbutt file is at least $HEADER bytes, got ${bytes.size}")
            val normalCount = bytes[0].toInt() and 0xFF
            val specialCount = bytes[2].toInt() and 0xFF
            val padding = (bytes[1].toInt() and 0xFF) to (bytes[3].toInt() and 0xFF)
            if (normalCount == 0 && specialCount == 0 && bytes.size == HEADER) return FoxResult.Ok(EMPTY.copy(headerPadding = padding))
            val expected = HEADER + 4 * (NORMAL_SLOTS + SPECIAL_SLOTS) + GROUP_BYTES * (normalCount + specialCount)
            if (bytes.size != expected) return FoxResult.Err("A Headbutt file with $normalCount normal and $specialCount special groups is $expected bytes, got ${bytes.size}")
            var at = HEADER
            val encounters = List(NORMAL_SLOTS + SPECIAL_SLOTS) {
                HeadbuttEncounter(bytes.u16(at), bytes[at + 2].toInt() and 0xFF, bytes[at + 3].toInt() and 0xFF).also { at += 4 }
            }
            fun groups(count: Int) = List(count) {
                HeadbuttTreeGroup(List(HeadbuttTreeGroup.SIZE) { HeadbuttTree(bytes.u16(at), bytes.u16(at + 2)).also { at += 4 } })
            }
            val normalGroups = groups(normalCount)
            val specialGroups = groups(specialCount)
            return FoxResult.Ok(HeadbuttFile(encounters.subList(0, NORMAL_SLOTS), encounters.subList(NORMAL_SLOTS, encounters.size), normalGroups, specialGroups, headerPadding = padding))
        }

        val SPEC = RecordSpec(HgssLayout.HEADBUTT, ::parse, HeadbuttFile::toBytes)
    }
}
