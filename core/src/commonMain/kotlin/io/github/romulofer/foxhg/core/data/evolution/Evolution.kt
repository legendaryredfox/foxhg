package io.github.romulofer.foxhg.core.data.evolution

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.RecordSpec
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.u16

/** What an [EvolutionEntry.param] means for a method. */
enum class ParamKind { NONE, LEVEL, ITEM, MOVE, SPECIES, FRIENDSHIP, BEAUTY }

/** Evolution methods of Gen IV (ids are what the game stores). */
enum class EvolutionMethod(val id: Int, val label: String, val param: ParamKind) {
    NONE(0, "None", ParamKind.NONE),
    FRIENDSHIP(1, "Friendship", ParamKind.FRIENDSHIP),
    FRIENDSHIP_DAY(2, "Friendship (day)", ParamKind.FRIENDSHIP),
    FRIENDSHIP_NIGHT(3, "Friendship (night)", ParamKind.FRIENDSHIP),
    LEVEL(4, "Level up", ParamKind.LEVEL),
    TRADE(5, "Trade", ParamKind.NONE),
    TRADE_ITEM(6, "Trade holding item", ParamKind.ITEM),
    STONE(7, "Use item", ParamKind.ITEM),
    LEVEL_ATK_GT_DEF(8, "Level, Atk > Def", ParamKind.LEVEL),
    LEVEL_ATK_EQ_DEF(9, "Level, Atk = Def", ParamKind.LEVEL),
    LEVEL_ATK_LT_DEF(10, "Level, Atk < Def", ParamKind.LEVEL),
    LEVEL_PID_LOW(11, "Level, personality low", ParamKind.LEVEL),
    LEVEL_PID_HIGH(12, "Level, personality high", ParamKind.LEVEL),
    LEVEL_NINJASK(13, "Level (Ninjask)", ParamKind.LEVEL),
    LEVEL_SHEDINJA(14, "Level (Shedinja)", ParamKind.LEVEL),
    BEAUTY(15, "Beauty", ParamKind.BEAUTY),
    STONE_MALE(16, "Use item (male)", ParamKind.ITEM),
    STONE_FEMALE(17, "Use item (female)", ParamKind.ITEM),
    ITEM_DAY(18, "Hold item (day)", ParamKind.ITEM),
    ITEM_NIGHT(19, "Hold item (night)", ParamKind.ITEM),
    HAS_MOVE(20, "Knows move", ParamKind.MOVE),
    OTHER_PARTY_MON(21, "Species in party", ParamKind.SPECIES),
    LEVEL_MALE(22, "Level (male)", ParamKind.LEVEL),
    LEVEL_FEMALE(23, "Level (female)", ParamKind.LEVEL),
    CORONET(24, "Level up at Mt. Coronet", ParamKind.NONE),
    ETERNA(25, "Level up at Eterna Forest", ParamKind.NONE),
    ROUTE_217(26, "Level up at Route 217", ParamKind.NONE);

    val isTrade: Boolean get() = this == TRADE || this == TRADE_ITEM

    companion object {
        fun fromId(id: Int): EvolutionMethod? = entries.firstOrNull { it.id == id }
    }
}

data class EvolutionEntry(val method: Int, val param: Int, val target: Int) {
    val known: EvolutionMethod? get() = EvolutionMethod.fromId(method)
    val isEmpty: Boolean get() = method == 0 && param == 0 && target == 0
}

/**
 * Evolutions of one species (`a/0/3/4`): [SLOTS] entries of `u16 method, u16 param, u16 target`. Bytes after the
 * last slot are kept in [trailing] so records round-trip.
 */
data class EvolutionData(val slots: List<EvolutionEntry>, val trailing: ByteArray = ByteArray(0)) {
    fun toBytes(): ByteArray {
        val out = ByteArray(SLOTS * ENTRY_SIZE + trailing.size)
        for (i in 0 until SLOTS) {
            val e = slots.getOrElse(i) { EvolutionEntry(0, 0, 0) }
            out.putU16(i * ENTRY_SIZE, e.method)
            out.putU16(i * ENTRY_SIZE + 2, e.param)
            out.putU16(i * ENTRY_SIZE + 4, e.target)
        }
        trailing.copyInto(out, SLOTS * ENTRY_SIZE)
        return out
    }

    fun with(slot: Int, entry: EvolutionEntry): EvolutionData =
        copy(slots = slots.toMutableList().also { it[slot] = entry })

    override fun equals(other: Any?) = other is EvolutionData && slots == other.slots && trailing.contentEquals(other.trailing)

    override fun hashCode() = slots.hashCode() * 31 + trailing.contentHashCode()

    companion object {
        const val SLOTS = 7
        const val ENTRY_SIZE = 6

        fun parse(bytes: ByteArray): FoxResult<EvolutionData> {
            if (bytes.size < SLOTS * ENTRY_SIZE) return FoxResult.Err("Evolution record needs ${SLOTS * ENTRY_SIZE} bytes, got ${bytes.size}")
            val slots = List(SLOTS) { EvolutionEntry(bytes.u16(it * ENTRY_SIZE), bytes.u16(it * ENTRY_SIZE + 2), bytes.u16(it * ENTRY_SIZE + 4)) }
            return FoxResult.Ok(EvolutionData(slots, bytes.copyOfRange(SLOTS * ENTRY_SIZE, bytes.size)))
        }

        val SPEC = RecordSpec(HgssLayout.EVOLUTIONS, ::parse, EvolutionData::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<EvolutionData>> = NarcRecords.load(project, SPEC)
    }
}
