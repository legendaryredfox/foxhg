package io.github.romulofer.foxhg.core.nds

/** Games foxhg can identify from the header game code. Only English HeartGold/SoulSilver are supported for editing. */
enum class Game(val displayName: String, val supported: Boolean) {
    HEARTGOLD_EN("Pokémon HeartGold (English)", supported = true),
    SOULSILVER_EN("Pokémon SoulSilver (English)", supported = true),
    HEARTGOLD_OTHER("Pokémon HeartGold (other language)", supported = false),
    SOULSILVER_OTHER("Pokémon SoulSilver (other language)", supported = false),
    UNKNOWN("Unknown game", supported = false);

    companion object {
        /** `IPK*` is HeartGold, `IPG*` is SoulSilver; the fourth letter is the region (E = USA/English). */
        fun fromGameCode(code: String): Game = when {
            code == "IPKE" -> HEARTGOLD_EN
            code == "IPGE" -> SOULSILVER_EN
            code.startsWith("IPK") -> HEARTGOLD_OTHER
            code.startsWith("IPG") -> SOULSILVER_OTHER
            else -> UNKNOWN
        }
    }
}
