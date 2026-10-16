package io.github.legendaryredfox.foxhg.core.data.personal

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.ByteArrayRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.testing.SyntheticRom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PersonalTableTest {
    // The synthetic ROM names its files a/<name>, so give it one personal file at that path and a stand-in constant.
    private fun project(): RomProject {
        val record = ByteArray(PersonalData.SIZE) { (it + 1).toByte() }
        val narc = Narc(listOf(record, record.copyOf().also { it[0] = 99 })).toBytes()
        val rom = SyntheticRom.build(listOf("personal" to narc), pathPrefix = "a/0/0", pathName = "2")
        return (RomProject.open(ByteArrayRomSource(rom)) as FoxResult.Ok).value
    }

    @Test
    fun loadsEditsAndSaves() {
        val p = project()
        val table = (PersonalTable.load(p) as FoxResult.Ok).value
        assertEquals(2, table.records.size)
        assertEquals(99, table.records[1].baseHp)

        assertIs<FoxResult.Ok<Unit>>(table.save(p))
        assertFalse(p.isDirty, "saving an unchanged table must not create an edit")

        val edited = table.with(1, table.records[1].copy(baseHp = 120))
        assertIs<FoxResult.Ok<Unit>>(edited.save(p))
        assertTrue(p.isDirty)
        assertEquals(120, (PersonalTable.load(p) as FoxResult.Ok).value.records[1].baseHp)
        assertEquals(setOf(HgssLayout.PERSONAL), p.editedPaths)
    }
}
