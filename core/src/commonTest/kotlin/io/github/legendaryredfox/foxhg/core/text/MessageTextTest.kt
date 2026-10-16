package io.github.legendaryredfox.foxhg.core.text

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageTextTest {
    private fun encode(text: String) = (MessageText.encode(text) as FoxResult.Ok).value

    @Test
    fun variablesAndNamedCommandsReadAsBraces() {
        val chars = intArrayOf(0xFFFE, 0x0103, 2, 0, 0, 0x01DE, 0xFFFE, 0x0201, 1, 30, 0xFFFE, 0x0205, 0)
        assertEquals("{STRVAR_1 3, 0, 0} {PAUSE 30}{ALN_CENTER}", MessageText.decode(chars))
        assertContentEquals(chars, encode(MessageText.decode(chars)))
    }

    @Test
    fun unknownCommandsAndStrayMarkersKeepTheirCodes() {
        val unknown = intArrayOf(0xFFFE, 0x7777, 1, 5)
        assertEquals("{CMD 0x7777, 5}", MessageText.decode(unknown))
        assertContentEquals(unknown, encode(MessageText.decode(unknown)))
        val stray = intArrayOf(0x0100, 0xFFFE, 0x0204) // not a whole command
        assertContentEquals(stray, encode(MessageText.decode(stray)))
    }

    @Test
    fun badCommandsAreRejectedWithAPosition() {
        for (bad in listOf("{NOPE}", "{PAUSE x}", "{STRVAR_1}", "{STRVAR_1 300}", "{PAUSE 5")) {
            assertTrue(MessageText.encode(bad) is FoxResult.Err, bad)
        }
    }

    @Test
    fun aTokenTwoCodesShareStaysLosslessThroughDecodeAndEncode() {
        // the charmap spells full-width 'z' for both 0x00DF and 0x00F5: the second is written as a code, so it comes back as itself
        val chars = intArrayOf(0x00DF, 0x00F5, 0xFFFF)
        assertContentEquals(chars, encode(MessageText.decode(chars)))
    }
}
