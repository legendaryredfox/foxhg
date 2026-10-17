package io.github.legendaryredfox.foxhg.core.nds

/** The games foxhg can name from the header. The game family decides which layout and editors apply. */
enum class GameFamily(val label: String) {
    DIAMOND_PEARL("Diamond and Pearl"),
    PLATINUM("Platinum"),
    HGSS("HeartGold and SoulSilver"),
    BLACK_WHITE("Black and White"),
    BLACK_WHITE_2("Black 2 and White 2"),
}

enum class GameVersion(val family: GameFamily, val title: String, val codePrefix: String) {
    DIAMOND(GameFamily.DIAMOND_PEARL, "Pokémon Diamond", "ADA"),
    PEARL(GameFamily.DIAMOND_PEARL, "Pokémon Pearl", "APA"),
    PLATINUM(GameFamily.PLATINUM, "Pokémon Platinum", "CPU"),
    HEARTGOLD(GameFamily.HGSS, "Pokémon HeartGold", "IPK"),
    SOULSILVER(GameFamily.HGSS, "Pokémon SoulSilver", "IPG"),
    BLACK(GameFamily.BLACK_WHITE, "Pokémon Black", "IRB"),
    WHITE(GameFamily.BLACK_WHITE, "Pokémon White", "IRA"),
    BLACK_2(GameFamily.BLACK_WHITE_2, "Pokémon Black 2", "IRE"),
    WHITE_2(GameFamily.BLACK_WHITE_2, "Pokémon White 2", "IRD"),
}

/** The language of a ROM, from the last letter of its game code (as the cartridge regions use it). */
enum class GameLanguage(val letter: Char, val label: String) {
    JAPANESE('J', "Japanese"),
    ENGLISH('E', "English"),
    FRENCH('F', "French"),
    GERMAN('D', "German"),
    ITALIAN('I', "Italian"),
    SPANISH('S', "Spanish"),
    KOREAN('K', "Korean"),
    ;

    companion object { fun of(letter: Char): GameLanguage? = entries.firstOrNull { it.letter == letter } }
}

/**
 * What a ROM is: the game, its language and the game code it came from. Only English HeartGold and SoulSilver have a layout and editors ([editable]);
 * any other game or language is named but opens read-only, so the file browser works and no editor misreads another game's files.
 */
data class GameProfile(val version: GameVersion?, val language: GameLanguage?, val gameCode: String) {
    val family: GameFamily? get() = version?.family

    /** True when foxhg has the paths, structures and text tables of this game and language. */
    val editable: Boolean get() = family == GameFamily.HGSS && language == GameLanguage.ENGLISH

    /** What the user sees: "Pokémon Platinum (Spanish)", or the game code of a ROM that is not a known Pokémon game. */
    val displayName: String
        get() = when {
            version == null -> "Unknown game ($gameCode)"
            language == null -> "${version.title} ($gameCode)"
            else -> "${version.title} (${language.label})"
        }

    /** Why this ROM opens read-only, or null when it can be edited. */
    val readOnlyReason: String?
        get() = when {
            editable -> null
            version == null -> "This ROM is not a Pokémon game foxhg knows ($gameCode). It opens read-only: you can browse and export its files."
            family == GameFamily.HGSS -> "${displayName} is not supported for editing yet: only the English release has its layout. It opens read-only: you can browse and export its files."
            else -> "${displayName} is not supported for editing yet (foxhg edits Pokémon HeartGold and SoulSilver, English). It opens read-only: you can browse and export its files."
        }

    companion object {
        fun fromGameCode(code: String): GameProfile {
            val version = if (code.length == 4) GameVersion.entries.firstOrNull { code.startsWith(it.codePrefix) } else null
            val language = if (version != null) GameLanguage.of(code[3]) else null
            return GameProfile(version, language, code)
        }
    }
}
