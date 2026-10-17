package io.github.legendaryredfox.foxhg.core.data.roamer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** One roaming legendary: the species and level the game gives it when `CreateRoamer` starts it. */
data class Roamer(val species: Int, val level: Int)

/**
 * The four roaming Pokémon of HeartGold and SoulSilver (Raikou, Entei, Latias, Latios in vanilla). `CreateRoamer` in the
 * scripts only picks which of the four starts; the species and level are in the code of `Save_CreateRoamerByID`, in the
 * ARM9 (a `switch` of four cases, each setting a register to the species and another to the level). The function is
 * found by its bytes, as [StarterPlaces][io.github.legendaryredfox.foxhg.core.data.starter.Starters] does for the starters.
 *
 * A species is an 8-bit operand in the first two cases, so Raikou's and Entei's slots take species 1 to 255. Latias' case
 * loads a byte and shifts it left, so it takes any species that is a byte times a power of two (the vanilla 380 is 95 << 2).
 * Latios' case loads a whole word from the literal pool, so it takes any species. [canHold] says which.
 */
data class Roamers(val roamers: List<Roamer>) {
    init { require(roamers.size == COUNT) { "There are exactly $COUNT roamers" } }

    /** Writes the roamers into a copy of [arm9]; an error says which one the code of its case can not hold. */
    fun applyTo(arm9: ByteArray): FoxResult<ByteArray> {
        val at = RoamerCode.find(arm9) ?: return FoxResult.Err("The roamer code was not found in the ARM9 (it may be changed by another tool)")
        for ((i, r) in roamers.withIndex()) {
            if (r.level !in 1..100) return FoxResult.Err("${NAMES[i]}'s level must be 1 to 100")
            if (!canHold(i, r.species)) return FoxResult.Err("${NAMES[i]}'s place in the code can not hold species ${r.species}: ${LIMITS[i]}")
        }
        val out = arm9.copyOf()
        out[at + RAIKOU_SPECIES] = roamers[0].species.toByte(); out[at + RAIKOU_LEVEL] = roamers[0].level.toByte()
        out[at + ENTEI_SPECIES] = roamers[1].species.toByte(); out[at + ENTEI_LEVEL] = roamers[1].level.toByte()
        val (imm, shift) = byteAndShift(roamers[2].species)!!
        out[at + LATIAS_SPECIES] = imm.toByte()
        out[at + LATIAS_SHIFT] = ((shift and 3 shl 6) or 0x36).toByte(); out[at + LATIAS_SHIFT + 1] = (shift shr 2).toByte()
        out[at + LATIAS_LEVEL] = roamers[2].level.toByte()
        out[at + LATIOS_LEVEL] = roamers[3].level.toByte()
        val literal = RoamerCode.literalOffset(arm9, at)!!
        for (b in 0 until 4) out[literal + b] = (roamers[3].species shr (8 * b)).toByte()
        return FoxResult.Ok(out)
    }

    companion object {
        const val COUNT = 4
        val NAMES = listOf("Raikou", "Entei", "Latias", "Latios")
        val LIMITS = listOf("1 to 255", "1 to 255", "a byte times a power of two (for example 380 = 95 x 4)", "1 to 493")
        val VANILLA = Roamers(listOf(Roamer(243, 40), Roamer(244, 40), Roamer(380, 35), Roamer(381, 35)))
        const val MAX_SPECIES = 493

        // offsets from the start of the four cases, each `movs rS, #species` (low byte first) then `movs rL, #level`
        private const val RAIKOU_SPECIES = 0
        private const val RAIKOU_LEVEL = 2
        private const val ENTEI_SPECIES = 6
        private const val ENTEI_LEVEL = 8
        private const val LATIAS_SPECIES = 12
        private const val LATIAS_SHIFT = 14
        private const val LATIAS_LEVEL = 16
        private const val LATIOS_LEVEL = 22

        /** The species a `movs`, `lsls` pair makes: a byte of 1 to 255 shifted left by 0 to 8, the largest shift first (so the vanilla 380 is 95 << 2, as the game has it). */
        internal fun byteAndShift(species: Int): Pair<Int, Int>? =
            (8 downTo 0).firstNotNullOfOrNull { s -> if (species > 0 && species and ((1 shl s) - 1) == 0 && (species shr s) in 1..255) (species shr s) to s else null }

        fun canHold(roamer: Int, species: Int): Boolean = when (roamer) {
            0, 1 -> species in 1..255
            2 -> species <= MAX_SPECIES && byteAndShift(species) != null
            else -> species in 1..MAX_SPECIES
        }

        fun parse(arm9: ByteArray): FoxResult<Roamers> {
            val at = RoamerCode.find(arm9) ?: return FoxResult.Err("The roamer code was not found in the ARM9 (it may be changed by another tool)")
            val literal = RoamerCode.literalOffset(arm9, at) ?: return FoxResult.Err("The roamer code's species word is outside the ARM9")
            fun u8(i: Int) = arm9[i].toInt() and 0xFF
            val shift = (u8(at + LATIAS_SHIFT) shr 6) or (u8(at + LATIAS_SHIFT + 1) shl 2)
            val latios = (0 until 4).fold(0) { acc, b -> acc or (u8(literal + b) shl (8 * b)) }
            return FoxResult.Ok(
                Roamers(
                    listOf(
                        Roamer(u8(at + RAIKOU_SPECIES), u8(at + RAIKOU_LEVEL)),
                        Roamer(u8(at + ENTEI_SPECIES), u8(at + ENTEI_LEVEL)),
                        Roamer(u8(at + LATIAS_SPECIES) shl shift, u8(at + LATIAS_LEVEL)),
                        Roamer(latios, u8(at + LATIOS_LEVEL)),
                    ),
                ),
            )
        }

        fun load(project: RomProject): FoxResult<Roamers> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> parse(a.value)
            is FoxResult.Err -> a
        }

        fun save(project: RomProject, roamers: Roamers): FoxResult<Unit> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> when (val patched = roamers.applyTo(a.value)) {
                is FoxResult.Ok -> project.writeArm9(patched.value)
                is FoxResult.Err -> patched
            }
            is FoxResult.Err -> a
        }
    }
}

/** Where the four cases are, found by the THUMB code around the values. */
internal object RoamerCode {
    /** The 26 bytes of the cases; null is a byte that varies (a species, level or an offset). The `lsls` word is checked apart. */
    private val PATTERN: List<Int?> = listOf(
        null, 0x26, null, 0x25, 0x0D, 0xE0,
        null, 0x26, null, 0x25, 0x0A, 0xE0,
        null, 0x26, null, null, null, 0x25, 0x06, 0xE0,
        null, 0x4E, null, 0x25, 0x03, 0xE0,
    )
    private const val LSLS = 14
    private const val LDR = 20

    fun find(arm9: ByteArray): Int? {
        var found = -1
        for (i in 0..arm9.size - PATTERN.size) {
            if (matches(arm9, i)) { if (found >= 0) return null; found = i }
        }
        return found.takeIf { it >= 0 }
    }

    private fun matches(d: ByteArray, at: Int): Boolean {
        for (i in PATTERN.indices) {
            val want = PATTERN[i] ?: continue
            if ((d[at + i].toInt() and 0xFF) != want) return false
        }
        // `lsls r6, r6, #k` is 0x0036 with the shift in bits 6 to 10
        return (d[at + LSLS].toInt() and 0x3F) == 0x36 && (d[at + LSLS + 1].toInt() and 0xF8) == 0
    }

    /** The literal pool word `ldr r6, [pc, #n]` of the last case reads (pc is the instruction's address plus 4, rounded down to a word). */
    fun literalOffset(arm9: ByteArray, at: Int): Int? {
        val ldr = at + LDR
        val target = ((ldr + 4) and 3.inv()) + (arm9[ldr].toInt() and 0xFF) * 4
        return target.takeIf { it + 4 <= arm9.size }
    }
}
