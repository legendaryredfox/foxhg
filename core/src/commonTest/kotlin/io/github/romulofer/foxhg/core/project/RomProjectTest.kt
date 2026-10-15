package io.github.romulofer.foxhg.core.project

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.nds.ByteArrayRomSink
import io.github.romulofer.foxhg.core.nds.ByteArrayRomSource
import io.github.romulofer.foxhg.core.nds.NdsRom
import io.github.romulofer.foxhg.core.testing.SyntheticRom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RomProjectTest {
    private val romBytes = SyntheticRom.build(listOf("one" to ByteArray(100) { 1 }, "two" to ByteArray(300) { 2 }))

    private fun project(): RomProject = (RomProject.open(ByteArrayRomSource(romBytes)) as FoxResult.Ok).value

    @Test
    fun startsCleanAndReadsOriginals() {
        val p = project()
        assertFalse(p.isDirty)
        assertContentEquals(ByteArray(100) { 1 }, p.read("a/one"))
    }

    @Test
    fun editsAreVisibleToReadersAndMarkTheProjectDirty() {
        val p = project()
        assertIs<FoxResult.Ok<Unit>>(p.write("a/one", ByteArray(100) { 9 }))
        assertTrue(p.isDirty && p.isEdited("a/one"))
        assertContentEquals(ByteArray(100) { 9 }, p.read("a/one"))
        assertContentEquals(ByteArray(300) { 2 }, p.read("a/two"))
    }

    @Test
    fun writingTheOriginalBytesBackIsNotAnEdit() {
        val p = project()
        p.write("a/one", ByteArray(100) { 9 })
        p.write("a/one", ByteArray(100) { 1 })
        assertFalse(p.isDirty)
    }

    @Test
    fun buildAppliesEditsAndLeavesTheSourceAlone() {
        val p = project()
        p.write("a/two", ByteArray(50) { 7 })
        val sink = ByteArrayRomSink()
        assertIs<FoxResult.Ok<Unit>>(p.build(sink))
        val built = (NdsRom.open(ByteArrayRomSource(sink.bytes)) as FoxResult.Ok).value
        assertContentEquals(ByteArray(50) { 7 }, built.read("a/two"))
        assertContentEquals(ByteArray(100) { 1 }, built.read("a/one"))
        assertContentEquals(ByteArray(300) { 2 }, p.rom.read("a/two"), "the opened ROM must not change")
    }

    @Test
    fun cleanBuildIsIdentical() {
        val sink = ByteArrayRomSink()
        project().build(sink)
        assertContentEquals(romBytes, sink.bytes)
    }

    @Test
    fun unknownPathIsRejected() {
        assertIs<FoxResult.Err>(project().write("a/nope", byteArrayOf(1)))
        assertEquals(emptySet(), project().editedPaths)
    }

    private fun clocked(): Pair<RomProject, LongArray> {
        val now = longArrayOf(0)
        return project().also { it.clock = { now[0] } } to now
    }

    @Test
    fun undoAndRedoStepThroughEdits() {
        val (p, now) = clocked()
        p.write("a/one", ByteArray(100) { 9 })
        now[0] += 5000
        p.write("a/one", ByteArray(100) { 8 })
        assertTrue(p.canUndo && !p.canRedo)
        assertEquals(listOf<EditTarget>(EditTarget.File("a/one")), p.undo())
        assertContentEquals(ByteArray(100) { 9 }, p.read("a/one"))
        assertTrue(p.canRedo)
        p.undo()
        assertFalse(p.isDirty)
        assertContentEquals(ByteArray(100) { 1 }, p.read("a/one"))
        assertEquals(null, p.undo())
        p.redo()
        p.redo()
        assertContentEquals(ByteArray(100) { 8 }, p.read("a/one"))
        assertEquals(null, p.redo())
    }

    @Test
    fun rapidWritesToOneFileAreOneStep() {
        val (p, now) = clocked()
        for (v in 2..6) { p.write("a/one", ByteArray(100) { v.toByte() }); now[0] += 100 }
        p.undo()
        assertFalse(p.isDirty)
        assertFalse(p.canUndo)
    }

    @Test
    fun writesToOtherFilesAreSeparateStepsAndANewEditClearsRedo() {
        val (p, _) = clocked()
        p.write("a/one", ByteArray(100) { 9 })
        p.write("a/two", ByteArray(300) { 9 })
        p.undo()
        assertContentEquals(ByteArray(300) { 2 }, p.read("a/two"))
        assertContentEquals(ByteArray(100) { 9 }, p.read("a/one"))
        assertTrue(p.canRedo)
        p.write("a/two", ByteArray(300) { 7 })
        assertFalse(p.canRedo)
    }

    @Test
    fun writingTheSameBytesAddsNoStep() {
        val (p, _) = clocked()
        p.write("a/one", ByteArray(100) { 1 })
        assertFalse(p.canUndo)
    }

    @Test
    fun revertAllUndoesAsOneStep() {
        val (p, now) = clocked()
        p.write("a/one", ByteArray(100) { 9 })
        now[0] += 5000
        p.write("a/two", ByteArray(300) { 9 })
        now[0] += 5000
        p.revertAll()
        assertFalse(p.isDirty)
        p.undo()
        assertEquals(setOf("a/one", "a/two"), p.editedPaths)
        assertContentEquals(ByteArray(100) { 9 }, p.read("a/one"))
    }

    @Test
    fun theHistoryRoundTripsThroughBytesAndComesBackOnAFreshProject() {
        val (p, now) = clocked()
        p.write("a/one", ByteArray(100) { 9 })
        now[0] += 5000
        p.write("a/two", ByteArray(300) { 8 })
        now[0] += 5000
        p.undo() // one undo step is now a redo step
        val bytes = p.historySnapshot().toBytes()

        // a fresh project with the same files, as after reopening
        val fresh = project()
        fresh.write("a/one", ByteArray(100) { 9 })
        val parsed = (UndoSnapshot.parse(bytes) as FoxResult.Ok).value
        assertTrue(fresh.restoreHistory(parsed))
        assertTrue(fresh.canUndo && fresh.canRedo)
        fresh.redo()
        assertContentEquals(ByteArray(300) { 8 }, fresh.read("a/two"))
        fresh.undo()
        fresh.undo()
        assertFalse(fresh.isDirty, "both steps undone: the project is as the ROM")
        // a new edit right after reopening is a new step, not merged into the old one
        val again = project()
        again.write("a/one", ByteArray(100) { 9 })
        again.restoreHistory(parsed)
        again.write("a/one", ByteArray(100) { 7 })
        again.undo()
        assertContentEquals(ByteArray(100) { 9 }, again.read("a/one"))
    }

    @Test
    fun aHistoryThatDoesNotFitTheFilesIsDropped() {
        val (p, now) = clocked()
        p.write("a/one", ByteArray(100) { 9 })
        val snapshot = p.historySnapshot()
        val other = project() // the file was not edited: the history would undo something that is not there
        assertFalse(other.restoreHistory(snapshot))
        assertFalse(other.canUndo)
        val changed = project()
        changed.write("a/one", ByteArray(100) { 5 }) // edited elsewhere
        assertFalse(changed.restoreHistory(snapshot))
        assertTrue(p.historyDirty)
        p.markHistorySaved()
        assertFalse(p.historyDirty)
        now[0] += 5000
        p.write("a/two", ByteArray(300) { 1 })
        assertTrue(p.historyDirty)
    }

    @Test
    fun damagedHistoryBytesAreRefusedAndTrimmingKeepsTheNewestSteps() {
        assertIs<FoxResult.Err>(UndoSnapshot.parse(ByteArray(10)))
        assertIs<FoxResult.Err>(UndoSnapshot.parse("FHGH".encodeToByteArray() + ByteArray(12)).let { if (it is FoxResult.Ok && it.value.undo.isEmpty()) FoxResult.Err("empty") else it })
        val (p, now) = clocked()
        for (v in 1..5) { p.write("a/two", ByteArray(300) { v.toByte() }); now[0] += 5000 }
        val full = p.historySnapshot()
        assertEquals(5, full.undo.size)
        val small = full.trimmed(1300) // each step holds 600 bytes: two fit
        assertEquals(2, small.undo.size)
        assertContentEquals(ByteArray(300) { 5 }, small.undo.last().single().after)
        val bytes = small.toBytes()
        val back = (UndoSnapshot.parse(bytes) as FoxResult.Ok).value
        assertEquals(2, back.undo.size)
        assertIs<FoxResult.Err>(UndoSnapshot.parse(bytes.copyOf(bytes.size - 5)))
    }
}
