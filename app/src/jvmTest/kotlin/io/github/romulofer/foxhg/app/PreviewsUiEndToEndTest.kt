package io.github.romulofer.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import kotlin.test.Test
import kotlin.test.assertTrue

/** The picture previews show up in the editors: trainer class sprites, map thumbnails, overworld sprites. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class PreviewsUiEndToEndTest {
    @Test
    fun trainerClassPicturesAppearInTheTrainersList() = withHeartGoldApp { _, _ ->
        open("Trainers")
        assertTrue(onAllNodesWithTag("preview:image", useUnmergedTree = true).fetchSemanticsNodes().size >= 5, "list rows and the header carry trainer pictures")
    }

    @Test
    fun mapThumbnailsAppearInTheMapsList() = withHeartGoldApp { _, _ ->
        open("Maps")
        assertTrue(onAllNodesWithTag("map-thumbnail", useUnmergedTree = true).fetchSemanticsNodes().size >= 5, "list rows carry map thumbnails")
    }

    @Test
    fun overworldSpritesAppearOnTheEventsScreen() = withHeartGoldApp { _, _ ->
        open("Events")
        clickFirstText("Burned Tower")
        waitForIdle()
        assertTrue(onAllNodesWithTag("preview:image", useUnmergedTree = true).fetchSemanticsNodes().size >= 5, "the overworld objects show their sprites")
    }

    @Test
    fun mapHeaderAndMatrixListsShowMapPictures() = withHeartGoldApp { _, _ ->
        for (screen in listOf("Map headers", "Matrices")) {
            open(screen)
            assertTrue(onAllNodesWithTag("map-thumbnail", useUnmergedTree = true).fetchSemanticsNodes().size >= 5, "$screen rows carry map pictures")
        }
    }

    @Test
    fun battlePicturesAppearInThePokemonHeaders() = withHeartGoldApp { _, _ ->
        for (screen in listOf("Personal data", "Learnsets", "Evolutions", "Egg moves")) {
            open(screen)
            clickFirstText("BULBASAUR")
            waitForIdle()
            assertTrue(onAllNodesWithTag("portraits", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "$screen shows the battle pictures")
        }
    }

    @Test
    fun theMapsScreenShowsThe3dView() = withHeartGoldApp { _, _ ->
        open("Maps")
        waitUntil(timeoutMillis = 20_000) { onAllNodesWithTag("map3d").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(onAllNodesWithTag("map3d").fetchSemanticsNodes().size == 1, "the 3D view of the selected map")
    }
}
