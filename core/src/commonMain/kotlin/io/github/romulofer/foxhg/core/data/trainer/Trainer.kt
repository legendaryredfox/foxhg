package io.github.romulofer.foxhg.core.data.trainer

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.putU32
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32
import io.github.romulofer.foxhg.core.u8

/**
 * Trainer header, 20 bytes (`a/0/5/5`). [type] bit 0 means the party lists custom moves, bit 1 means held items;
 * that decides the size of every party record. Layout from the HeartGold decomp's `TrainerData`.
 */
data class TrainerHeader(
    val type: Int,
    val trainerClass: Int,
    val unknown2: Int,
    val partySize: Int,
    val items: List<Int>,
    val aiFlags: Long,
    val doubleBattle: Long,
) {
    val hasCustomMoves: Boolean get() = type and CUSTOM_MOVES != 0
    val hasHeldItems: Boolean get() = type and HELD_ITEMS != 0

    fun withFlags(customMoves: Boolean, heldItems: Boolean): TrainerHeader =
        copy(type = (type and (CUSTOM_MOVES or HELD_ITEMS).inv()) or (if (customMoves) CUSTOM_MOVES else 0) or (if (heldItems) HELD_ITEMS else 0))

    fun toBytes(): ByteArray {
        val b = ByteArray(SIZE)
        b[0] = type.toByte(); b[1] = trainerClass.toByte(); b[2] = unknown2.toByte(); b[3] = partySize.toByte()
        for (i in 0 until 4) b.putU16(4 + 2 * i, items.getOrElse(i) { 0 })
        b.putU32(12, aiFlags); b.putU32(16, doubleBattle)
        return b
    }

    companion object {
        const val SIZE = 20
        const val CUSTOM_MOVES = 1
        const val HELD_ITEMS = 2

        fun parse(bytes: ByteArray): FoxResult<TrainerHeader> {
            if (bytes.size != SIZE) return FoxResult.Err("Trainer header must be $SIZE bytes, got ${bytes.size}")
            return FoxResult.Ok(
                TrainerHeader(
                    type = bytes.u8(0), trainerClass = bytes.u8(1), unknown2 = bytes.u8(2), partySize = bytes.u8(3),
                    items = List(4) { bytes.u16(4 + 2 * it) }, aiFlags = bytes.u32(12), doubleBattle = bytes.u32(16),
                ),
            )
        }
    }
}

/**
 * One Pokémon of a trainer's party (`a/0/5/6`). The stored record is 8 bytes (difficulty, gender/ability override,
 * level, species+form, capsule), plus 2 for a held item and 8 for four moves depending on the trainer's type.
 * [item] and [moves] always exist in memory (zeros when the trainer's type does not store them).
 */
data class TrainerMon(
    /** IV scale: IVs become `difficulty * 31 / 255`. */
    val difficulty: Int,
    /** Bits 0..3: 0 none, 1 male, 2 female. Bits 4..7: 0 none, 1 ability 1, 2 ability 2. */
    val genderAbility: Int,
    val level: Int,
    /** Bits 0..9 species, bits 10..15 form. */
    val speciesAndForm: Int,
    val item: Int = 0,
    val moves: List<Int> = listOf(0, 0, 0, 0),
    val capsule: Int = 0,
) {
    val species: Int get() = speciesAndForm and 0x3FF
    val form: Int get() = speciesAndForm ushr 10

    fun withSpecies(species: Int): TrainerMon = copy(speciesAndForm = (speciesAndForm and 0x3FF.inv()) or (species and 0x3FF))

    fun toBytes(customMoves: Boolean, heldItem: Boolean): ByteArray {
        val b = ByteArray(recordSize(customMoves, heldItem))
        b[0] = difficulty.toByte(); b[1] = genderAbility.toByte()
        b.putU16(2, level); b.putU16(4, speciesAndForm)
        var p = 6
        if (heldItem) { b.putU16(p, item); p += 2 }
        if (customMoves) { for (i in 0 until 4) b.putU16(p + 2 * i, moves.getOrElse(i) { 0 }); p += 8 }
        b.putU16(p, capsule)
        return b
    }

    companion object {
        fun recordSize(customMoves: Boolean, heldItem: Boolean) = 8 + (if (heldItem) 2 else 0) + (if (customMoves) 8 else 0)

        fun parse(bytes: ByteArray, offset: Int, customMoves: Boolean, heldItem: Boolean): TrainerMon {
            var p = offset + 6
            val item = if (heldItem) bytes.u16(p).also { p += 2 } else 0
            val moves = if (customMoves) List(4) { bytes.u16(p + 2 * it) }.also { p += 8 } else listOf(0, 0, 0, 0)
            return TrainerMon(
                difficulty = bytes.u8(offset), genderAbility = bytes.u8(offset + 1), level = bytes.u16(offset + 2),
                speciesAndForm = bytes.u16(offset + 4), item = item, moves = moves, capsule = bytes.u16(p),
            )
        }
    }
}

/**
 * A trainer and its party. Vanilla has a few records whose party file is longer than the header says (the dummy
 * trainer 0 has 8 spare bytes); [trailing] keeps them so those records still round-trip.
 */
data class Trainer(val header: TrainerHeader, val party: List<TrainerMon>, val trailing: ByteArray = ByteArray(0)) {
    init {
        require(header.partySize == party.size) { "header says ${header.partySize} Pokémon, party has ${party.size}" }
    }

    fun partyBytes(): ByteArray {
        val size = TrainerMon.recordSize(header.hasCustomMoves, header.hasHeldItems)
        val out = ByteArray(size * party.size + trailing.size)
        trailing.copyInto(out, size * party.size)
        party.forEachIndexed { i, mon -> mon.toBytes(header.hasCustomMoves, header.hasHeldItems).copyInto(out, i * size) }
        return out
    }

    fun withParty(newParty: List<TrainerMon>): Trainer = Trainer(header.copy(partySize = newParty.size), newParty, trailing)

    fun withHeader(newHeader: TrainerHeader): Trainer = Trainer(newHeader.copy(partySize = party.size), party, trailing)

    override fun equals(other: Any?) = other is Trainer && header == other.header && party == other.party && trailing.contentEquals(other.trailing)

    override fun hashCode() = (header.hashCode() * 31 + party.hashCode()) * 31 + trailing.contentHashCode()

    companion object {
        const val MAX_PARTY = 6

        fun parse(headerBytes: ByteArray, partyBytes: ByteArray): FoxResult<Trainer> {
            val header = when (val h = TrainerHeader.parse(headerBytes)) {
                is FoxResult.Ok -> h.value
                is FoxResult.Err -> return h
            }
            val size = TrainerMon.recordSize(header.hasCustomMoves, header.hasHeldItems)
            val expected = size * header.partySize
            if (partyBytes.size < expected) {
                return FoxResult.Err("Party has ${partyBytes.size} bytes, expected ${header.partySize} x $size")
            }
            return FoxResult.Ok(
                Trainer(
                    header,
                    List(header.partySize) { TrainerMon.parse(partyBytes, it * size, header.hasCustomMoves, header.hasHeldItems) },
                    partyBytes.copyOfRange(expected, partyBytes.size),
                ),
            )
        }
    }
}
