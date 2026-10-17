package io.github.legendaryredfox.foxhg.core.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameProfileTest {
    private fun profile(code: String) = GameProfile.fromGameCode(code)

    @Test
    fun onlyEnglishHeartGoldAndSoulSilverAreEditable() {
        assertTrue(profile("IPKE").editable)
        assertTrue(profile("IPGE").editable)
        assertNull(profile("IPKE").readOnlyReason)
        for (code in listOf("IPKS", "IPGF", "IPKD", "IPGI", "IPKJ", "CPUE", "ADAE", "APAE", "IRBO", "IREO", "BPEE", "AAAA")) assertFalse(profile(code).editable, code)
    }

    @Test
    fun theGamesAndLanguagesAreNamedFromTheCode() {
        assertEquals("Pokémon HeartGold (English)", profile("IPKE").displayName)
        assertEquals("Pokémon SoulSilver (French)", profile("IPGF").displayName)
        assertEquals("Pokémon Platinum (Spanish)", profile("CPUS").displayName)
        assertEquals("Pokémon Diamond (German)", profile("ADAD").displayName)
        assertEquals("Pokémon Pearl (Italian)", profile("APAI").displayName)
        assertEquals(GameFamily.PLATINUM, profile("CPUE").family)
        assertEquals(GameFamily.DIAMOND_PEARL, profile("APAJ").family)
        assertEquals(GameFamily.BLACK_WHITE_2, profile("IRDO").family)
        assertEquals(GameFamily.BLACK_WHITE, profile("IRAO").family)
        assertEquals(GameLanguage.JAPANESE, profile("IPKJ").language)
        assertNull(profile("IRBO").language, "Gen V codes end in O for the international release, no language letter")
        assertEquals("Unknown game (BPEE)", profile("BPEE").displayName)
    }

    @Test
    fun readOnlyReasonsSayWhy() {
        assertTrue(assertNotNull(profile("IPKS").readOnlyReason).contains("only the English release"))
        assertTrue(assertNotNull(profile("CPUE").readOnlyReason).contains("Platinum"))
        assertTrue(assertNotNull(profile("BPEE").readOnlyReason).contains("not a Pokémon game"))
        // the profile agrees with the older Game enum on what is supported
        for (code in listOf("IPKE", "IPGE", "IPKS", "CPUE", "BPEE")) assertEquals(Game.fromGameCode(code).supported, profile(code).editable, code)
    }
}
