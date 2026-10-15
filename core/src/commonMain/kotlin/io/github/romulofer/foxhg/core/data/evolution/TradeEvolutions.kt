package io.github.romulofer.foxhg.core.data.evolution

/** One change made by [TradeEvolutions.convert]. */
data class TradeConversion(val species: Int, val slot: Int, val before: EvolutionEntry, val after: EvolutionEntry)

/**
 * Removes trade evolutions so every species can evolve without a second player:
 * plain trade becomes a level-up at [levelForTrade]; trade-holding-an-item becomes using that item (the stone method),
 * so Metal Coat, Dragon Scale, King's Rock, ... are used from the bag.
 */
object TradeEvolutions {
    fun convert(records: List<EvolutionData>, levelForTrade: Int): Pair<List<EvolutionData>, List<TradeConversion>> {
        require(levelForTrade in 2..100) { "level must be 2..100" }
        val changes = ArrayList<TradeConversion>()
        val result = records.mapIndexed { species, data ->
            var updated = data
            data.slots.forEachIndexed { slot, entry ->
                val replacement = when (entry.known) {
                    EvolutionMethod.TRADE -> entry.copy(method = EvolutionMethod.LEVEL.id, param = levelForTrade)
                    EvolutionMethod.TRADE_ITEM -> entry.copy(method = EvolutionMethod.STONE.id)
                    else -> null
                }
                if (replacement != null) {
                    updated = updated.with(slot, replacement)
                    changes += TradeConversion(species, slot, entry, replacement)
                }
            }
            updated
        }
        return result to changes
    }
}
