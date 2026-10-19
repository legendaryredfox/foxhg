package io.github.legendaryredfox.foxhg.core.script

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * Setting the weather from a script (wiki guide "Setting the Weather From a Script", HeartGold English). `ChangeWeather` is dummied out, but
 * `FlashEffect` (the guide's `FlashAnimation`) sets the weather of the area to the immediate of one `movs r1, #12` in the ARM9 and then updates the weather
 * graphics; the debug command `WriteByteToAddr` (the guide's `AdrsValueSet`) writes a byte anywhere in RAM, so a script can change that immediate first. [snippet] is the three commands that do it, and puts the 12 back
 * so Flash still works. No ROM byte changes: this is script text.
 */
object ScriptWeather {
    /** RAM address of the immediate (ARM9 file offset [OFFSET], the ARM9 is loaded at 0x02000000). */
    const val ADDRESS = 0x020436D4
    const val OFFSET = 0x436D4

    /** Vanilla value: weather 12, "darkness after Flash". */
    const val DEFAULT = 0x0C

    /** The weather numbers of the game (decomp `constants/weather.h`, where 4, 9 and 10 are not named yet). */
    val NAMES: Map<Int, String> = mapOf(
        0 to "Sunny", 1 to "Rain", 2 to "Heavy rain", 3 to "Thunderstorm", 4 to "Storm (unconfirmed)", 5 to "Snow", 6 to "Blizzard", 7 to "Sandstorm",
        8 to "Diamond dust", 9 to "Mist 1 (unconfirmed)", 10 to "Mist 2 (unconfirmed)", 11 to "Darkness", 12 to "Darkness after Flash", 13 to "Low light",
    )

    /** The bytes around the immediate, from the guide: `... 0C 21` (`movs r1, #12`) inside the weather update function, `XX` is any byte. */
    private val PATTERN: List<Int?> = ("70 BD 38 B5 04 1C XX XX XX XX C0 68 F8 F7 XX XX XX XX 0C 21 XX XX XX XX 28 1C F8 F7 52 F9 80 34 01 1C 20 68 40 68 C0 68 XX XX XX XX 01 20 38 BD")
        .split(" ").map { if (it == "XX") null else it.toInt(16) }

    /** Where the pattern's `0C` is, from its start. */
    private const val IMMEDIATE_AT = 18

    /** Whether this ROM's ARM9 has the immediate where the guide says (vanilla HeartGold English); a failure says why the snippet can not be trusted. */
    fun check(project: RomProject): FoxResult<Unit> {
        val arm9 = when (val a = project.readArm9()) {
            is FoxResult.Ok -> a.value
            is FoxResult.Err -> return a
        }
        val start = OFFSET - IMMEDIATE_AT
        if (start < 0 || start + PATTERN.size > arm9.size) return FoxResult.Err("The ARM9 is too small: this is not a HeartGold English ROM")
        val matches = PATTERN.indices.all { i -> PATTERN[i]?.let { (arm9[start + i].toInt() and 0xFF) == it } ?: true }
        return if (matches) FoxResult.Ok(Unit) else FoxResult.Err("The weather code is not where the guide says: another ROM or an edited ARM9")
    }

    /** The commands that show [weather] now and keep the game's own Flash weather for later. */
    fun snippet(weather: Int): String {
        require(weather in 0..0xFF) { "A weather number is one byte" }
        return listOf(
            "WriteByteToAddr 0x${ADDRESS.toString(16).uppercase()}, $weather",
            "FlashEffect",
            "WriteByteToAddr 0x${ADDRESS.toString(16).uppercase()}, 0x${DEFAULT.toString(16).uppercase()}",
        ).joinToString("\n") { "        $it" }
    }
}
