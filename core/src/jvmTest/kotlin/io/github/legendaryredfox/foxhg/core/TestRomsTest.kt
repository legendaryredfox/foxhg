package io.github.legendaryredfox.foxhg.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TestRomsTest {
    private val none: (String) -> String? = { null }

    @Test
    fun theEnglishHeartGoldRomKeepsItsOldPlaceAndName() {
        assertEquals(Path.of("../roms/heartgold.nds"), TestRoms.pathFor("IPKE", none))
        assertEquals(Path.of("/x/hg.nds"), TestRoms.pathFor("IPKE") { if (it == "FOXHG_ROM") "/x/hg.nds" else null })
    }

    @Test
    fun aRomOfAnotherLanguageIsFoundByItsGameCode() {
        assertEquals(Path.of("../roms/heartgold-IPKS.nds"), TestRoms.pathFor("IPKS", none))
        assertEquals(Path.of("../roms/soulsilver-IPGS.nds"), TestRoms.pathFor("IPGS", none))
        assertEquals(Path.of("../roms/cpue-CPUE.nds"), TestRoms.pathFor("CPUE", none))
    }

    @Test
    fun anEnvironmentVariableForTheCodeWins() {
        val env = mapOf("FOXHG_ROM_IPKS" to "/spanish.nds", "FOXHG_ROM" to "/english.nds")
        assertEquals(Path.of("/spanish.nds"), TestRoms.pathFor("IPKS") { env[it] })
        assertEquals(Path.of("/english.nds"), TestRoms.pathFor("IPKE") { env[it] })
        assertEquals(Path.of("/spanish-too.nds"), TestRoms.pathFor("IPKE") { if (it == "FOXHG_ROM_IPKE") "/spanish-too.nds" else env[it] })
    }
}
