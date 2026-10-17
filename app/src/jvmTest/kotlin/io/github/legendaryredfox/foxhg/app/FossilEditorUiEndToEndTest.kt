package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.fossil.Fossils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The fossils screen: a species written into overlay 21, kept by a rebuild, and the restore button. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class FossilEditorUiEndToEndTest {
    @Test
    fun editingAFossilOnItsScreenWritesTheTable() = withHeartGoldApp { model, project ->
        val table = { (Fossils.load(project) as FoxResult.Ok).value }
        open("Fossils")
        pick("Old Amber", "SCEPTILE")
        assertEquals(project.names!!.species.indexOf("SCEPTILE"), table().fossils[0].species)
        assertEquals(Fossils.VANILLA.fossils.drop(1), table().fossils.drop(1), "the other fossils are left alone")

        buildAndReopen(model) { rebuilt ->
            assertEquals(table(), (Fossils.load(rebuilt) as FoxResult.Ok).value)
        }
        tap(onNodeWithTag("fossil-restore"), inScrollPane = false)
        waitForIdle()
        assertEquals(Fossils.VANILLA, table())
        assertTrue(!project.isDirty)
    }
}
