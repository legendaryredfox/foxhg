package io.github.legendaryredfox.foxhg.core.data.hpbar

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/**
 * How fast the battle HP bar moves (wiki guide "Changing the HP bar speed"). In overlay 12 the Thumb instruction `LSLS R0, R0, #n` at
 * [OFFSET] scales the bar's step: the step is 2^[shift], 8 in the game, so the speed is 2^(shift - 8) times the normal speed (shift 9 is
 * twice as fast, shift 6 a quarter). The instruction is two bytes (`00 02` for #8). The guide's fine control through the synthetic overlay is not used.
 */
data class HpBarSpeed(val shift: Int) {
    init { require(shift in MIN_SHIFT..MAX_SHIFT) { "The shift is $MIN_SHIFT to $MAX_SHIFT" } }

    /** The speed as a multiple of the game's: 0.25, 0.5, 1, 2, 4, 8 ... */
    val multiplier: Double get() = if (shift >= NORMAL_SHIFT) (1 shl (shift - NORMAL_SHIFT)).toDouble() else 1.0 / (1 shl (NORMAL_SHIFT - shift))

    /** The instruction word: `LSLS R0, R0, #shift` is `shift << 6`. */
    val instruction: Int get() = shift shl 6

    companion object {
        const val OVERLAY = 12
        const val OFFSET = 0x2E17A
        const val NORMAL_SHIFT = 8
        const val MIN_SHIFT = 1
        const val MAX_SHIFT = 15
        val VANILLA = HpBarSpeed(NORMAL_SHIFT)

        /** True when [word] is `LSLS R0, R0, #n` with n from [MIN_SHIFT] to [MAX_SHIFT]. */
        private fun isShift(word: Int): Boolean = word and 0xF83F == 0 && (word shr 6) in MIN_SHIFT..MAX_SHIFT

        fun parse(overlay: ByteArray): FoxResult<HpBarSpeed> {
            if (overlay.size < OFFSET + 2) return FoxResult.Err("Overlay $OVERLAY is too short for the HP bar speed")
            val word = overlay.u16(OFFSET)
            if (!isShift(word)) return FoxResult.Err("The HP bar speed instruction at 0x${OFFSET.toString(16).uppercase()} of overlay $OVERLAY is not LSLS R0, R0, #n (it is 0x${word.toString(16).uppercase().padStart(4, '0')}); another tool changed it")
            return FoxResult.Ok(HpBarSpeed(word shr 6))
        }

        fun load(project: RomProject): FoxResult<HpBarSpeed> = when (val o = project.readOverlay(OVERLAY)) {
            is FoxResult.Ok -> parse(o.value)
            is FoxResult.Err -> o
        }

        fun save(project: RomProject, speed: HpBarSpeed): FoxResult<Unit> {
            val overlay = when (val o = project.readOverlay(OVERLAY)) {
                is FoxResult.Ok -> o.value
                is FoxResult.Err -> return o
            }
            if (parse(overlay) is FoxResult.Err) return parse(overlay) as FoxResult.Err
            overlay.putU16(OFFSET, speed.instruction)
            return project.writeOverlay(OVERLAY, overlay)
        }
    }
}
