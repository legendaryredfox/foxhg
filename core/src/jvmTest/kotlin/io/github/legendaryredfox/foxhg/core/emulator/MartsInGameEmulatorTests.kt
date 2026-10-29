package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.data.item.ItemData
import io.github.legendaryredfox.foxhg.core.data.mart.BadgeMartEntry
import io.github.legendaryredfox.foxhg.core.data.mart.Marts
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.romPath
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.legendaryredfox.foxhg.core.FoxResult

/*
 * The marts in the running game: a saved game stands next to a clerk, the ROM variant is booted, Continue is chosen, the clerk is talked to and the
 * buy list opens. The shop copies the clerk's item list into RAM when it opens, so the list is found there as the u16 sequence; a picture of the
 * screen is kept in build/emulator-shots (look at it). Each class boots the core once (a core cannot be loaded twice in a process).
 */

/** Viridian's special mart 26 in the vanilla ROM: the list the pointer table names, as the shop shows it. */
class VanillaSpecialMartInGameEmulatorTest {
    @Test
    fun theShopHasTheListOfTheRom() {
        if (!available() || !EmulatorSaves.available("viridian")) return
        val expected = MartInGame.vanillaMarts().specials[26]
        MartInGame.withBuyListOpen("viridian", romPath, "viridian-vanilla.png") { emu ->
            val found = emu.findU16s(expected)
            assertTrue(found.size >= 2, "the list $expected is in the ARM9 and copied by the shop, found at ${found.map { it.toString(16) }}")
        }
    }
}

/** Mart 26 grown to six items: it lives in the synthetic overlay, and the shop shows all six. */
class MovedSpecialMartInGameEmulatorTest {
    @Test
    fun theShopShowsTheMovedList() {
        if (!available() || !EmulatorSaves.available("viridian")) return
        val dir = Files.createTempDirectory("foxhg-moved")
        try {
            var expected = emptyList<Int>()
            val rom = MartInGame.build(dir, "heartgold-moved26.nds") { project ->
                val items = project.names!!.items
                check(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
                var marts = (Marts.load(project) as FoxResult.Ok).value
                marts = marts.withItem(26, 0, items.indexOf("Rare Candy"))
                for (name in listOf("Potion", "Super Potion", "Hyper Potion")) marts = marts.withAdded(26, items.indexOf(name))
                check(marts.isMoved(26) && marts.save(project) is FoxResult.Ok)
                expected = marts.specials[26]
            }
            assertEquals(6, expected.size)
            MartInGame.withBuyListOpen("viridian", rom, "viridian-moved26.png") { emu ->
                val found = emu.findU16s(expected)
                assertTrue(found.any { it >= HgssEnglishLayout.syntheticOverlayAddress && it < HgssEnglishLayout.syntheticOverlayAddress + HgssEnglishLayout.syntheticOverlaySize }, "the moved list is in the synthetic overlay, found at ${found.map { it.toString(16) }}")
                assertTrue(found.any { it < HgssEnglishLayout.syntheticOverlayAddress || it >= HgssEnglishLayout.syntheticOverlayAddress + HgssEnglishLayout.syntheticOverlaySize }, "and the shop copied it, found at ${found.map { it.toString(16) }}")
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}

/** The standard clerk at Cherrygrove with the badge mart edited: Antidote's place sells Rare Candy, Parlyz Heal waits for 8 badges. */
class BadgeMartInGameEmulatorTest {
    @Test
    fun theShopHasTheEditedBadgeList() {
        if (!available() || !EmulatorSaves.available("cherrygrove")) return
        val dir = Files.createTempDirectory("foxhg-badge")
        try {
            var potion = 0
            var candy = 0
            var antidote = 0 // the item whose place Rare Candy takes
            val rom = MartInGame.build(dir, "heartgold-badge.nds") { project ->
                val items = project.names!!.items
                potion = items.indexOf("Potion"); candy = items.indexOf("Rare Candy"); antidote = items.indexOf("Antidote")
                val marts = (Marts.load(project) as FoxResult.Ok).value
                val antidoteAt = marts.badge.indexOfFirst { it.item == antidote }
                val parlyzAt = marts.badge.indexOfFirst { items[it.item] == "Parlyz Heal" }
                check(marts.withBadge(antidoteAt, BadgeMartEntry(candy, 1)).withBadge(parlyzAt, marts.badge[parlyzAt].copy(tier = 6)).save(project) is FoxResult.Ok)
                val itemData = (ItemData.load(project) as FoxResult.Ok).value
                check(itemData.with(candy, itemData.records[candy].copy(price = 1234)).save(project) is FoxResult.Ok)
            }
            MartInGame.withBuyListOpen("cherrygrove", rom, "cherrygrove-badge.png") { emu ->
                // Potion then Rare Candy next to each other is the shop's own list (the picture shows Parlyz Heal and Antidote gone; RAM holds Antidote elsewhere, in the bag)
                assertTrue(emu.findU16s(listOf(potion, candy)).isNotEmpty(), "the shop's list has Potion then Rare Candy")
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
