package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.roamer.Roamers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The roamers screen: levels and species written into the ARM9, a species the code can not hold refused, restore. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class RoamerEditorUiEndToEndTest {
    @Test
    fun editingTheRoamersOnTheirScreenWritesTheCode() = withHeartGoldApp { model, project ->
        val roamers = { (Roamers.load(project) as FoxResult.Ok).value }
        open("Roamers")
        onNodeWithTag("number:Raikou level").typeNumber(55)
        waitForIdle()
        assertEquals(55, roamers().roamers[0].level)

        pick("Latios (roamer 3)", "SCEPTILE")
        assertEquals(project.names!!.species.indexOf("SCEPTILE"), roamers().roamers[3].species)

        // 381 (Latios) is odd, so Latias' byte-and-shift place can not hold it
        pick("Latias (roamer 2)", "LATIOS")
        onNodeWithTag("roamer-message").assertIsDisplayed()
        assertEquals(380, roamers().roamers[2].species, "a refused species changes nothing")

        buildAndReopen(model) { rebuilt ->
            val reread = (Roamers.load(rebuilt) as FoxResult.Ok).value
            assertEquals(roamers(), reread)
        }
        tap(onNodeWithTag("roamer-restore"), inScrollPane = false)
        waitForIdle()
        assertEquals(Roamers.VANILLA, roamers())
        assertTrue(!project.isDirty)
    }
}
