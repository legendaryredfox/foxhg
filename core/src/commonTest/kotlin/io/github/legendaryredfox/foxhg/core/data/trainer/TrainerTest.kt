package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TrainerTest {
    private fun header(type: Int, count: Int) = TrainerHeader(type, trainerClass = 2, unknown2 = 0, partySize = count, items = listOf(1, 0, 0, 0), aiFlags = 7, doubleBattle = 0)

    private fun mon(i: Int) = TrainerMon(difficulty = 30, genderAbility = 0, level = 10 + i, speciesAndForm = 16 + i, item = 20 + i, moves = listOf(33, 45, 0, 0), capsule = 0)

    @Test
    fun headerRoundTripsAndFlagsDecideRecordSize() {
        val bytes = header(3, 2).toBytes()
        val parsed = (TrainerHeader.parse(bytes) as FoxResult.Ok).value
        assertContentEquals(bytes, parsed.toBytes())
        assertEquals(true, parsed.hasCustomMoves && parsed.hasHeldItems)
        assertEquals(listOf(8, 10, 16, 18), listOf(false to false, false to true, true to false, true to true).map { (m, i) -> TrainerMon.recordSize(m, i) })
    }

    @Test
    fun everyPartyFormatRoundTrips() {
        for (type in 0..3) {
            val trainer = Trainer(header(type, 3), List(3) { mon(it) }.map { m ->
                // Fields a format does not store come back as zeros.
                m.copy(item = if (type and 2 != 0) m.item else 0, moves = if (type and 1 != 0) m.moves else listOf(0, 0, 0, 0))
            })
            val parsed = (Trainer.parse(trainer.header.toBytes(), trainer.partyBytes()) as FoxResult.Ok).value
            assertEquals(trainer, parsed, "type $type")
            assertContentEquals(trainer.partyBytes(), parsed.partyBytes())
        }
    }

    @Test
    fun speciesAndFormShareAWord() {
        val m = TrainerMon(0, 0, 5, speciesAndForm = (2 shl 10) or 201)
        assertEquals(201, m.species)
        assertEquals(2, m.form)
        assertEquals((2 shl 10) or 25, m.withSpecies(25).speciesAndForm)
    }

    @Test
    fun partySizeMustMatchTheHeader() {
        assertFailsWith<IllegalArgumentException> { Trainer(header(0, 2), listOf(mon(0))) }
        assertIs<FoxResult.Err>(Trainer.parse(header(0, 2).toBytes(), ByteArray(15)))
        val withSpare = (Trainer.parse(header(0, 0).toBytes(), ByteArray(8) { 9 }) as FoxResult.Ok).value
        assertContentEquals(ByteArray(8) { 9 }, withSpare.partyBytes())
        val t = Trainer(header(0, 1), listOf(mon(0)))
        assertEquals(2, t.withParty(listOf(mon(0), mon(1))).header.partySize)
    }
}
