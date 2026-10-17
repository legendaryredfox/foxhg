package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.patch.ShinyRates
import io.github.legendaryredfox.foxhg.core.patch.ShinyRatesPatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shiny rates screen: install the code, set each rate on its own, keep them through a rebuild. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class ShinyRatesEditorUiEndToEndTest {
    @Test
    fun installingTheCodeAndSettingTheRatesOnTheirScreen() = withHeartGoldApp { model, project ->
        open("Shiny rates")
        tap(onNodeWithTag("shiny-install"), inScrollPane = false)
        waitForIdle()
        assertEquals(ShinyRates.NONE, (ShinyRatesPatch.load(project) as FoxResult.Ok).value)

        onNodeWithTag("number:Shiny wild").typeNumber(1000)
        waitForIdle()
        onNodeWithTag("number:Shiny trainer").typeNumber(250)
        waitForIdle()
        val rates = (ShinyRatesPatch.load(project) as FoxResult.Ok).value
        assertEquals(ShinyRates.fromBasisPoints(1000), rates.wild)
        assertEquals(ShinyRates.fromBasisPoints(250), rates.trainer)
        assertEquals(0, rates.scripted)
        assertEquals(0, rates.gift)
        assertTrue(project.isDirty)

        buildAndReopen(model) { rebuilt ->
            assertEquals(rates, (ShinyRatesPatch.load(rebuilt) as FoxResult.Ok).value)
        }
    }
}
