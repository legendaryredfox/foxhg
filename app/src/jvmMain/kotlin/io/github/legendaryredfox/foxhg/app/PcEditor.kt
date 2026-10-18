package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import io.github.legendaryredfox.foxhg.app.ui.ItemPicker
import io.github.legendaryredfox.foxhg.app.ui.NumberField
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerSet
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.save.PcStorageData
import io.github.legendaryredfox.foxhg.core.save.Pokemon

/** The PC boxes of the save: pick a box and a slot, edit the Pokémon in it, move it to the party, rename the box and change its wallpaper. Written to the file only by the Write button. */
@Composable
fun SavePcScreen(model: SaveEditorModel, names: GameNames?, loadRules: () -> SaveRules?, modifier: Modifier = Modifier) {
    val pc = model.pc
    val rules = remember(names) { loadRules() }
    DetailPane(modifier.padding(16.dp)) {
        EditorHeader("", model.path.fileName.toString(), trailing = { SaveStatus(model) })
        if (pc == null) {
            Text(stringResource(Res.string.pc_this_save_has_no_sound_storage_blo), color = MaterialTheme.colorScheme.error)
            return@DetailPane
        }
        var boxIndex by remember { mutableStateOf(pc.currentBox.coerceIn(0, PcStorageData.BOXES - 1)) }
        var slotIndex by remember { mutableStateOf(0) }
        val box = boxIndex
        val speciesNames = remember(names) { names?.species ?: List(494) { "#$it" } }
        val moveNames = remember(names) { names?.moves ?: List(468) { "#$it" } }
        val itemNames = remember(names) { names?.items ?: List(540) { "#$it" } }
        SectionCard(stringResource(Res.string.pc_boxes), subtitle = stringResource(Res.string.pc_x_pok_mon_in_the_pc, pc.total)) {
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until PcStorageData.BOXES) {
                    val label = "${pc.boxName(i)} ${pc.occupied(i).size}/${PcStorageData.PER_BOX}"
                    if (i == box) Button(onClick = { boxIndex = i; slotIndex = 0 }, modifier = Modifier.testTag("pc-box:$i")) { Text(label) }
                    else OutlinedButton(onClick = { boxIndex = i; slotIndex = 0 }, modifier = Modifier.testTag("pc-box:$i")) { Text(label) }
                }
            }
        }
        SectionCard(stringResource(Res.string.pc_this_box)) {
            FieldFlow {
                var name by remember(box, pc.boxName(box)) { mutableStateOf(pc.boxName(box)) }
                LabeledText(stringResource(Res.string.save_name_up_to_x_characters, PcStorageData.MAX_NAME), name) { text ->
                    name = text
                    if (text.isNotEmpty()) model.updatePc { it.withBoxName(box, text) }
                }
                val wallpapers = PcStorageData.WALLPAPERS.entries.toList()
                val current = wallpapers.indexOfFirst { it.key == pc.wallpapers[box] }.coerceAtLeast(0)
                PickerField(stringResource(Res.string.pc_wallpaper), current, wallpapers.map { it.value }, { v -> model.updatePc { it.withWallpaper(box, wallpapers[v].key) } }, Modifier.width(200.dp))
            }
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until PcStorageData.PER_BOX) {
                    val mon = pc.boxes[box][i]
                    val label = if (mon.isEmpty) "${i + 1}. -" else "${i + 1}. ${mon.nickname.ifBlank { speciesNames.getOrElse(mon.species) { "#${mon.species}" } }}"
                    if (i == slotIndex) Button(onClick = { slotIndex = i }, modifier = Modifier.testTag("pc-slot:$i")) { Text(label) }
                    else OutlinedButton(onClick = { slotIndex = i }, modifier = Modifier.testTag("pc-slot:$i")) { Text(label) }
                }
            }
        }
        val slot = slotIndex
        val mon = pc.boxes[box][slot]
        fun edit(change: (Pokemon) -> Pokemon) = model.updatePc { FoxResult.Ok(it.placing(box, slot, change(it.boxes[box][slot]))) }
        if (mon.isEmpty) {
            SectionCard(stringResource(Res.string.pc_slot_x_is_empty, slot + 1), subtitle = stringResource(Res.string.pc_a_new_pok_mon_needs_a_personality)) {
                Text(stringResource(Res.string.pc_empty), style = MaterialTheme.typography.bodyMedium)
            }
            return@DetailPane
        }
        SectionCard(stringResource(Res.string.group_pokemon), subtitle = stringResource(Res.string.pc_a_box_does_not_keep_the_level_or_s)) {
            FieldFlow {
                SpeciesPicker(stringResource(Res.string.evolution_species), mon.species, speciesNames, { v -> edit { it.withSpecies(v) } }, Modifier.width(220.dp))
                var nickname by remember(box, slot, mon.nickname) { mutableStateOf(mon.nickname) }
                LabeledText(stringResource(Res.string.party_nickname_up_to_10_characters_empty), nickname) { text ->
                    nickname = text
                    mon.withNickname(text).let { r -> if (r is FoxResult.Ok) edit { r.value } }
                }
                ItemPicker(stringResource(Res.string.starter_held_item), mon.heldItem, itemNames, { v -> edit { it.withHeldItem(v) } }, Modifier.width(220.dp))
            }
            FieldFlow {
                NumberField(stringResource(Res.string.party_experience), mon.experience.toInt(), 0..2_000_000, { v -> edit { it.withExperience(v.toLong()) } }, Modifier.width(130.dp))
                NumberField(stringResource(Res.string.personal_friendship), mon.friendship, 0..255, { v -> edit { it.withFriendship(v) } }, Modifier.width(100.dp))
                rules?.level(mon.species, mon.experience)?.let { Chip(stringResource(Res.string.growth_level_x, it)) }
                Chip("Nature ${BattleTowerSet.NATURES.getOrElse(mon.nature) { "?" }}")
                Chip(stringResource(Res.string.party_ot_x, mon.otName))
                if (mon.isEgg) Chip(stringResource(Res.string.party_egg), emphasis = true)
            }
        }
        SectionCard(stringResource(Res.string.editor_moves)) {
            for (i in 0 until 4) {
                FieldFlow {
                    PickerField(stringResource(Res.string.trainer_move_x, i + 1), mon.moves[i], moveNames, { v -> edit { it.withMove(i, v, if (v == 0) 0 else if (it.moves[i] == 0 || it.pp[i] == 0) 5 else it.pp[i]) } }, Modifier.width(220.dp))
                    NumberField(stringResource(Res.string.move_pp), mon.pp[i], 0..99, { v -> edit { it.withMove(i, it.moves[i], v) } }, Modifier.width(80.dp), tag = "PP ${i + 1}")
                }
            }
        }
        SectionCard(stringResource(Res.string.party_ivs_and_evs)) {
            FieldFlow {
                BattleTowerSet.EV_STATS.forEachIndexed { i, name -> NumberField(stringResource(Res.string.party_iv_x, name), mon.ivs[i], 0..31, { v -> edit { it.withIv(i, v) } }, Modifier.width(100.dp), tag = "IV $i") }
            }
            FieldFlow {
                BattleTowerSet.EV_STATS.forEachIndexed { i, name -> NumberField(stringResource(Res.string.party_ev_x, name), mon.evs[i], 0..255, { v -> edit { it.withEv(i, v) } }, Modifier.width(100.dp), tag = "EV $i") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { model.withdrawFromPc(box, slot, rules) }, modifier = Modifier.testTag("pc-withdraw")) { Text(stringResource(Res.string.pc_move_to_the_party)) }
                OutlinedButton(onClick = { model.updatePc { FoxResult.Ok(it.clearing(box, slot)) } }, modifier = Modifier.testTag("pc-release")) { Text(stringResource(Res.string.pc_release)) }
            }
        }
    }
}
