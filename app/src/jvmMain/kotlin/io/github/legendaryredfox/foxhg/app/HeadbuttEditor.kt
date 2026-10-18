package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
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
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.app.ui.SpeciesPicker
import io.github.legendaryredfox.foxhg.core.data.headbutt.HeadbuttEncounter
import io.github.legendaryredfox.foxhg.core.data.headbutt.HeadbuttFile
import io.github.legendaryredfox.foxhg.core.data.headbutt.HeadbuttTree
import io.github.legendaryredfox.foxhg.core.data.headbutt.HeadbuttTreeGroup
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** Headbutt trees: each map header has three Pokémon tables (common, rare, special) and the tiles its trees stand on (a file of `a/2/5/2`). */
class HeadbuttEditorModel(project: RomProject, onChanged: () -> Unit = {}, first: Int = 0, onSelected: (Int) -> Unit = {}) {
    private val labels = EventLabels(project)
    val files = RecordEditorModel(project, HeadbuttFile.SPEC, { i -> "%03d  %s".format(i, labels.mapName(i)) }, onChanged, firstIndex = first, onSelected = onSelected)

    fun treeCount(file: HeadbuttFile): Int = file.normalGroups.size + file.specialGroups.size

    fun setNormal(slot: Int, change: (HeadbuttEncounter) -> HeadbuttEncounter) = files.update { it.withNormal(slot, change(it.normal[slot])) }

    fun setSpecial(slot: Int, change: (HeadbuttEncounter) -> HeadbuttEncounter) = files.update { it.withSpecial(slot, change(it.special[slot])) }

    fun setTree(special: Boolean, group: Int, tile: Int, tree: HeadbuttTree) = files.update { f ->
        val groups = if (special) f.specialGroups else f.normalGroups
        val changed = groups.mapIndexed { i, g -> if (i == group) g.with(tile, tree) else g }
        if (special) f.copy(specialGroups = changed) else f.copy(normalGroups = changed)
    }

    fun addGroup(special: Boolean) = files.update { f ->
        if (special) f.copy(specialGroups = f.specialGroups + HeadbuttTreeGroup.EMPTY, stub = false)
        else f.copy(normalGroups = f.normalGroups + HeadbuttTreeGroup.EMPTY, stub = false)
    }

    fun removeGroup(special: Boolean, group: Int) = files.update { f ->
        if (special) f.copy(specialGroups = f.specialGroups.filterIndexed { i, _ -> i != group })
        else f.copy(normalGroups = f.normalGroups.filterIndexed { i, _ -> i != group })
    }

    companion object { const val MAX_GROUPS = 255 }
}

@Composable
fun HeadbuttEditor(model: HeadbuttEditorModel, modifier: Modifier = Modifier) {
    val species = model.files.names?.species ?: emptyList()
    RecordListDetail(
        model.files, modifier,
        rowDetail = { i -> model.files.records.getOrNull(i)?.takeIf { it.hasTrees }?.let { plural(model.treeCount(it), "tree") } },
    ) { file ->
        val index = model.files.selectedIndex
        EditorHeader(index.toString(), nameOf(model.files.label(index)), trailing = {
            Chip(if (file.hasTrees) plural(model.treeCount(file), "tree group") else stringResource(Res.string.headbutt_no_trees))
        })
        if (!file.hasTrees) {
            Text(
                stringResource(Res.string.headbutt_this_map_has_no_headbutt_trees_so),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TableCard(stringResource(Res.string.headbutt_common_a), stringResource(Res.string.headbutt_most_normal_trees_use_this_table), "Common", file.common, species) { slot, change -> model.setNormal(slot, change) }
        TableCard(stringResource(Res.string.headbutt_rare_b), stringResource(Res.string.headbutt_some_normal_trees_use_this_one_ins), "Rare", file.rare, species) { slot, change ->
            model.setNormal(HeadbuttFile.TABLE_SLOTS + slot, change)
        }
        TableCard(stringResource(Res.string.headbutt_special_c), stringResource(Res.string.headbutt_trees_in_the_special_groups_always), "Special", file.special, species) { slot, change -> model.setSpecial(slot, change) }
        GroupsCard(stringResource(Res.string.headbutt_normal_trees), "Normal", false, file.normalGroups, model)
        GroupsCard(stringResource(Res.string.headbutt_special_trees), "Special", true, file.specialGroups, model)
    }
}

@Composable
private fun TableCard(
    title: String,
    subtitle: String,
    tag: String,
    table: List<HeadbuttEncounter>,
    species: List<String>,
    set: (Int, (HeadbuttEncounter) -> HeadbuttEncounter) -> Unit,
) {
    SectionCard(title, subtitle = stringResource(Res.string.headbutt_x_species_0_is_an_empty_slot_fill, subtitle)) {
        table.forEachIndexed { slot, e ->
            FieldFlow {
                SpeciesPicker("$tag ${slot + 1}", e.species, species, { v -> set(slot) { it.copy(species = v) } }, Modifier.width(240.dp))
                NumberField(stringResource(Res.string.headbutt_min_level), e.minLevel, 0..100, { v -> set(slot) { it.copy(minLevel = v, maxLevel = maxOf(it.maxLevel, v)) } }, Modifier.width(90.dp), tag = "$tag ${slot + 1} min")
                NumberField(stringResource(Res.string.headbutt_max_level), e.maxLevel, 0..100, { v -> set(slot) { it.copy(maxLevel = v, minLevel = minOf(it.minLevel, v)) } }, Modifier.width(90.dp), tag = "$tag ${slot + 1} max")
                Chip("${HeadbuttFile.SLOT_CHANCE[slot]}%")
            }
        }
    }
}

@Composable
private fun GroupsCard(title: String, tag: String, special: Boolean, groups: List<HeadbuttTreeGroup>, model: HeadbuttEditorModel) {
    SectionCard(
        title,
        subtitle = stringResource(Res.string.headbutt_x_a_group_is_one_tree_which_can_be, pluralStringResource(Res.plurals.plural_group, groups.size, groups.size), HeadbuttTreeGroup.SIZE),
        actions = {
            Button(onClick = { model.addGroup(special) }, enabled = groups.size < HeadbuttEditorModel.MAX_GROUPS) { Text(stringResource(Res.string.headbutt_add_tree)) }
        },
    ) {
        groups.forEachIndexed { g, group ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("${g + 1}", emphasis = true)
                Text(
                    group.trees.firstOrNull { !it.isUnused }?.let { "Around ${it.x}, ${it.y}" } ?: "Empty group",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { model.removeGroup(special, g) }) { Text(stringResource(Res.string.learnset_remove)) }
            }
            group.trees.forEachIndexed { t, tree ->
                FieldFlow {
                    NumberField(stringResource(Res.string.headbutt_x), tree.x, 0..0xFFFF, { v -> model.setTree(special, g, t, tree.copy(x = v)) }, Modifier.width(90.dp), tag = "$tag ${g + 1} tile ${t + 1} X")
                    NumberField(stringResource(Res.string.headbutt_y), tree.y, 0..0xFFFF, { v -> model.setTree(special, g, t, tree.copy(y = v)) }, Modifier.width(90.dp), tag = "$tag ${g + 1} tile ${t + 1} Y")
                    Text(
                        if (tree.isUnused) stringResource(Res.string.headbutt_unused) else stringResource(Res.string.headbutt_matrix_x_x_tile_x_x, tree.x / HeadbuttTree.MAP_SIZE, tree.y / HeadbuttTree.MAP_SIZE, tree.x % HeadbuttTree.MAP_SIZE, tree.y % HeadbuttTree.MAP_SIZE),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
