package io.github.legendaryredfox.foxhg.core

import java.nio.file.Path

/**
 * Where the tests find the ROM of a game and language, by the game code of its header (`IPKE` is HeartGold English, `IPKS` HeartGold Spanish,
 * `IPGS` SoulSilver Spanish, ...). The path is `FOXHG_ROM_<code>` when that is set, else `roms/<game>-<code>.nds`; the English HeartGold ROM is also
 * `FOXHG_ROM` and `roms/heartgold.nds`, which is what the tests used before there was more than one. A test that finds no file passes with a notice.
 */
object TestRoms {
    /** The `roms/` folder, relative to the module the tests run in. */
    private const val ROMS = "../roms"

    const val HEARTGOLD_ENGLISH = "IPKE"

    val heartGoldEnglish: Path get() = pathFor(HEARTGOLD_ENGLISH)

    fun pathFor(gameCode: String, env: (String) -> String? = System::getenv): Path {
        env("FOXHG_ROM_$gameCode")?.let { return Path.of(it) }
        if (gameCode == HEARTGOLD_ENGLISH) return Path.of(env("FOXHG_ROM") ?: "$ROMS/heartgold.nds")
        return Path.of("$ROMS/${gameName(gameCode)}-$gameCode.nds")
    }

    /** The file name part for a game: `heartgold` for `IPK*`, `soulsilver` for `IPG*`, else the lower case code. */
    fun gameName(gameCode: String): String = when {
        gameCode.startsWith("IPK") -> "heartgold"
        gameCode.startsWith("IPG") -> "soulsilver"
        else -> gameCode.lowercase()
    }
}
