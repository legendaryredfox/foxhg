package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
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
}
