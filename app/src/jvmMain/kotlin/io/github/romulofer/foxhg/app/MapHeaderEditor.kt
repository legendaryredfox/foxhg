package io.github.romulofer.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.app.ui.Chip
import io.github.romulofer.foxhg.app.ui.EditorHeader
import io.github.romulofer.foxhg.app.ui.FieldFlow
import io.github.romulofer.foxhg.app.ui.NumberField
import io.github.romulofer.foxhg.app.ui.PickerField
import io.github.romulofer.foxhg.app.ui.SectionCard
import io.github.romulofer.foxhg.app.ui.SoundButton
import io.github.romulofer.foxhg.app.ui.ToggleChip
import io.github.romulofer.foxhg.core.data.mapheader.MapHeader

@Composable
fun MapHeaderEditor(model: MapHeaderEditorModel, modifier: Modifier = Modifier) {
    model.loadError?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    Row(modifier.fillMaxSize()) {
        ListPane(
            rows = model.rows(),
            selected = model.selectedIndex,
            search = model.search,
            onSearch = { model.search = it },
            placeholder = "Search maps…",
            label = model::label,
            onSelect = model::select,
            isEdited = model::isEdited,
            detail = { i -> model.all.getOrNull(i)?.let { "matrix ${it.matrix}" } },
        )
        model.selected?.let { h -> DetailPane { Detail(model, h) } }
    }
}

@Composable
private fun Detail(model: MapHeaderEditorModel, h: MapHeader) {
    val locations = model.names?.locations ?: emptyList()
    EditorHeader(model.selectedIndex.toString(), model.locationName(model.selectedIndex), trailing = {
        if (h.wildEncounterFile != MapHeader.NO_ENCOUNTERS) Chip("Wild: ${nameOf(model.encounterAreas.label(h.wildEncounterFile))}")
    })

    SectionCard("Place", subtitle = "The name shown when you enter, and where the map sits in the world") {
        FieldFlow {
            PickerField("Location name", h.locationName, locations, { v -> model.update { it.copy(locationName = v) } }, Modifier.width(240.dp))
            NumberField("Area icon", h.areaIcon, 0..15, { v -> model.update { it.withAreaIcon(v) } }, Modifier.width(90.dp))
            NumberField("Location type", h.locationType, 0..15, { v -> model.update { it.withLocationType(v) } }, Modifier.width(110.dp))
            NumberField("World map X", h.worldMapX, 0..63, { v -> model.update { it.withWorldMap(v, it.worldMapY) } }, Modifier.width(100.dp))
            NumberField("World map Y", h.worldMapY, 0..63, { v -> model.update { it.withWorldMap(it.worldMapX, v) } }, Modifier.width(100.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Region", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToggleChip("Kanto", h.isKanto, { v -> model.update { it.withKanto(v) } })
        }
    }

    SectionCard("What the map uses", subtitle = "Numbers are file indexes in the ROM; the matching editors open them") {
        FieldFlow {
            NumberField("Matrix", h.matrix, 0..65535, { v -> model.update { it.copy(matrix = v) } }, Modifier.width(100.dp))
            NumberField("Area data", h.areaData, 0..255, { v -> model.update { it.copy(areaData = v) } }, Modifier.width(100.dp))
            NumberField("Script file", h.script, 0..65535, { v -> model.update { it.copy(script = v) } }, Modifier.width(110.dp))
            NumberField("Level script", h.levelScript, 0..65535, { v -> model.update { it.copy(levelScript = v) } }, Modifier.width(110.dp))
            NumberField("Text archive", h.textArchive, 0..65535, { v -> model.update { it.copy(textArchive = v) } }, Modifier.width(110.dp))
            NumberField("Event file", h.eventFile, 0..65535, { v -> model.update { it.copy(eventFile = v) } }, Modifier.width(110.dp))
            NumberField("Wild file (255 none)", h.wildEncounterFile, 0..255, { v -> model.update { it.copy(wildEncounterFile = v) } }, Modifier.width(150.dp))
        }
    }

    SectionCard("Music and look") {
        FieldFlow {
            NumberField("Day music", h.musicDay, 0..65535, { v -> model.update { it.copy(musicDay = v) } }, Modifier.width(100.dp))
            SoundButton(h.musicDay)
            NumberField("Night music", h.musicNight, 0..65535, { v -> model.update { it.copy(musicNight = v) } }, Modifier.width(100.dp))
            SoundButton(h.musicNight)
            NumberField("Weather", h.weather, 0..127, { v -> model.update { it.withWeather(v) } }, Modifier.width(90.dp))
            NumberField("Camera", h.camera, 0..63, { v -> model.update { it.withCamera(v) } }, Modifier.width(90.dp))
            NumberField("Battle background", h.battleBackground, 0..31, { v -> model.update { it.withBattleBackground(v) } }, Modifier.width(140.dp))
            NumberField("Follower mode", h.followMode, 0..3, { v -> model.update { it.withFollowMode(v) } }, Modifier.width(120.dp))
        }
    }

    SectionCard("Permissions", subtitle = "What the player may do on this map") {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MapHeader.Permission.entries.forEach { p ->
                ToggleChip(p.label, h.permissions and (1 shl p.bit) != 0, { on -> model.update { it.withPermission(p, on) } })
            }
        }
    }
}
