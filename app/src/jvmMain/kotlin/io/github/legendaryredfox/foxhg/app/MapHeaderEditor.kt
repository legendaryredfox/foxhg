package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SoundButton
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeader

@Composable
fun MapHeaderEditor(model: MapHeaderEditorModel, modifier: Modifier = Modifier, onOpen: (EditorKind, Int) -> Unit = { _, _ -> }) {
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
            placeholder = stringResource(Res.string.map_header_search_maps),
            label = model::label,
            onSelect = model::select,
            isEdited = model::isEdited,
            detail = { i -> model.all.getOrNull(i)?.let { "matrix ${it.matrix}" } },
            leading = { i -> AsyncMapPicture(i) { model.pictures.ofHeader(i) } },
        )
        model.selected?.let { h -> DetailPane { Detail(model, h, onOpen) } }
    }
}

@Composable
private fun Detail(model: MapHeaderEditorModel, h: MapHeader, onOpen: (EditorKind, Int) -> Unit) {
    val locations = model.names?.locations ?: emptyList()
    EditorHeader(model.selectedIndex.toString(), model.locationName(model.selectedIndex), trailing = {
        CsvButtons(MapHeader.CSV, "map headers", { model.all }, { model.locationName(it) }) { change -> model.updateAll(change) }
        AsyncMapPicture(model.selectedIndex, 96.dp) { model.pictures.ofHeader(model.selectedIndex) }
        if (h.wildEncounterFile != MapHeader.NO_ENCOUNTERS) Chip(stringResource(Res.string.map_header_wild_x, nameOf(model.encounterAreas.label(h.wildEncounterFile))))
    })

    SectionCard(stringResource(Res.string.photo_place), subtitle = stringResource(Res.string.map_header_the_name_shown_when_you_enter_and)) {
        FieldFlow {
            PickerField(stringResource(Res.string.map_header_location_name), h.locationName, locations, { v -> model.update { it.copy(locationName = v) } }, Modifier.width(240.dp))
            NumberField(stringResource(Res.string.map_header_area_icon), h.areaIcon, 0..15, { v -> model.update { it.withAreaIcon(v) } }, Modifier.width(90.dp))
            NumberField(stringResource(Res.string.map_header_location_type), h.locationType, 0..15, { v -> model.update { it.withLocationType(v) } }, Modifier.width(110.dp))
            NumberField(stringResource(Res.string.map_header_world_map_x), h.worldMapX, 0..63, { v -> model.update { it.withWorldMap(v, it.worldMapY) } }, Modifier.width(100.dp))
            NumberField(stringResource(Res.string.map_header_world_map_y), h.worldMapY, 0..63, { v -> model.update { it.withWorldMap(it.worldMapX, v) } }, Modifier.width(100.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.map_header_region), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToggleChip(stringResource(Res.string.map_header_kanto), h.isKanto, { v -> model.update { it.withKanto(v) } })
        }
    }

    SectionCard(stringResource(Res.string.map_header_what_the_map_uses), subtitle = stringResource(Res.string.map_header_numbers_are_file_indexes_in_the_ro)) {
        FieldFlow {
            NumberField(stringResource(Res.string.map_header_matrix), h.matrix, 0..65535, { v -> model.update { it.copy(matrix = v) } }, Modifier.width(100.dp))
            TextButton(onClick = { onOpen(EditorKind.MATRICES, h.matrix) }) { Text(stringResource(Res.string.map_header_open_matrix)) }
            NumberField(stringResource(Res.string.map_header_area_data), h.areaData, 0..255, { v -> model.update { it.copy(areaData = v) } }, Modifier.width(100.dp))
            NumberField(stringResource(Res.string.map_header_script_file), h.script, 0..65535, { v -> model.update { it.copy(script = v) } }, Modifier.width(110.dp))
            TextButton(onClick = { onOpen(EditorKind.SCRIPTS, h.script) }) { Text(stringResource(Res.string.map_header_open_script_file)) }
            NumberField(stringResource(Res.string.map_header_level_script), h.levelScript, 0..65535, { v -> model.update { it.copy(levelScript = v) } }, Modifier.width(110.dp))
            NumberField(stringResource(Res.string.map_header_text_archive), h.textArchive, 0..65535, { v -> model.update { it.copy(textArchive = v) } }, Modifier.width(110.dp))
            TextButton(onClick = { onOpen(EditorKind.TEXT, h.textArchive * 100000) }) { Text(stringResource(Res.string.map_header_open_text_archive)) }
            NumberField(stringResource(Res.string.map_header_event_file), h.eventFile, 0..65535, { v -> model.update { it.copy(eventFile = v) } }, Modifier.width(110.dp))
            TextButton(onClick = { onOpen(EditorKind.EVENTS, h.eventFile) }) { Text(stringResource(Res.string.map_header_open_event_file)) }
            NumberField(stringResource(Res.string.map_header_wild_file_255_none), h.wildEncounterFile, 0..255, { v -> model.update { it.copy(wildEncounterFile = v) } }, Modifier.width(150.dp))
            if (h.wildEncounterFile != MapHeader.NO_ENCOUNTERS) TextButton(onClick = { onOpen(EditorKind.ENCOUNTERS, h.wildEncounterFile) }) { Text(stringResource(Res.string.map_header_open_wild_file)) }
        }
    }

    SectionCard(stringResource(Res.string.map_header_music_and_look)) {
        FieldFlow {
            NumberField(stringResource(Res.string.map_header_day_music), h.musicDay, 0..65535, { v -> model.update { it.copy(musicDay = v) } }, Modifier.width(100.dp))
            SoundButton(h.musicDay)
            NumberField(stringResource(Res.string.map_header_night_music), h.musicNight, 0..65535, { v -> model.update { it.copy(musicNight = v) } }, Modifier.width(100.dp))
            SoundButton(h.musicNight)
            NumberField(stringResource(Res.string.map_header_weather), h.weather, 0..127, { v -> model.update { it.withWeather(v) } }, Modifier.width(90.dp))
            NumberField(stringResource(Res.string.map_header_camera), h.camera, 0..63, { v -> model.update { it.withCamera(v) } }, Modifier.width(90.dp))
            NumberField(stringResource(Res.string.map_header_battle_background), h.battleBackground, 0..31, { v -> model.update { it.withBattleBackground(v) } }, Modifier.width(140.dp))
            NumberField(stringResource(Res.string.map_header_follower_mode), h.followMode, 0..3, { v -> model.update { it.withFollowMode(v) } }, Modifier.width(120.dp))
        }
    }

    SectionCard(stringResource(Res.string.map_header_permissions), subtitle = stringResource(Res.string.map_header_what_the_player_may_do_on_this_map)) {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MapHeader.Permission.entries.forEach { p ->
                ToggleChip(p.label, h.permissions and (1 shl p.bit) != 0, { on -> model.update { it.withPermission(p, on) } })
            }
        }
    }
}
