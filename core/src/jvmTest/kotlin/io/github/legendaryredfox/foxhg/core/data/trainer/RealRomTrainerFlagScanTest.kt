package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.TestRoms
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.event.EventFile
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.ScriptCommands
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptItem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomTrainerFlagScanTest {
    private val romPath: Path = TestRoms.heartGoldEnglish

    /** The flags the decomp names unknown after the trainer block of vanilla (0x82E) up to the new trainer limit are free: nothing uses them. */
    @Test
    fun noScriptOrObjectUsesAFlagBetweenTheVanillaTrainersAndTheNewTrainerLimit() {
        if (!Files.exists(romPath)) { println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}"); return }
        FileRomSource(romPath).use { source ->
            val project = (RomProject.open(source) as FoxResult.Ok).value
            val first = 0x550 + 735
            val limit = NewTrainer.FIRST_USED_FLAG_ABOVE_TRAINERS
            val inRange = { v: Int -> v in first until limit }
            val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value.records
            val scriptFlags = mutableSetOf<Int>()
            scripts.forEach { entry ->
                if (entry !is ScriptEntry.Script) return@forEach
                for (item in entry.file.items) {
                    val c = (item as? ScriptItem.Instruction)?.command ?: continue
                    val params = ScriptCommands.byId(c.id)?.params ?: continue
                    params.forEachIndexed { i, p ->
                        val v = c.args.getOrNull(i)?.toInt() ?: return@forEachIndexed
                        if (p.name.contains("flag")) { if (inRange(v)) scriptFlags += v }
                    }
                }
            }
            assertEquals(emptySet(), scriptFlags, "flags the scripts use inside the trainer block")
            val eventFlags = (EventFile.load(project) as FoxResult.Ok).value.records.flatMap { e -> e.overworlds.map { it.flag } }.filter(inRange)
            assertEquals(emptyList(), eventFlags, "flags the objects use inside the trainer block")
        }
    }
}
