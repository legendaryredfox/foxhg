package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.hpbar.HpBarSpeed
import io.github.legendaryredfox.foxhg.core.emulator.EmulatorSupport.available
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The HP bar speed in the game: a wild Rattata battle starts from the clerk's script (as in the shiny rate tests), the player picks FIGHT and
 * Tackle, and the two HP bars of the top screen are watched frame by frame; how many frames a bar takes for one pixel in its first drain is measured (the
 * damage and the start of the battle vary between runs, the speed of the bar does not): about 8 at a quarter of the speed, 2 at the game's and
 * 1 (one pixel every frame) at 16 times. Each class boots once, at one speed, and prints `HPBAR shift n: ...`.
 */
/** A drain of an HP bar: how many frames it changed in, over how many frames, and by how many pixels. */
internal class Drain(val steps: Int, val span: Int, val pixels: Int, val start: Int = 0) {
    val framesPerStep: Double get() = if (steps > 1) span.toDouble() / (steps - 1) else 0.0
}

internal object HpBarInGame {
    /** The rows of the two HP bars on the top screen (enemy at the top left, player at the right), as x range and y of the bar. */
    private val BARS = listOf(Triple(46..100, 43, 0), Triple(196..252, 119, 0))

    /** The first drain of an HP bar after Tackle was picked, at [shift]; null when the saved game is missing or no bar moved. */
    fun drain(shift: Int): Drain? {
        if (!EmulatorSaves.available("party")) return null
        val work = Files.createTempDirectory("foxhg-hpbar")
        try {
            val rom = MartInGame.build(work, "hpbar.nds") { project ->
                check(HpBarSpeed.save(project, HpBarSpeed(shift)) is FoxResult.Ok)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val file = (scripts.records[851] as ScriptEntry.Script).file
                val lines = ScriptText.toText(file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.add(first + 1, "        WildBattle 19, 5, 0")
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                check(scripts.with(851, ScriptEntry.Script(edited)).save(project) is FoxResult.Ok)
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run(EmulatorSaves.OPEN_BUY_LIST_SCRIPT + ",s1200,A,s120,A,s120,A")
                val seen = ArrayList<List<Int>>().also { it += fills(emu) }
                val moves = ArrayList<Int>()
                for (frame in 1..3000) {
                    emu.step(1)
                    seen += fills(emu)
                    if (seen.last() != seen[seen.size - 2]) moves += frame
                    if (frame == 60) { Files.createDirectories(MartInGame.shotDir); emu.savePicture(MartInGame.shotDir.resolve("hpbar-shift$shift.png")) }
                }
                // a drain: changes that follow each other less than 12 frames apart; the first one that moved a bar by 3 pixels or more
                // (a miss or a tiny hit would not tell the speed)
                val bursts = ArrayList<List<Int>>()
                var current = ArrayList<Int>()
                for (m in moves) { if (current.isNotEmpty() && m - current.last() >= 12) { bursts += current; current = ArrayList() }; current += m }
                if (current.isNotEmpty()) bursts += current
                val drains = bursts.map { burst ->
                    Drain(burst.size, burst.last() - burst.first(), seen[burst.first() - 1].indices.sumOf { abs(seen[burst.last()][it] - seen[burst.first() - 1][it]) }, burst.first())
                }
                val drain = drains.firstOrNull { it.pixels >= 3 }
                println("HPBAR shift $shift: first drain ${drain?.let { "at frame ${it.start}: ${it.steps} steps over ${it.span} frames, ${it.pixels} pixels (${it.framesPerStep} frames per step)" }}; all changes $moves")
                return drain
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    /** How many pixels of each bar's row are bar colored (green, yellow or red), the enemy bar then the player's. */
    private fun fills(emu: EmulatorDriver): List<Int> {
        val image = emu.screenshot() ?: error("no frame yet")
        return BARS.map { (xs, y, _) ->
            xs.count { x ->
                val c = image.getRGB(x, y)
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
                (g > r + 30 && g > b + 30) || (r > 200 && g in 120..230 && b < 90) || (r > 190 && g < 90 && b < 90)
            }
        }
    }
}

/** A quarter of the game's speed (shift 6): the bar moves slowly. */
class HpBarQuarterSpeedEmulatorTest {
    @Test fun theBarsAnimateForLong() {
        if (!available()) return
        val drain = HpBarInGame.drain(6)
        if (!EmulatorSaves.available("party")) return
        assertTrue(drain != null && drain.steps >= 3 && drain.framesPerStep >= 6, "a bar at a quarter of the speed takes about 8 frames a pixel: ${drain?.framesPerStep}")
    }
}

/** The game's own speed (shift 8). */
class HpBarNormalSpeedEmulatorTest {
    @Test fun theBarsAnimate() {
        if (!available()) return
        val drain = HpBarInGame.drain(8)
        if (!EmulatorSaves.available("party")) return
        assertTrue(drain != null && drain.steps >= 3 && drain.framesPerStep in 1.4..3.0, "a bar at the game's speed takes about 2 frames a pixel: ${drain?.framesPerStep}")
    }
}

/** Sixteen times the game's speed (shift 12). */
class HpBarFastSpeedEmulatorTest {
    @Test fun theBarsAnimateBriefly() {
        if (!available()) return
        val drain = HpBarInGame.drain(12)
        if (!EmulatorSaves.available("party")) return
        assertTrue(drain != null && drain.pixels >= 3 && drain.span <= 2, "a bar at 16 times the speed jumps within two frames: ${drain?.pixels} pixels over ${drain?.span} frames")
    }
}
