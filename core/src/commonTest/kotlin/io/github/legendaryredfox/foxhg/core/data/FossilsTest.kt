package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.fossil.Fossil
import io.github.legendaryredfox.foxhg.core.data.fossil.Fossils
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FossilsTest {
    private fun overlay(table: Fossils = Fossils.VANILLA): ByteArray {
        val d = ByteArray(0x100)
        table.fossils.forEachIndexed { i, f ->
            d[AT + 4 * i] = f.item.toByte(); d[AT + 4 * i + 1] = (f.item shr 8).toByte()
            d[AT + 4 * i + 2] = f.species.toByte(); d[AT + 4 * i + 3] = (f.species shr 8).toByte()
        }
        return d
    }

    @Test
    fun theVanillaTableReadsAndWritesBackTheSame() {
        assertEquals(Fossils.VANILLA, (Fossils.parse(overlay()) as FoxResult.Ok).value)
        assertContentEquals(overlay(), (Fossils.VANILLA.applyTo(overlay()) as FoxResult.Ok).value)
    }

    @Test
    fun anEditedSpeciesIsStillFoundAndTouchesOnlyTheTable() {
        val edited = Fossils(Fossils.VANILLA.fossils.mapIndexed { i, f -> if (i == 1) Fossil(f.item, 493) else f })
        val bytes = (edited.applyTo(overlay()) as FoxResult.Ok).value
        assertEquals(edited, (Fossils.parse(bytes) as FoxResult.Ok).value)
        assertTrue(bytes.indices.filter { bytes[it] != overlay()[it] }.all { it in AT until AT + 28 })
    }

    @Test
    fun badSpeciesAndMovedItemsAreRefused() {
        assertIs<FoxResult.Err>(Fossils(Fossils.VANILLA.fossils.map { it.copy(species = 0) }).applyTo(overlay()))
        assertIs<FoxResult.Err>(Fossils(Fossils.VANILLA.fossils.map { it.copy(species = 494) }).applyTo(overlay()))
        assertIs<FoxResult.Err>(Fossils(Fossils.VANILLA.fossils.mapIndexed { i, f -> if (i == 0) f.copy(item = 1) else f }).applyTo(overlay()))
        assertIs<FoxResult.Err>(Fossils.parse(ByteArray(0x100)))
    }

    private companion object { const val AT = 0x30 }
}
