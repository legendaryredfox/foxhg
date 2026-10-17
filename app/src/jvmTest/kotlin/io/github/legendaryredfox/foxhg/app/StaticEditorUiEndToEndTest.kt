package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.legendaryredfox.foxhg.core.FoxResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The static Pokémon screen: the scripts' fixed Pokémon listed, a level edited, and the script written. Skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
class StaticEditorUiEndToEndTest {
    @Test
    fun editingALevelOnTheStaticPokemonScreenWritesTheScript() = withHeartGoldApp { _, project ->
        open("Static Pokémon")
        val mewtwo = (io.github.legendaryredfox.foxhg.core.data.staticmon.StaticEncounters.find(project) as FoxResult.Ok).value.first { it.species == 150 && it.level == 70 }
        onNodeWithText("Scripted battles").assertIsDisplayed()
        onNodeWithTag("number:Level ${mewtwo.file}:${mewtwo.index}").typeNumber(65)
        waitForIdle()
        val after = (io.github.legendaryredfox.foxhg.core.data.staticmon.StaticEncounters.find(project) as FoxResult.Ok).value.first { it.file == mewtwo.file && it.index == mewtwo.index }
        assertEquals(listOf(150, 65), listOf(after.species, after.level))
        assertTrue(project.isDirty)
    }

    @Test
    fun aSpeciesThatAScriptPutsInAVariableIsListedPerNumberAndItsLevelEdits() = withHeartGoldApp { _, project ->
        open("Static Pokémon")
        fun all() = (io.github.legendaryredfox.foxhg.core.data.staticmon.StaticEncounters.find(project) as FoxResult.Ok).value
        val hooh = all().first { it.viaVariable && it.species == 250 }
        assertTrue(hooh.index != hooh.commandIndex && !hooh.speciesIsVariable)
        val latias = all().first { it.viaVariable && it.species == 380 }
        onNodeWithTag("number:Level ${latias.file}:${latias.index}").typeNumber(44)
        waitForIdle()
        assertEquals(listOf(44, 44), all().filter { it.file == latias.file && it.viaVariable }.map { it.level }, "both numbers share the command's level")
        assertTrue(project.isDirty)
    }
}
