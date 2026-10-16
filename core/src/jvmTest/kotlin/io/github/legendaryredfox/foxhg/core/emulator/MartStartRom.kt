package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.nds.FileRomSink
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import java.nio.file.Files
import java.nio.file.Path

/**
 * A ROM whose new game starts in a Poke Mart next to a clerk, with the bag, trainer card and save button given, for making a save with the
 * emulator that stands at a clerk. The start location (the decomp's `sLocation_PlayerRoom`, found in the ARM9 by its bytes) is patched to map
 * [map], tile ([x], [y]), facing [direction] (0 north, 1 south, 2 west, 3 east), and the init script (std script 9600, file 149) sets the flags.
 */
object MartStartRom {
    /** Cherrygrove's Poke Mart (map 68), next to the clerk of the badge mart at (2, 6), facing west. */
    val CHERRYGROVE = Start(68, 3, 6, 2)

    /** Viridian's Poke Mart (map 500), next to the second clerk at (2, 4), who sells special mart 26 (Steel Mail, Net Ball, Heal Ball). */
    val VIRIDIAN = Start(500, 3, 4, 2)

    data class Start(val map: Int, val x: Int, val y: Int, val direction: Int, val gifts: List<Gift> = emptyList())

    /** A Pokemon the init script gives at the start of the new game (`GiveMon`): the party of a save made with it. */
    data class Gift(val species: Int, val level: Int, val item: Int = 0)

    /** Cherrygrove's mart with a party: Cyndaquil Lv 5, Pikachu Lv 20 holding a Potion and Pidgey Lv 7. */
    val CHERRYGROVE_WITH_PARTY = CHERRYGROVE.copy(gifts = listOf(Gift(155, 5), Gift(25, 20, 17), Gift(16, 7)))

    /** Writes the ROM for [start] to [target], made from the ROM at [romPath]. */
    fun build(romPath: Path, target: Path, start: Start) {
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val arm9 = (project.readArm9() as FoxResult.Ok).value
            // mapId 64 (the player's room), warp 0xFFFFFFFF, x 6, y 6, direction 1 (south)
            val vanilla = byteArrayOf(64, 0, 0, 0, -1, -1, -1, -1, 6, 0, 0, 0, 6, 0, 0, 0, 1, 0, 0, 0)
            val at = (0..arm9.size - vanilla.size).filter { i -> vanilla.indices.all { arm9[i + it] == vanilla[it] } }
            check(at.size == 1) { "the start location is at ${at.map { it.toString(16) }}" }
            byteArrayOf(
                start.map.toByte(), (start.map shr 8).toByte(), 0, 0, -1, -1, -1, -1, start.x.toByte(), 0, 0, 0, start.y.toByte(), 0, 0, 0, start.direction.toByte(), 0, 0, 0,
            ).copyInto(arm9, at.single())
            check(project.writeArm9(arm9) is FoxResult.Ok)

            val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
            val init = (scripts.records[149] as ScriptEntry.Script).file
            val lines = ScriptText.toText(init).lines().toMutableList()
            val first = lines.indexOfFirst { it.trim() == "script_1:" } + 1
            check(first > 0) { "no script_1 in the init script" }
            for (gift in start.gifts.reversed()) lines.add(first, "    GiveMon ${gift.species}, ${gift.level}, ${gift.item}, 0, 0, VAR_SPECIAL_RESULT")
            for (flag in listOf("FLAG_GOT_BAG", "FLAG_GOT_TRAINER_CARD", "FLAG_GOT_SAVE_BUTTON", "FLAG_GOT_OPTIONS_BUTTON")) lines.add(first, "    SetFlag $flag")
            val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited init script does not parse")
            check(scripts.with(149, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            Files.createDirectories(target.toAbsolutePath().parent)
            FileRomSink(target).use { sink -> check(project.build(sink) is FoxResult.Ok) { "build failed" } }
        }
    }
}
