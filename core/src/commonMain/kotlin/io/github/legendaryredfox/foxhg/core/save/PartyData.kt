package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u32

/**
 * The `PARTY` chunk (0x5B0 bytes): the party's capacity (6), how many of the [slots] are used, six 0xEC byte Pokemon, and 0x20 bytes ([extra]) the game
 * keeps after them. From the decomp's `PartyCore` and `Party`; a real save with three Pokemon and one with none were read.
 */
data class PartyData(val maxCount: Int, val count: Int, val slots: List<Pokemon>, val extra: ByteArray) {
    init {
        require(slots.size == SLOTS) { "a party has $SLOTS slots" }
        require(count in 0..SLOTS) { "a party holds 0 to $SLOTS Pokémon" }
    }

    /** The Pokemon in the party, in order. */
    val members: List<Pokemon> get() = slots.take(count)

    /** A copy with [mon] in [slot] (0 to count - 1, or [count] to add one at the end). */
    fun with(slot: Int, mon: Pokemon): PartyData {
        require(slot in 0..count && slot < SLOTS) { "slot $slot is not in the party" }
        return copy(count = maxOf(count, slot + 1), slots = slots.toMutableList().also { it[slot] = mon })
    }

    /** A copy without the Pokemon in [slot]; the ones after it move up and the last slot is cleared. */
    fun without(slot: Int): PartyData {
        require(slot in 0 until count) { "slot $slot is not in the party" }
        val kept = members.filterIndexed { i, _ -> i != slot }
        return copy(count = count - 1, slots = List(SLOTS) { kept.getOrNull(it) ?: Pokemon.EMPTY })
    }

    fun toBytes(): ByteArray = ByteArray(SIZE).also { b ->
        b.putU32(0, maxCount.toLong()); b.putU32(4, count.toLong())
        slots.forEachIndexed { i, m -> m.toBytes().copyInto(b, 8 + i * Pokemon.SIZE) }
        extra.copyInto(b, 8 + SLOTS * Pokemon.SIZE)
    }

    override fun equals(other: Any?) = other is PartyData && maxCount == other.maxCount && count == other.count && slots == other.slots && extra.contentEquals(other.extra)
    override fun hashCode() = ((maxCount * 31 + count) * 31 + slots.hashCode()) * 31 + extra.contentHashCode()

    companion object {
        const val SLOTS = 6
        const val SIZE = 0x5B0
        const val EXTRA = SIZE - 8 - SLOTS * Pokemon.SIZE

        fun parse(bytes: ByteArray): FoxResult<PartyData> {
            if (bytes.size != SIZE) return FoxResult.Err("The party is $SIZE bytes, got ${bytes.size}")
            val max = bytes.u32(0).toInt()
            val count = bytes.u32(4).toInt()
            if (count !in 0..SLOTS) return FoxResult.Err("The party says it holds $count Pokémon")
            val slots = ArrayList<Pokemon>(SLOTS)
            for (i in 0 until SLOTS) {
                when (val m = Pokemon.parse(bytes.copyOfRange(8 + i * Pokemon.SIZE, 8 + (i + 1) * Pokemon.SIZE))) {
                    is FoxResult.Ok -> slots += m.value
                    is FoxResult.Err -> return FoxResult.Err("Party slot ${i + 1}: ${m.message}")
                }
            }
            return FoxResult.Ok(PartyData(max, count, slots, bytes.copyOfRange(8 + SLOTS * Pokemon.SIZE, SIZE)))
        }

        fun read(save: SaveFile): FoxResult<PartyData> {
            val block = save.current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
            return parse(save.chunk(block, SaveChunks.byId(SaveChunks.PARTY)))
        }
    }
}

/** A copy of the save with [party] written into the current general block (checksums recomputed). */
fun SaveFile.withParty(party: PartyData): FoxResult<SaveFile> {
    val block = current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
    return FoxResult.Ok(withChunk(block, SaveChunks.byId(SaveChunks.PARTY), party.toBytes()))
}
