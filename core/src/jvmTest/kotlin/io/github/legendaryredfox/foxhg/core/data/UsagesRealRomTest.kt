package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.research.Usage
import io.github.legendaryredfox.foxhg.core.data.research.UsageKind
import io.github.legendaryredfox.foxhg.core.data.research.Usages
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class UsagesRealRomTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    @Test
    fun theResearchHelperFindsKnownUsesInTheRealRom() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            fun find(kind: UsageKind, id: Int) = (Usages.find(project, kind, id) as FoxResult.Ok).value
            // FLAG_HIDE_WHIRL_ISLAND_LUGIA (579) is set and cleared in the Lugia script (file 104, see the static editor's findings)
            val lugia = find(UsageKind.FLAG, 579)
            println("USAGE flag 579: " + lugia.map { "${it.place}:${it.file}:${it.index} ${it.text}" })
            assertTrue(lugia.any { it.place == Usage.Place.SCRIPT && it.file == 104 && it.text.startsWith("SetFlag") }, "Lugia script sets the flag")
            assertTrue(lugia.any { it.place == Usage.Place.SCRIPT && it.file == 104 && it.text.startsWith("ClearFlag") }, "and clears it")
            // variable 0x8008 (32776) carries the ground item in the 255 item ball scripts of file 141
            val ballVar = find(UsageKind.VARIABLE, 0x8008)
            assertTrue(ballVar.count { it.file == 141 && it.place == Usage.Place.SCRIPT } >= 255, "every item ball script sets 0x8008")
            // items named by GiveItem, HasItem, TakeItem, the marts' buffers and the like
            val itemsNamed = (1..536).count { id -> find(UsageKind.ITEM, id).isNotEmpty() }
            println("USAGE items named by scripts: $itemsNamed of 536")
            assertTrue(itemsNamed > 20, "scripts name many items: $itemsNamed")
            // trainers: the first Youngster Joey (trainer 1?) is not asserted by name; trainer 1 to 740 are mostly on overworld objects or in TrainerBattle commands
            val trainerHits = (1..740).count { find(UsageKind.TRAINER, it).isNotEmpty() }
            println("USAGE trainers found somewhere: $trainerHits of 740")
            assertTrue(trainerHits > 400, "most trainers stand on a map: $trainerHits")
            // sprite entries: the player's (entry 0?) is not used; at least some entry is used by many objects
            val sprite = (0..300).maxOf { find(UsageKind.SPRITE, it).size }
            assertTrue(sprite > 20, "some sprite entry is shared by many objects: $sprite")
            // a flag no one uses
            assertTrue(find(UsageKind.FLAG, 0xFFFE).isEmpty())
        }
    }
}
