package io.github.legendaryredfox.foxhg.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.Chip
import io.github.legendaryredfox.foxhg.app.ui.EditorHeader
import io.github.legendaryredfox.foxhg.app.ui.FieldFlow
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.ToggleChip
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.save.BlockKind
import io.github.legendaryredfox.foxhg.core.save.LocalFieldData
import io.github.legendaryredfox.foxhg.core.save.Location
import io.github.legendaryredfox.foxhg.core.save.movedTo
import io.github.legendaryredfox.foxhg.core.save.PlayerData
import io.github.legendaryredfox.foxhg.core.save.SaveChunks
import io.github.legendaryredfox.foxhg.core.save.SaveFile
import io.github.legendaryredfox.foxhg.core.save.withPlayer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * An open save file. Edits live in memory until [write]: the save is the player's progress, so nothing is written behind their back, and the first
 * write keeps the file that was there as `<name>.bak`.
 */
class SaveEditorModel(val path: Path, file: SaveFile) {
    var file: SaveFile by mutableStateOf(file)
        private set
    private var onDisk: ByteArray = file.toFileBytes()
    var status: String? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    /** True when the save in memory is not the one in the file. */
    val dirty: Boolean get() = !file.toFileBytes().contentEquals(onDisk)

    val player: PlayerData? get() = (PlayerData.read(file) as? FoxResult.Ok)?.value

    val position: LocalFieldData? get() = (LocalFieldData.read(file) as? FoxResult.Ok)?.value

    /** Makes the save continue at [place] (see [movedTo]). */
    fun moveTo(place: Location) {
        when (val r = file.movedTo(place)) {
            is FoxResult.Ok -> { file = r.value; error = null }
            is FoxResult.Err -> error = r.message
        }
    }

    /** Replaces the player data; the reason is shown when the save refuses it. */
    fun updatePlayer(change: (PlayerData) -> PlayerData) {
        val current = player ?: return
        when (val r = file.withPlayer(change(current))) {
            is FoxResult.Ok -> { file = r.value; error = null }
            is FoxResult.Err -> error = r.message
        }
    }

    /** Writes the save to its file, keeping the previous file as `<name>.bak` the first time. */
    fun write(): Boolean {
        val bytes = file.toFileBytes()
        return try {
            val backup = path.resolveSibling(path.fileName.toString() + ".bak")
            if (Files.exists(path) && !Files.exists(backup)) Files.copy(path, backup)
            val temp = path.resolveSibling(path.fileName.toString() + ".part")
            Files.write(temp, bytes)
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            onDisk = bytes
            status = "Wrote ${path.fileName}"
            error = null
            true
        } catch (e: java.io.IOException) {
            error = "Cannot write ${path.fileName}: ${e.message}"
            false
        }
    }

    /** Forgets the edits: the save in memory becomes the file as it is. */
    fun discard() {
        (SaveFile.parse(onDisk) as? FoxResult.Ok)?.let { file = it.value }
        error = null
    }

    companion object {
        /** Reads the save at [path] (a raw `.sav` or a DeSmuME `.dsv`). */
        fun open(path: Path): FoxResult<SaveEditorModel> {
            val bytes = try { Files.readAllBytes(path) } catch (e: java.io.IOException) { return FoxResult.Err("Cannot read $path: ${e.message}") }
            return when (val parsed = SaveFile.parse(bytes)) {
                is FoxResult.Ok -> FoxResult.Ok(SaveEditorModel(path, parsed.value))
                is FoxResult.Err -> parsed
            }
        }
    }
}

private val JOHTO_BADGES = listOf("Zephyr", "Hive", "Plain", "Fog", "Storm", "Mineral", "Glacier", "Rising")
private val KANTO_BADGES = listOf("Boulder", "Cascade", "Thunder", "Rainbow", "Soul", "Marsh", "Volcano", "Earth")

/** What the save file is: its blocks with their counters and checks, and the chunks of the general block. */
@Composable
fun SaveFileScreen(model: SaveEditorModel, modifier: Modifier = Modifier) {
    val file = model.file
    DetailPane(modifier.padding(16.dp)) {
        EditorHeader("", model.path.fileName.toString(), trailing = { SaveStatus(model) })
        SectionCard("Blocks", subtitle = "The save is kept twice (slot 0 and slot 1); the game loads the newest sound copy of each block and writes the other slot next time") {
            file.blocks.forEach { b ->
                val current = file.current(b.kind)?.slot == b.slot
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Slot ${b.slot} ${b.kind.name.lowercase()}", Modifier.width(150.dp), style = MaterialTheme.typography.bodyMedium)
                    Chip(if (b.valid) "sound" else "not a save (blank or damaged)", emphasis = !b.valid)
                    if (b.valid) Chip("saved ${b.footer.count} times")
                    if (current) Chip("loaded by the game", emphasis = true)
                }
            }
        }
        SectionCard("General block chunks", subtitle = "What the 41 chunks of the general block are, from the game's own table (the fields inside are decoded one editor at a time)") {
            SaveChunks.of(BlockKind.GENERAL).forEach { c ->
                Text("%02d  %-32s offset 0x%04X, %d bytes".format(c.id, c.name, c.offset, c.size), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SaveStatus(model: SaveEditorModel) {
    if (model.dirty) Chip("● Not written", emphasis = true, modifier = Modifier.testTag("save-dirty"))
    else model.status?.let { Chip(it) }
    Button(onClick = { model.write() }, enabled = model.dirty, modifier = Modifier.testTag("save-write")) { Text("Write save file") }
    model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

/** The player: name, ID, money, badges, play time and the options that can be set safely. */
@Composable
fun SavePlayerScreen(model: SaveEditorModel, modifier: Modifier = Modifier) {
    val player = model.player
    DetailPane(modifier.padding(16.dp)) {
        EditorHeader("", model.path.fileName.toString(), trailing = { SaveStatus(model) })
        if (player == null) {
            Text("This save has no sound general block, so there is no player to show.", color = MaterialTheme.colorScheme.error)
            return@DetailPane
        }
        SectionCard("Trainer", subtitle = "${if (player.version == 7) "HeartGold" else if (player.version == 8) "SoulSilver" else "Game ${player.version}"}, language ${player.language}. The ID is shown as the Trainer Card shows it; it is not edited here") {
            FieldFlow {
                var name by remember(player.name) { mutableStateOf(player.name) }
                LabeledText("Name (up to ${PlayerData.MAX_NAME} characters)", name) { text ->
                    name = text
                    player.withName(text).let { r -> if (r is FoxResult.Ok) model.updatePlayer { r.value } }
                }
                PickerField("Gender", player.gender.coerceIn(0, 1), PlayerData.GENDERS, { v -> model.updatePlayer { it.copy(gender = v) } }, Modifier.width(140.dp))
                Chip("ID %05d".format(player.visibleId))
            }
            FieldFlow {
                NumberField("Money", player.money.toInt(), 0..PlayerData.MAX_MONEY.toInt(), { v -> model.updatePlayer { it.copy(money = v.toLong()) } }, Modifier.width(130.dp))
                NumberField("Coins", player.coins, 0..50_000, { v -> model.updatePlayer { it.copy(coins = v) } }, Modifier.width(110.dp))
            }
        }
        SectionCard("Badges") {
            Text("Johto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                JOHTO_BADGES.forEachIndexed { i, name -> ToggleChip(name, player.johtoBadges and (1 shl i) != 0, { on -> model.updatePlayer { it.copy(johtoBadges = if (on) it.johtoBadges or (1 shl i) else it.johtoBadges and (1 shl i).inv()) } }) }
            }
            Text("Kanto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KANTO_BADGES.forEachIndexed { i, name -> ToggleChip(name, player.kantoBadges and (1 shl i) != 0, { on -> model.updatePlayer { it.copy(kantoBadges = if (on) it.kantoBadges or (1 shl i) else it.kantoBadges and (1 shl i).inv()) } }) }
            }
        }
        SectionCard("Play time and progress") {
            FieldFlow {
                NumberField("Hours", player.hours, 0..999, { v -> model.updatePlayer { it.copy(hours = v) } }, Modifier.width(90.dp))
                NumberField("Minutes", player.minutes, 0..59, { v -> model.updatePlayer { it.copy(minutes = v) } }, Modifier.width(90.dp))
                NumberField("Seconds", player.seconds, 0..59, { v -> model.updatePlayer { it.copy(seconds = v) } }, Modifier.width(90.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("National Dex", player.nationalDex, { on -> model.updatePlayer { it.copy(flags = if (on) it.flags or 2 else it.flags and 2.inv()) } })
                ToggleChip("Game cleared", player.gameClear, { on -> model.updatePlayer { it.copy(flags = if (on) it.flags or 1 else it.flags and 1.inv()) } })
            }
        }
    }
}

/** A place the save was moved to and the game followed (in the emulator): the standing tile next to a Poke Mart clerk. */
private class Preset(val label: String, val place: Location)

private val PRESETS = listOf(
    Preset("Cherrygrove Poké Mart, badge clerk", Location(68, -1, 3, 6, 2)),
    Preset("Viridian Poké Mart, special clerk (mart 26)", Location(500, -1, 3, 4, 2)),
)

/** Where the player is, and moving the save so Continue starts somewhere else. [mapName] names a map number when a ROM is open. */
@Composable
fun SavePositionScreen(model: SaveEditorModel, mapName: (Int) -> String?, modifier: Modifier = Modifier) {
    val field = model.position
    DetailPane(modifier.padding(16.dp)) {
        EditorHeader("", model.path.fileName.toString(), trailing = { SaveStatus(model) })
        if (field == null) {
            Text("This save has no sound general block, so there is no position to show.", color = MaterialTheme.colorScheme.error)
            return@DetailPane
        }
        val here = field.current
        SectionCard("Where the player is") {
            FieldFlow {
                Chip("Map ${here.map}" + (mapName(here.map)?.let { " - $it" } ?: ""))
                Chip("Tile ${here.x}, ${here.y}")
                Chip("Facing ${Location.DIRECTIONS.getOrElse(here.direction) { "?" }.lowercase()}")
            }
        }
        var map by remember(here) { mutableStateOf(here.map) }
        var x by remember(here) { mutableStateOf(here.x) }
        var y by remember(here) { mutableStateOf(here.y) }
        var direction by remember(here) { mutableStateOf(here.direction.coerceIn(0, 3)) }
        SectionCard(
            "Move the player",
            subtitle = "Continue then starts on this tile, with the map's own NPCs. The map and tile must exist and be walkable: the game does not check, and a wall or a tile outside the map leaves the player where nothing works. Use a tile the player can stand on, as the ones below.",
        ) {
            FieldFlow {
                NumberField("Map", map, 0..65535, { map = it }, Modifier.width(100.dp), tag = "Map")
                NumberField("Tile X", x, 0..1023, { x = it }, Modifier.width(100.dp), tag = "Tile X")
                NumberField("Tile Y", y, 0..1023, { y = it }, Modifier.width(100.dp), tag = "Tile Y")
                PickerField("Facing", direction, Location.DIRECTIONS, { direction = it }, Modifier.width(140.dp))
            }
            mapName(map)?.let { Text("Map $map: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = { model.moveTo(Location(map, -1, x, y, direction)) }, modifier = Modifier.testTag("save-move")) { Text("Move the player here") }
            Text("Places the game was seen to continue at:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PRESETS.forEach { p ->
                OutlinedButton(onClick = { map = p.place.map; x = p.place.x; y = p.place.y; direction = p.place.direction }, modifier = Modifier.testTag("preset:${p.place.map}")) { Text(p.label) }
            }
        }
    }
}

