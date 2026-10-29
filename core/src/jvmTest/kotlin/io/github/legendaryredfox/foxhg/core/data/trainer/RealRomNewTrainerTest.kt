package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.LabelRef
import io.github.legendaryredfox.foxhg.core.script.ScriptCommand
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptItem
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomNewTrainerTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    private fun withProject(block: (RomProject) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block(RomProject.open(source).ok()) }
    }

    private fun names(project: RomProject) = TextArchive.load(project).ok().records[TextBanks.TRAINER_NAMES]

    @Test
    fun aNewTrainerIsACopyAtTheEndWithItsNameAndAnEmptyMessageSlot() = withProject { project ->
        val before = TrainerTable.load(project).ok()
        val count = before.trainers.size
        println("NEWTRAINER vanilla: $count trainers, ${names(project).entries.size} names, ${TrainerMessages.load(project).ok().offsets.size} message offsets")
        val template = 1
        val id = NewTrainer.add(project, "FOXHG", template).ok()
        assertEquals(count, id)

        val after = TrainerTable.load(project).ok()
        assertEquals(count + 1, after.trainers.size)
        assertEquals(before.trainers[template], after.trainers[id], "the new trainer is a copy of trainer $template")
        assertEquals(before.trainers, after.trainers.take(count), "the others did not change")
        assertEquals("FOXHG", names(project).entries.last().text())
        assertEquals(count + 1, names(project).entries.size)
        val messages = TrainerMessages.load(project).ok()
        assertEquals(count + 1, messages.offsets.size)
        assertEquals(emptyList(), messages.messagesOf(id), "no battle messages yet")
        assertEquals(1717, messages.size, "the messages did not change")

        // it can be given messages and a second one can follow
        val withMessage = messages.replaceAll(messages.all() + TrainerMessage(id, 1, "Hello!")).ok()
        assertEquals(1, withMessage.messagesOf(id).size)
        withMessage.save(project).ok()
        assertEquals(count + 1, NewTrainer.add(project, "SECOND", id).ok())
        assertEquals(count + 2, TrainerTable.load(project).ok().trainers.size)
        assertEquals(1, TrainerMessages.load(project).ok().messagesOf(id).size, "the first one kept its message")

        // undo takes all of it back
        while (project.canUndo) project.undo()
        assertFalse(project.isDirty)
        assertEquals(count, TrainerTable.load(project).ok().trainers.size)
    }

    @Test
    fun aBadRequestChangesNothing() = withProject { project ->
        val count = TrainerTable.load(project).ok().trainers.size
        assertTrue(NewTrainer.add(project, "FOXHG", count + 5) is FoxResult.Err, "no such trainer to copy")
        assertTrue(NewTrainer.add(project, "FOXHG", -1) is FoxResult.Err)
        assertTrue(NewTrainer.add(project, "A name with {BAD CODE", 1) is FoxResult.Err, "a name the game can not show")
        assertFalse(project.isDirty, "nothing was written")
    }

    @Test
    fun trainersFrom741OnGetAnEntryInTheTrainerScriptFileThatJumpsToTheSharedBody() = withProject { project ->
        val scripts = { (ScriptEntry.load(project).ok().records[NewTrainer.TRAINER_SCRIPT_FILE] as ScriptEntry.Script).file }
        val before = scripts()
        assertEquals(740, before.scriptCount)
        // 738 and 739 already have the shared body, 740 is the engine's eye-contact script: nothing is added for them
        for (id in 738..740) assertEquals(id, NewTrainer.add(project, "T$id", 1).ok())
        assertEquals(before, scripts(), "trainers up to 740 leave the file alone")
        assertEquals(741, NewTrainer.add(project, "T741", 1).ok())
        val after = scripts()
        assertEquals(741, after.scriptCount)
        assertEquals(null, after.problem())
        assertEquals(before.items, after.items.take(before.items.size), "the 740 entries did not change")
        assertEquals(
            listOf(ScriptItem.Label(LabelRef(LabelRef.Kind.SCRIPT, 741)), ScriptItem.Instruction(ScriptCommand(0x16, listOf(0L), LabelRef(LabelRef.Kind.SCRIPT, 1)))),
            after.items.drop(before.items.size),
        )
        assertEquals(742, NewTrainer.add(project, "T742", 1).ok())
        assertEquals(742, scripts().scriptCount)

        // undo takes the entries back with the trainers
        while (project.canUndo) project.undo()
        assertEquals(before, scripts())
    }
}
