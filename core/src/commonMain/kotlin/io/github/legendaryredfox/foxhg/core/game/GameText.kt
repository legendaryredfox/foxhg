package io.github.legendaryredfox.foxhg.core.game

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.NdsRom
import io.github.legendaryredfox.foxhg.core.text.TextArchive

/** The ROM's message archives, parsed on first use and cached. */
class GameText(private val narc: Narc) {
    private val cache = HashMap<Int, TextArchive>()

    val bankCount: Int get() = narc.files.size

    fun bank(index: Int): FoxResult<TextArchive> {
        cache[index]?.let { return FoxResult.Ok(it) }
        if (index !in narc.files.indices) return FoxResult.Err("No message bank $index (the ROM has ${narc.files.size})")
        return when (val parsed = TextArchive.parse(narc.files[index])) {
            is FoxResult.Ok -> parsed.also { cache[index] = it.value }
            is FoxResult.Err -> FoxResult.Err("Message bank $index: ${parsed.message}")
        }
    }

    /** All messages of a bank as readable text. */
    fun lines(index: Int): FoxResult<List<String>> = when (val b = bank(index)) {
        is FoxResult.Ok -> FoxResult.Ok(b.value.entries.map { it.text() })
        is FoxResult.Err -> b
    }

    companion object {
        fun open(rom: NdsRom): FoxResult<GameText> {
            val bytes = rom.read(HgssLayout.MESSAGES) ?: return FoxResult.Err("The ROM has no ${HgssLayout.MESSAGES} (message archives)")
            return when (val narc = Narc.parse(bytes)) {
                is FoxResult.Ok -> FoxResult.Ok(GameText(narc.value))
                is FoxResult.Err -> FoxResult.Err("${HgssLayout.MESSAGES}: ${narc.message}")
            }
        }
    }
}

/** Display names for ids, read from the ROM's own text. An id outside the list is shown as `#id`. */
class GameNames(text: GameText) {
    val species: List<String> = load(text, TextBanks.SPECIES)
    val moves: List<String> = load(text, TextBanks.MOVES)
    val items: List<String> = load(text, TextBanks.ITEMS)
    val abilities: List<String> = load(text, TextBanks.ABILITIES)
    val types: List<String> = load(text, TextBanks.TYPES)
    val trainerClasses: List<String> = load(text, TextBanks.TRAINER_CLASSES)
    val trainerNames: List<String> = load(text, TextBanks.TRAINER_NAMES)
    val locations: List<String> = load(text, TextBanks.LOCATIONS)
    val tradeNames: List<String> = load(text, TextBanks.TRADE_NAMES)
    val battleTowerNames: List<String> = load(text, TextBanks.BATTLE_TOWER_NAMES)
    val battleTowerMessages: List<String> = load(text, TextBanks.BATTLE_TOWER_MESSAGES)

    companion object {
        /** The charmap spells the "PK" and "MN" glyphs of "Pokémon" as two currency signs; names show them as letters. */
        private fun load(text: GameText, bank: Int): List<String> =
            ((text.lines(bank) as? FoxResult.Ok)?.value ?: emptyList()).map { it.replace("\u20A7", "PK").replace("\u20A6", "MN") }

        fun name(list: List<String>, id: Int): String = list.getOrNull(id) ?: "#$id"
    }
}
