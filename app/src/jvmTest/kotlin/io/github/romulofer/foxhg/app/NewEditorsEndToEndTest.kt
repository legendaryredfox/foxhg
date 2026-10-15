package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.eggmove.EggMoves
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.event.Warp
import io.github.romulofer.foxhg.core.data.item.ItemData
import io.github.romulofer.foxhg.core.data.tmhm.TmHmTable
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassGenders
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassPrizes
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Edits through the item, egg move, TM/HM and trainer class models on the real ROM, builds, reopens, reads back. */
class NewEditorsEndToEndTest {
    @Test
    fun itemEggMoveTmAndClassEditsSurviveABuild() {
        if (!romAvailable()) return
        val app = testModel()
        val built = tempFile("foxhg-e2e-new", ".nds")
        try {
            app.openRom(romPath)
            val project = app.project!!

            val items = RecordEditorModel(project, ItemData.SPEC, RecordEditorModel.itemLabel(project.names), app::projectChanged, firstIndex = 1)
            items.select(4)
            items.update { it.copy(price = 123, battlePocket = 3, partyParams = it.partyParams.toMutableList().also { l -> l[0] = 9 }) }

            val eggs = RecordEditorModel(project, EggMoves.SPEC, { "%03d".format(it) }, app::projectChanged)
            eggs.update { it.withMoves(25, listOf(85, 98)) } // Pikachu: not in the vanilla list
            assertEquals(listOf(85, 98), eggs.selected!!.movesOf(25))

            val events = RecordEditorModel(project, EventFile.SPEC, { "%03d".format(it) }, app::projectChanged)
            val target = events.records.indexOfFirst { it.warps.isNotEmpty() && it.overworlds.isNotEmpty() }
            events.select(target)
            val before = events.selected!!
            events.update { it.copy(warps = it.warps + Warp(5, 6, 7, 8, 9), overworlds = it.overworlds.mapIndexed { i, o -> if (i == 0) o.copy(x = o.x + 1) else o }) }
            assertTrue(events.isEdited(target))

            val tm = TmHmEditorModel(project, onChanged = app::projectChanged)
            tm.setMove(0, 1)
            val classes = TrainerClassEditorModel(project, onChanged = app::projectChanged)
            classes.setGender(2, 1)
            classes.setPrize(2, 9)

            app.build(built)
            assertNull(app.error, app.error)

            FileRomSource(built).use { source ->
                val reopened = (RomProject.open(source) as FoxResult.Ok).value
                val item = (ItemData.load(reopened) as FoxResult.Ok).value.records[4]
                assertEquals(listOf(123, 3, 9), listOf(item.price, item.battlePocket, item.partyParams[0]))
                val egg = (EggMoves.load(reopened) as FoxResult.Ok).value.records.single()
                assertEquals(listOf(85, 98), egg.movesOf(25))
                val ev = (EventFile.load(reopened) as FoxResult.Ok).value.records[target]
                assertEquals(before.warps + Warp(5, 6, 7, 8, 9), ev.warps)
                assertEquals(before.overworlds[0].x + 1, ev.overworlds[0].x)
                assertEquals(1, (TmHmTable.load(reopened) as FoxResult.Ok).value.moves[0])
                assertEquals(1, (TrainerClassGenders.load(reopened) as FoxResult.Ok).value.genders[2])
                assertEquals(9, (TrainerClassPrizes.load(reopened) as FoxResult.Ok).value.entries[2].multiplier)
            }
        } finally {
            app.close()
            Files.deleteIfExists(built)
        }
    }
}
