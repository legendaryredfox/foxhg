package io.github.romulofer.foxhg.core.script

import io.github.romulofer.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptTextTest {
    private val source = """
        script_1:
            LockAll
            Wait 30, 16384        ; comment
            GoToIf 1, function_1
            ApplyMovement 255, action_1
            End
        function_1:
            SetFlag 0x10
            End
        action_1:
            FaceNorth 2
            End
    """.trimIndent()

    private fun file(text: String) = (ScriptText.parse(text) as FoxResult.Ok).value

    @Test
    fun textBuildsAFileThatWritesAndReadsBack() {
        val f = file(source)
        val bytes = f.toBytes()
        assertEquals(0xFD13, (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8), "header: one entry, then the marker")
        val again = (ScriptFile.parse(bytes) as FoxResult.Ok).value
        assertContentEquals(bytes, again.toBytes())
        assertEquals(f.scriptCount, again.scriptCount)
        assertEquals(file(ScriptText.toText(again)), again, "text of a parsed file reads back to it")
    }

    @Test
    fun jumpsResolveToLabelsAndMovementListsAreAligned() {
        val bytes = file(source).toBytes()
        val again = (ScriptFile.parse(bytes) as FoxResult.Ok).value
        val jumps = again.items.filterIsInstance<ScriptItem.Instruction>().mapNotNull { it.command.target }
        assertEquals(listOf("function_1", "action_1"), jumps.map { it.toString() })
    }

    @Test
    fun mistakesAreReportedWithTheLine() {
        for ((text, part) in listOf(
            "script_1:\n    Nope 1\n" to "Line 2",
            "script_1:\n    SetFlag\n    End\n" to "needs another parameter",
            "script_1:\n    SetFlag 70000\n    End\n" to "does not fit",
            "script_1:\n    GoTo function_9\n" to "not defined",
            "script_2:\n    End\n" to "numbered 1",
            "script_1:\n    End\nscript_1:\n    End\n" to "twice",
        )) {
            val r = ScriptText.parse(text)
            assertTrue(r is FoxResult.Err && r.message.contains(part), "$text -> $r")
        }
    }
}
