package io.github.legendaryredfox.foxhg.app

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
        SectionCard("Place", subtitle = "Where the player stands in the picture, as the TakePhoto script command uses it") {
            FieldFlow {
                PickerField("Map", p.header, mapLabels, { v -> model.update { it.copy(header = v) } }, Modifier.width(260.dp))
                NumberField("Player X", p.x, 0..65535, { v -> model.update { it.copy(x = v) } }, Modifier.width(100.dp))
                NumberField("Player Y", p.y, 0..65535, { v -> model.update { it.copy(y = v) } }, Modifier.width(100.dp))
            }
            Text("X and Y are global positions (the whole matrix, as in the Events editor).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard("Picture", subtitle = "The icon in the Photo Album and who is next to the player") {
            FieldFlow {
                PickerField("Album icon", p.icon, PhotoLocation.ICONS, { v -> model.update { it.copy(icon = v) } }, Modifier.width(220.dp))
                NumberField("Overworld entry (0 = party)", p.npc, 0..65535, { v -> model.update { it.copy(npc = v) } }, Modifier.width(210.dp))
            }
            Text(
                "With an overworld entry of 0 the picture is a group photo of the party; otherwise it shows the player, the first Pokémon and that overworld NPC, whether or not it is on the map.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard("Unknown", subtitle = "Kept as they are in the game: the constant is 0xFF01 everywhere, the parameters are used by some places and their meaning is not known") {
            FieldFlow {
                NumberField("Constant", p.unknown, 0..65535, { v -> model.update { it.copy(unknown = v) } }, Modifier.width(110.dp))
                NumberField("Parameter 1", p.param1, 0..65535, { v -> model.update { it.copy(param1 = v) } }, Modifier.width(110.dp))
                NumberField("Parameter 2", p.param2, 0..65535, { v -> model.update { it.copy(param2 = v) } }, Modifier.width(110.dp))
            }
        }
    }
}
