package io.github.legendaryredfox.foxhg.core.data.typechart

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** One entry of the type table: a [attacker] type hitting a [defender] type for [multiplier] times 10 (0 none, 5 half, 20 double). */
data class TypeEntry(val attacker: Int, val defender: Int, val multiplier: Int)

/**
 * The type effectiveness table of overlay 12. It lists only the pairs that are not neutral, in two parts: [main] (always used) and
 * [foresight] (the Ghost immunities of Normal and Fighting, which Foresight, Odd-Eye and Scrappy switch off: the game stops reading at the
 * divider then). Together with the divider the entries fill [HgssLayout.TYPE_CHART_SLOTS] slots, so a new non-neutral pair needs a slot a
 * neutral one freed. From the decomp's `sTypeEffectiveness` in `overlay_12_0224E4FC.c`; checked against the ROM.
 */
data class TypeChart(val main: List<TypeEntry>, val foresight: List<TypeEntry>) {
    init { require(main.size + foresight.size + 1 <= HgssLayout.TYPE_CHART_SLOTS) { "The type table has room for ${HgssLayout.TYPE_CHART_SLOTS - 1} entries" } }

    private val byPair: Map<Pair<Int, Int>, TypeEntry> by lazy { (main + foresight).associateBy { it.attacker to it.defender } }

    /** Slots in use, the divider included. */
    val slotsUsed: Int get() = main.size + foresight.size + 1

    /** The multiplier times 10 of [attacker] against [defender]: 10 when the pair is not in the table. */
    fun multiplier(attacker: Int, defender: Int): Int = byPair[attacker to defender]?.multiplier ?: NEUTRAL

    /** Whether the pair is in the Foresight part (switched off by Foresight and the like). */
    fun isForesightPart(attacker: Int, defender: Int): Boolean = foresight.any { it.attacker == attacker && it.defender == defender }

    /** Whether [multiplier] for this pair would fit: it already has a slot, is neutral, or a slot is free. */
    fun canSet(attacker: Int, defender: Int, multiplier: Int): Boolean =
        multiplier in VALID_MULTIPLIERS && attacker in 0 until TYPES && defender in 0 until TYPES && (multiplier == NEUTRAL || (attacker to defender) in byPair || slotsUsed < HgssLayout.TYPE_CHART_SLOTS)

    /**
     * [attacker] against [defender] set to [multiplier]; unchanged when there is no slot for it. A neutral multiplier removes the entry.
     * Normal and Fighting against Ghost at 0x go to the Foresight part (the only immunities Foresight removes), anything else to the main part
     * (after the last entry of the same attacker).
     */
    fun with(attacker: Int, defender: Int, multiplier: Int): TypeChart {
        if (!canSet(attacker, defender, multiplier)) return this
        val without = copy(
            main = main.filterNot { it.attacker == attacker && it.defender == defender },
            foresight = foresight.filterNot { it.attacker == attacker && it.defender == defender },
        )
        if (multiplier == NEUTRAL) return without
        val entry = TypeEntry(attacker, defender, multiplier)
        if (multiplier == 0 && defender == GHOST && attacker in FORESIGHT_ATTACKERS) return without.copy(foresight = without.foresight + entry)
        val at = without.main.indexOfLast { it.attacker == attacker }
        return without.copy(main = if (at < 0) without.main + entry else without.main.toMutableList().also { it.add(at + 1, entry) })
    }

    /** Writes the table into a copy of [overlay]; slots a shorter table leaves are filled with terminator entries. */
    fun applyTo(overlay: ByteArray): ByteArray = overlay.copyOf().also { out ->
        val entries = main.map { listOf(it.attacker, it.defender, it.multiplier) } + listOf(listOf(FORESIGHT, FORESIGHT, 0)) +
            foresight.map { listOf(it.attacker, it.defender, it.multiplier) }
        for (slot in 0..HgssLayout.TYPE_CHART_SLOTS) {
            val e = entries.getOrElse(slot) { listOf(END, END, 0) }
            for (b in 0..2) out[HgssLayout.TYPE_CHART_OFFSET + 3 * slot + b] = e[b].toByte()
        }
    }

    fun save(project: RomProject): FoxResult<Unit> = when (val o = project.readOverlay(HgssLayout.TYPE_CHART_OVERLAY)) {
        is FoxResult.Ok -> project.writeOverlay(HgssLayout.TYPE_CHART_OVERLAY, applyTo(o.value))
        is FoxResult.Err -> o
    }

    companion object {
        const val NEUTRAL = 10

        /** The multipliers times 10 the game uses: none, half, neutral (not stored) and double. */
        val VALID_MULTIPLIERS = listOf(0, 5, 10, 20)
        const val GHOST = 7

        /** The attackers whose Ghost immunity the table keeps in the Foresight part: Normal (0) and Fighting (1). */
        val FORESIGHT_ATTACKERS = setOf(0, 1)
        const val FORESIGHT = 0xFE
        const val END = 0xFF

        /** Type numbers 0 to 17; 9 is "???" (no entries). */
        const val TYPES = 18

        fun parse(overlay: ByteArray): FoxResult<TypeChart> {
            val end = HgssLayout.TYPE_CHART_OFFSET + 3 * (HgssLayout.TYPE_CHART_SLOTS + 1)
            if (overlay.size < end) return FoxResult.Err("Overlay ${HgssLayout.TYPE_CHART_OVERLAY} is too small for the type table")
            val main = ArrayList<TypeEntry>()
            val foresight = ArrayList<TypeEntry>()
            var inForesight = false
            for (slot in 0..HgssLayout.TYPE_CHART_SLOTS) {
                val at = HgssLayout.TYPE_CHART_OFFSET + 3 * slot
                val a = overlay[at].toInt() and 0xFF
                val d = overlay[at + 1].toInt() and 0xFF
                val m = overlay[at + 2].toInt() and 0xFF
                when {
                    a == END -> return if (inForesight) FoxResult.Ok(TypeChart(main, foresight)) else FoxResult.Err("The type table has no Foresight divider: this is not the table this editor knows")
                    a == FORESIGHT -> if (inForesight) return FoxResult.Err("The type table has two Foresight dividers") else inForesight = true
                    a >= TYPES || d >= TYPES -> return FoxResult.Err("Type table entry $slot has type $a against $d")
                    m !in VALID_MULTIPLIERS -> return FoxResult.Err("Type table entry $slot has the multiplier $m: this is not the table this editor knows")
                    inForesight -> foresight += TypeEntry(a, d, m)
                    else -> main += TypeEntry(a, d, m)
                }
            }
            return FoxResult.Err("The type table has no terminator")
        }

        fun load(project: RomProject): FoxResult<TypeChart> = when (val o = project.readOverlay(HgssLayout.TYPE_CHART_OVERLAY)) {
            is FoxResult.Ok -> parse(o.value)
            is FoxResult.Err -> o
        }
    }
}
