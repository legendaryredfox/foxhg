package io.github.legendaryredfox.foxhg.core.data.map

/** The tile type byte of the map permissions (the game's tile behaviour), from the decomp's enum. */
object TileTypes {
    class Info(val id: Int, val name: String?, val flags: Int) {
        val surfable: Boolean get() = flags and 1 != 0
        val encounters: Boolean get() = flags and 2 != 0
        val label: String get() = name?.let { pretty(it) } ?: "Type $id"
    }

    val all: List<Info> by lazy {
        TILE_TYPE_DATA.split('\n').map { line ->
            val (id, name, flags) = line.split('|')
            Info(id.toInt(), name.ifEmpty { null }, flags.toInt())
        }
    }

    fun info(id: Int): Info = all.getOrNull(id) ?: Info(id, null, 0)

    /** `WARP_ENTRANCE_EAST` -> `Warp entrance east`; the decomp's `1_UNUSED` -> `Unused`. */
    private fun pretty(name: String): String =
        name.removePrefix("1_").lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Collision byte: bit 7 blocks the tile, the other bits are further flags the game reads. */
object Collision {
    const val BLOCKED = 0x80
    fun isBlocked(value: Int) = value and BLOCKED != 0
}
