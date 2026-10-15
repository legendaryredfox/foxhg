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
}
