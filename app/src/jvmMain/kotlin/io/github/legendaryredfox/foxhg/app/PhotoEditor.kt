package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable
import io.github.legendaryredfox.foxhg.core.data.photo.PhotoLocation
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** One label per map header ("060  New Bark Town"), for the map pickers. */
fun mapHeaderLabels(project: RomProject): List<String> {
    val headers = (MapHeaderTable.load(project) as? FoxResult.Ok)?.value ?: emptyList()
    val locations = project.names?.locations ?: emptyList()
    return headers.mapIndexed { i, h -> "%03d  %s".format(i, locations.getOrNull(h.locationName)?.takeIf { it.isNotBlank() } ?: "Map") }
}

/** "07  Route 34" for photo location 7: the place of its map header. */
fun photoLabel(project: RomProject): (Int) -> String {
    val maps = mapHeaderLabels(project)
    val photos = (PhotoLocation.load(project) as? FoxResult.Ok)?.value?.records ?: emptyList()
    return { i -> "%02d  %s".format(i, photos.getOrNull(i)?.let { maps.getOrNull(it.header)?.drop(5) } ?: "Photo $i") }
}

/** The places the Photo Album knows (`a/2/5/4`): the map and global position of the player in the picture, the album icon and the NPC next to the player. */
@Composable
fun PhotoEditor(model: RecordEditorModel<PhotoLocation>, mapLabels: List<String>, modifier: Modifier = Modifier) {
    RecordListDetail(model, modifier) { p ->
        EditorHeader(model.selectedIndex.toString(), model.label(model.selectedIndex).drop(4))
        SectionCard(stringResource(Res.string.photo_place), subtitle = stringResource(Res.string.photo_where_the_player_stands_in_the_pic)) {
            FieldFlow {
                PickerField(stringResource(Res.string.new_save_map), p.header, mapLabels, { v -> model.update { it.copy(header = v) } }, Modifier.width(260.dp))
                NumberField(stringResource(Res.string.photo_player_x), p.x, 0..65535, { v -> model.update { it.copy(x = v) } }, Modifier.width(100.dp))
                NumberField(stringResource(Res.string.photo_player_y), p.y, 0..65535, { v -> model.update { it.copy(y = v) } }, Modifier.width(100.dp))
            }
            Text(stringResource(Res.string.photo_x_and_y_are_global_positions_the_w), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(Res.string.photo_picture), subtitle = stringResource(Res.string.photo_the_icon_in_the_photo_album_and_wh)) {
            FieldFlow {
                PickerField(stringResource(Res.string.photo_album_icon), p.icon, PhotoLocation.ICONS, { v -> model.update { it.copy(icon = v) } }, Modifier.width(220.dp))
                NumberField(stringResource(Res.string.photo_overworld_entry_0_party), p.npc, 0..65535, { v -> model.update { it.copy(npc = v) } }, Modifier.width(210.dp))
            }
            Text(
                stringResource(Res.string.photo_with_an_overworld_entry_of_0_the_p),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard(stringResource(Res.string.photo_unknown), subtitle = stringResource(Res.string.photo_kept_as_they_are_in_the_game_the_c)) {
            FieldFlow {
                NumberField(stringResource(Res.string.photo_constant), p.unknown, 0..65535, { v -> model.update { it.copy(unknown = v) } }, Modifier.width(110.dp))
                NumberField(stringResource(Res.string.photo_parameter_1), p.param1, 0..65535, { v -> model.update { it.copy(param1 = v) } }, Modifier.width(110.dp))
                NumberField(stringResource(Res.string.photo_parameter_2), p.param2, 0..65535, { v -> model.update { it.copy(param2 = v) } }, Modifier.width(110.dp))
            }
        }
    }
}
