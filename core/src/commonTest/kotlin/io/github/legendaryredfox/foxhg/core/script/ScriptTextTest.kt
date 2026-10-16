package io.github.legendaryredfox.foxhg.core.script

import io.github.legendaryredfox.foxhg.core.FoxResult
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

    @Test
    fun flagAndVariableNamesAreNotesAndCanBeTyped() {
        val named = file("script_1:\n    SetFlag FLAG_NOTHING\n    SetVar VAR_SPECIAL_RESULT, 1\n    End\n")
        val text = ScriptText.toText(named)
        assertTrue(text.contains("SetFlag 0") && text.contains("; FLAG_NOTHING"), text)
        assertTrue(text.contains("; VAR_SPECIAL_RESULT"), text)
        assertEquals(named, file(text), "the notes are comments: reading the text gives the same file")
    }

    @Test
    fun messageNotesShowTheStartOfTheText() {
        val f = file("script_1:\n    NPCMsg 3\n    End\n")
        val text = ScriptText.toText(f) { id -> if (id == 3) "Hello there!\\nHow are you?" else null }
        assertTrue(text.contains("; \"Hello there! How are you?\""), text)
        assertEquals(f, file(text))
        assertTrue(!ScriptText.toText(f).contains(";"), "without a message source there is no note")
    }
}
