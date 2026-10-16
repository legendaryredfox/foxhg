package io.github.legendaryredfox.foxhg.core.data.personal

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersonalDataTest {
    @Test
    fun anyRecordRoundTripsByteForByte() {
        val random = Random(42)
        repeat(200) {
            val bytes = ByteArray(PersonalData.SIZE) { random.nextInt(256).toByte() }
            val parsed = (PersonalData.parse(bytes) as FoxResult.Ok).value
            assertContentEquals(bytes, parsed.toBytes())
        }
    }

    @Test
    fun evYieldIsTwoBitsPerStat() {
        val bytes = ByteArray(PersonalData.SIZE)
        bytes[10] = 0b11_01_10_01.toByte() // speed 3, def 1, atk 2, hp 1
        bytes[11] = 0b0000_10_11.toByte()  // sp.def 2, sp.atk 3
        val p = (PersonalData.parse(bytes) as FoxResult.Ok).value
        assertEquals(listOf(1, 2, 1, 3, 3, 2), listOf(p.evHp, p.evAttack, p.evDefense, p.evSpeed, p.evSpAttack, p.evSpDefense))
    }

    @Test
    fun tmHmBitsCanBeToggled() {
        val p = (PersonalData.parse(ByteArray(PersonalData.SIZE)) as FoxResult.Ok).value
        assertFalse(p.learnsTmHm(40))
        val withTm = p.withTmHm(40, true).withTmHm(95, true)
        assertTrue(withTm.learnsTmHm(40) && withTm.learnsTmHm(95))
        assertFalse(withTm.withTmHm(40, false).learnsTmHm(40))
        assertEquals(0L, withTm.withTmHm(40, false).withTmHm(95, false).tmHmWords.sum())
    }

    @Test
    fun rejectsWrongSize() {
        assertIs<FoxResult.Err>(PersonalData.parse(ByteArray(43)))
    }
}
