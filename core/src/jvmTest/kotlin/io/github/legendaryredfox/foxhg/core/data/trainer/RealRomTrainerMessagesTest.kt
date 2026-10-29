package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomTrainerMessagesTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    private fun withProject(block: (RomProject) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block(RomProject.open(source).ok()) }
    }

    @Test
    fun theTablesRoundTripAndEveryMessageBelongsToATrainerTheGameFinds() = withProject { project ->
        val messages = TrainerMessages.load(project).ok()
        assertEquals(1717, messages.size)
        assertEquals(735, messages.offsets.size)

        fun file0(path: String) = (Narc.parse(project.read(path)!!) as FoxResult.Ok).value.files.single()
        assertContentEquals(file0(HgssEnglishLayout.trainerMessageTable), messages.tableBytes())
        assertContentEquals(file0(HgssEnglishLayout.trainerMessageOffsets), messages.offsetBytes())

        // the game finds every message from its trainer's offset, and a trainer's messages are next to each other
        val found = (0 until messages.offsets.size).flatMap { messages.messagesOf(it) }
        assertEquals((0 until messages.size).toList(), found.sorted(), "every message is reached from exactly one trainer")
        assertEquals(found.size, found.toSet().size)

        // writing it all back leaves the project as it was
        messages.save(project).ok()
        assertFalse(project.isDirty, "an unchanged save must not dirty the project")
        // the same through the rebuild that CSV import uses
        val rebuilt = messages.replaceAll(messages.all()).ok()
        assertEquals(messages.trainers, rebuilt.trainers)
        assertEquals(messages.triggers, rebuilt.triggers)
        assertEquals(messages.offsets, rebuilt.offsets)
        assertEquals(messages.texts, rebuilt.texts)
        println("trigger counts: ${messages.triggers.groupingBy { it }.eachCount().toSortedMap()}")
    }

    @Test
    fun csvExportsAndImportsBackTheSameAndEditsSurviveASave() = withProject { project ->
        val names = project.names!!
        val messages = TrainerMessages.load(project).ok()
        val csv = messages.exportCsv { names.trainerNames.getOrElse(it) { "" } }
        val back = messages.importCsv(csv).ok()
        back.save(project).ok()
        assertFalse(project.isDirty, "a CSV round trip must not dirty the project")

        val falkner = names.trainerNames.indexOf("Falkner")
        val first = messages.messagesOf(falkner).first()
        println("Falkner #$falkner first message ${messages.message(first)}")

        // move one message to another trigger, add one for a trainer that had none, change a text
        val edited = messages.all().toMutableList()
        edited[first] = edited[first].copy(trigger = 20, text = "Edited battle line!")
        val bare = (0 until messages.offsets.size).first { messages.messagesOf(it).isEmpty() }
        edited += TrainerMessage(bare, 1, "A new message")
        val changed = messages.replaceAll(edited).ok()
        changed.save(project).ok()
        assertTrue(project.isDirty)

        val reloaded = TrainerMessages.load(project).ok()
        assertEquals(1718, reloaded.size)
        val hit = reloaded.messagesOf(bare).single()
        assertEquals(TrainerMessage(bare, 1, "A new message"), reloaded.message(hit))
        assertTrue(reloaded.messagesOf(falkner).any { reloaded.message(it) == TrainerMessage(falkner, 20, "Edited battle line!") })
        assertEquals(1718, reloaded.all().size)
        // untouched trainers keep their messages
        val other = (0 until messages.offsets.size).first { it != falkner && it != bare && messages.messagesOf(it).isNotEmpty() }
        assertEquals(messages.messagesOf(other).map { messages.message(it) }, reloaded.messagesOf(other).map { reloaded.message(it) })

        // bad input is refused whole
        assertTrue(messages.importCsv("trainer,trigger,text\r\n99999,1,x\r\n") is FoxResult.Err)
        assertTrue(messages.importCsv("trainer,trigger,text\r\n1,x,x\r\n") is FoxResult.Err)
        assertTrue(messages.importCsv("a,b\r\n1,2\r\n") is FoxResult.Err)
    }
}
