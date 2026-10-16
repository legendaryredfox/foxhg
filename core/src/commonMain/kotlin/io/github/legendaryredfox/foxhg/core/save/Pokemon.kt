package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.text.Charmap
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

/**
 * One Pokemon as the save keeps it (`Pokemon`, 0xEC bytes; the decomp's `pokemon_types_def.h`): the personality value, a flags word, the checksum, four
 * 32 byte blocks (A: species, item, ID, experience, EVs, contest stats; B: moves, PP, IVs, form; C: nickname; D: original trainer, dates, ball) and
 * 100 bytes of party-only data (level, HP and stats). The blocks are encrypted with a linear congruential stream seeded by the checksum and stored in
 * an order that depends on the personality value; the party data is encrypted with the personality value as the seed. [blocks] holds the four blocks
 * decrypted and in the order A, B, C, D (128 bytes); [party] the 100 bytes decrypted. Checked on the party of a real save (the checksums match and the
 * species, levels and moves are those the game gave) and on the empty slots, which decrypt to zeros.
 */
class Pokemon private constructor(val personality: Long, val flags: Int, val blocks: ByteArray, val party: ByteArray) {
    init {
        require(blocks.size == BLOCKS_SIZE) { "the blocks are $BLOCKS_SIZE bytes" }
        require(party.size == PARTY_SIZE) { "the party data is $PARTY_SIZE bytes" }
    }

    // --- block A
    val species: Int get() = blocks.u16(0)
    val heldItem: Int get() = blocks.u16(2)
    val otId: Long get() = blocks.u32(4)
    val experience: Long get() = blocks.u32(8)
    val friendship: Int get() = blocks.u8(12)
    val ability: Int get() = blocks.u8(13)
    val markings: Int get() = blocks.u8(14)
    val originLanguage: Int get() = blocks.u8(15)

    /** Effort values in the order HP, Attack, Defense, Speed, Sp. Atk, Sp. Def. */
    val evs: List<Int> get() = List(6) { blocks.u8(0x10 + it) }

    // --- block B
    val moves: List<Int> get() = List(4) { blocks.u16(0x20 + 2 * it) }
    val pp: List<Int> get() = List(4) { blocks.u8(0x28 + it) }
    val ppUps: List<Int> get() = List(4) { blocks.u8(0x2C + it) }
    private val ivWord: Long get() = blocks.u32(0x30)

    /** Individual values in the order HP, Attack, Defense, Speed, Sp. Atk, Sp. Def (5 bits each). */
    val ivs: List<Int> get() = List(6) { ((ivWord ushr (5 * it)) and 31).toInt() }
    val isEgg: Boolean get() = (ivWord ushr 30) and 1L == 1L
    val hasNickname: Boolean get() = (ivWord ushr 31) and 1L == 1L
    val form: Int get() = blocks.u8(0x38) ushr 3
    val gender: Int get() = (blocks.u8(0x38) ushr 1) and 3

    // --- block C and D
    val nickname: String get() = text(0x40, 11)
    val otName: String get() = text(0x60, 8)

    // --- party data
    val level: Int get() = party.u8(4)
    val currentHp: Int get() = party.u16(6)
    val maxHp: Int get() = party.u16(8)

    /** Attack, Defense, Speed, Sp. Atk and Sp. Def as the party data keeps them. */
    val stats: List<Int> get() = List(5) { party.u16(10 + 2 * it) }

    val isEmpty: Boolean get() = species == 0

    /** The nature, which the personality value decides: 0 to 24, Hardy first. */
    val nature: Int get() = (personality % 25).toInt()

    private fun text(at: Int, length: Int): String {
        val codes = IntArray(length) { blocks.u16(at + 2 * it) }
        return Charmap.HGSS.decode(codes.takeWhile { it != 0xFFFF }.toIntArray())
    }

    private fun withBlocks(change: (ByteArray) -> Unit): Pokemon = Pokemon(personality, flags, blocks.copyOf().also(change), party)
    private fun withParty(change: (ByteArray) -> Unit): Pokemon = Pokemon(personality, flags, blocks, party.copyOf().also(change))

    fun withSpecies(value: Int) = withBlocks { it.putU16(0, value) }
    fun withHeldItem(value: Int) = withBlocks { it.putU16(2, value) }
    fun withExperience(value: Long) = withBlocks { it.putU32(8, value) }
    fun withFriendship(value: Int) = withBlocks { it[12] = value.toByte() }
    fun withMove(slot: Int, move: Int, pp: Int) = withBlocks { it.putU16(0x20 + 2 * slot, move); it[0x28 + slot] = pp.toByte() }
    fun withEv(stat: Int, value: Int) = withBlocks { it[0x10 + stat] = value.toByte() }
    fun withIv(stat: Int, value: Int) = withBlocks { b ->
        val shift = 5 * stat
        b.putU32(0x30, (b.u32(0x30) and (31L shl shift).inv()) or ((value.toLong() and 31) shl shift))
    }
    fun withLevel(value: Int) = withParty { it[4] = value.toByte() }
    fun withHp(current: Int, max: Int) = withParty { it.putU16(6, current); it.putU16(8, max) }
    fun withStats(values: List<Int>) = withParty { p -> values.forEachIndexed { i, v -> p.putU16(10 + 2 * i, v) } }

    /**
     * The stats worked out from [base] (HP, Attack, Defense, Speed, Sp. Atk, Sp. Def of the species) with the level, IVs, EVs and nature, as the game
     * computes them. Current HP rises by what the maximum rose, as on a level up, and never passes the maximum. Changing the level or the IVs does not
     * do this by itself: the game keeps the stored stats until the next level up or box visit.
     */
    fun recalculated(base: List<Int>): Pokemon {
        require(base.size == 6) { "a species has 6 base stats" }
        val l = level
        fun core(i: Int) = (2 * base[i] + ivs[i] + evs[i] / 4) * l / 100
        val maxHp = core(0) + l + 10
        // stored order after HP: Attack, Defense, Speed, Sp. Atk, Sp. Def
        val raised = nature / 5
        val lowered = nature % 5
        val others = List(5) { k ->
            val plain = core(k + 1) + 5
            when {
                raised == lowered -> plain
                k == raised -> plain * 110 / 100
                k == lowered -> plain * 90 / 100
                else -> plain
            }
        }
        val hp = (currentHp + maxOf(0, maxHp - this.maxHp)).coerceIn(0, maxHp)
        return withHp(hp, maxHp).withStats(others)
    }

    /** A nickname of up to 10 characters (the game's text table), or none: [text] empty clears the nickname flag. The original name field is left as the game keeps it. */
    fun withNickname(text: String): FoxResult<Pokemon> {
        val codes = if (text.isEmpty()) IntArray(0) else when (val r = Charmap.HGSS.encode(text)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        if (codes.size > 10) return FoxResult.Err("A nickname has at most 10 characters")
        return FoxResult.Ok(
            withBlocks { b ->
                for (i in 0 until 11) b.putU16(0x40 + 2 * i, if (i < codes.size) codes[i] else if (i == codes.size) 0xFFFF else 0xFFFF)
                val word = b.u32(0x30)
                b.putU32(0x30, if (text.isEmpty()) word and (1L shl 31).inv() else word or (1L shl 31))
            },
        )
    }

    /** The 0xEC bytes the save holds: the blocks shuffled and encrypted with the new checksum, the party data encrypted with the personality value. */
    fun toBytes(): ByteArray {
        val out = ByteArray(SIZE)
        val checksum = checksumOf(blocks)
        out.putU32(0, personality); out.putU16(4, flags); out.putU16(6, checksum)
        val order = SHUFFLE[shuffleIndex(personality)]
        val shuffled = ByteArray(BLOCKS_SIZE)
        for (position in 0 until 4) blocks.copyInto(shuffled, position * BLOCK, order[position] * BLOCK, order[position] * BLOCK + BLOCK)
        crypt(shuffled, checksum.toLong()).copyInto(out, 8)
        crypt(party, personality).copyInto(out, 8 + BLOCKS_SIZE)
        return out
    }

    override fun equals(other: Any?) = other is Pokemon && personality == other.personality && flags == other.flags && blocks.contentEquals(other.blocks) && party.contentEquals(other.party)
    override fun hashCode() = (personality.toInt() * 31 + blocks.contentHashCode()) * 31 + party.contentHashCode()

    companion object {
        const val SIZE = 0xEC
        const val BLOCK = 32
        const val BLOCKS_SIZE = 128
        const val PARTY_SIZE = 100

        /** A slot with nothing in it, as the game clears it (all zeros, encrypted the same way). */
        val EMPTY: Pokemon = Pokemon(0, 0, ByteArray(BLOCKS_SIZE), ByteArray(PARTY_SIZE))

        /** For each of the 24 personality value shuffles, which block (0 = A ... 3 = D) sits at each of the four places. */
        private val SHUFFLE: List<IntArray> = listOf(
            "ABCD", "ABDC", "ACBD", "ACDB", "ADBC", "ADCB", "BACD", "BADC", "BCAD", "BCDA", "BDAC", "BDCA",
            "CABD", "CADB", "CBAD", "CBDA", "CDAB", "CDBA", "DABC", "DACB", "DBAC", "DBCA", "DCAB", "DCBA",
        ).map { order -> IntArray(4) { order[it] - 'A' } }

        private fun shuffleIndex(personality: Long): Int = (((personality ushr 13) and 31) % 24).toInt()

        /** The checksum: the sum of the 64 16-bit words of the four blocks, in 16 bits. */
        fun checksumOf(blocks: ByteArray): Int {
            var sum = 0
            for (i in 0 until BLOCKS_SIZE step 2) sum += blocks.u16(i)
            return sum and 0xFFFF
        }

        /** The game's stream cipher: each 16-bit word is XORed with the high half of the next value of `seed * 0x41C64E6D + 0x6073`. The same call encrypts and decrypts. */
        private fun crypt(data: ByteArray, startSeed: Long): ByteArray {
            var seed = startSeed and 0xFFFFFFFFL
            val out = ByteArray(data.size)
            for (i in data.indices step 2) {
                seed = (seed * 0x41C64E6DL + 0x6073L) and 0xFFFFFFFFL
                out.putU16(i, data.u16(i) xor ((seed ushr 16).toInt() and 0xFFFF))
            }
            return out
        }

        /** Reads the 0xEC bytes of a party slot; an error when the checksum does not fit (a damaged or hand-edited slot). */
        fun parse(bytes: ByteArray): FoxResult<Pokemon> {
            if (bytes.size != SIZE) return FoxResult.Err("A Pokémon is $SIZE bytes, got ${bytes.size}")
            val personality = bytes.u32(0)
            val flags = bytes.u16(4)
            val checksum = bytes.u16(6)
            val stored = crypt(bytes.copyOfRange(8, 8 + BLOCKS_SIZE), checksum.toLong())
            val order = SHUFFLE[shuffleIndex(personality)]
            val blocks = ByteArray(BLOCKS_SIZE)
            for (position in 0 until 4) stored.copyInto(blocks, order[position] * BLOCK, position * BLOCK, position * BLOCK + BLOCK)
            if (checksumOf(blocks) != checksum) return FoxResult.Err("The checksum of this Pokémon does not fit its data")
            return FoxResult.Ok(Pokemon(personality, flags, blocks, crypt(bytes.copyOfRange(8 + BLOCKS_SIZE, SIZE), personality)))
        }
    }
}
