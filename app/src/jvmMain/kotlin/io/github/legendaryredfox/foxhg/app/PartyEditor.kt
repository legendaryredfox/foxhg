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
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerSet
import io.github.legendaryredfox.foxhg.core.data.growth.GrowthCurve
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalData
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.save.PcStorageData
import io.github.legendaryredfox.foxhg.core.save.Pokemon

/** What the open ROM says about species, for the party editor: base stats for the stat formula and growth curves for experience at a level. */
class SaveRules(private val personal: List<PersonalData>, private val curves: List<GrowthCurve>) {
    fun baseStats(species: Int): List<Int>? =
        personal.getOrNull(species)?.let { listOf(it.baseHp, it.baseAttack, it.baseDefense, it.baseSpeed, it.baseSpAttack, it.baseSpDefense) }

    /** The experience a Pokemon of [species] has at [level], from the growth curve of its growth rate. */
    fun experience(species: Int, level: Int): Long? =
        personal.getOrNull(species)?.let { curves.getOrNull(it.growthRate)?.exp?.getOrNull(level)?.toLong() }

    /** The level a Pokemon of [species] with [experience] has: the highest level whose curve value it reaches, 1 to 100. */
    fun level(species: Int, experience: Long): Int? {
        val exp = personal.getOrNull(species)?.let { curves.getOrNull(it.growthRate)?.exp } ?: return null
        return (100 downTo 1).firstOrNull { l -> exp.getOrNull(l)?.let { it <= experience } == true } ?: 1
    }

    companion object {
        fun load(project: RomProject): SaveRules? {
            val personal = project.rom.read(HgssLayout.PERSONAL)?.let { (Narc.parse(it) as? FoxResult.Ok)?.value }
                ?.files?.map { (PersonalData.parse(it) as? FoxResult.Ok)?.value } ?: return null
            if (personal.any { it == null }) return null
            val curves = (NarcRecords.load(project, GrowthCurve.SPEC) as? FoxResult.Ok)?.value?.records ?: return null
            return SaveRules(personal.filterNotNull(), curves)
        }
    }
}

private val STAT_NAMES = BattleTowerSet.EV_STATS

/** A Pokemon with its level, experience and stats made to agree under [rules] (when a ROM gives them). */
internal fun Pokemon.settled(rules: SaveRules?): Pokemon {
    val base = rules?.baseStats(species) ?: return this
    return recalculated(base)
}

/** The party of the save: pick a slot and edit its species, level, moves, IVs and EVs. Written to the file only by the Write button. */
@Composable
fun SavePartyScreen(model: SaveEditorModel, names: GameNames?, loadRules: () -> SaveRules?, modifier: Modifier = Modifier) {
    val party = model.party
    // read once per visit: a tab change after editing species data in the ROM reads it again
    val rules = remember(names) { loadRules() }
    DetailPane(modifier.padding(16.dp)) {
        EditorHeader("", model.path.fileName.toString(), trailing = { SaveStatus(model) })
        if (party == null) {
            Text(stringResource(Res.string.party_this_save_has_no_sound_general_blo), color = MaterialTheme.colorScheme.error)
            return@DetailPane
        }
        var selected by remember { mutableStateOf(0) }
        val slot = selected.coerceIn(0, maxOf(0, party.count - 1))
        val speciesNames = remember(names) { names?.species ?: List(494) { "#$it" } }
        val moveNames = remember(names) { names?.moves ?: List(468) { "#$it" } }
        val itemNames = remember(names) { names?.items ?: List(540) { "#$it" } }
        SectionCard(stringResource(Res.string.editor_save_party), subtitle = if (party.count == 0) stringResource(Res.string.party_the_party_is_empty) else stringResource(Res.string.party_x_of_x_pok_mon, party.count, party.maxCount)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                party.members.forEachIndexed { i, mon ->
                    val label = "${i + 1}. ${mon.nickname.ifBlank { speciesNames.getOrElse(mon.species) { "#${mon.species}" } }} Lv${mon.level}"
                    if (i == slot) Button(onClick = { selected = i }, modifier = Modifier.testTag("party-slot:$i")) { Text(label) }
                    else OutlinedButton(onClick = { selected = i }, modifier = Modifier.testTag("party-slot:$i")) { Text(label) }
                }
            }
        }
        val mon = party.members.getOrNull(slot) ?: return@DetailPane
        fun edit(change: (Pokemon) -> Pokemon) = model.updateParty { it.with(slot, change(it.members[slot])) }

        SectionCard(stringResource(Res.string.group_pokemon), subtitle = "The personality value, which sets the nature and whether the Pokémon is shiny, is left alone. ${if (rules == null) "Without a ROM the stats are not recalculated: use the stat fields below." else "Changing the species, level, IVs or EVs recalculates the stats."}") {
            FieldFlow {
                SpeciesPicker(stringResource(Res.string.evolution_species), mon.species, speciesNames, { v -> edit { it.withSpecies(v).settled(rules) } }, Modifier.width(220.dp))
                var nickname by remember(slot, mon.nickname) { mutableStateOf(mon.nickname) }
                LabeledText(stringResource(Res.string.party_nickname_up_to_10_characters_empty), nickname) { text ->
                    nickname = text
                    mon.withNickname(text).let { r -> if (r is FoxResult.Ok) edit { r.value } }
                }
                ItemPicker(stringResource(Res.string.starter_held_item), mon.heldItem, itemNames, { v -> edit { it.withHeldItem(v) } }, Modifier.width(220.dp))
            }
            FieldFlow {
                NumberField(stringResource(Res.string.learnset_level), mon.level, 1..100, { v ->
                    edit { m -> m.withLevel(v).let { l -> rules?.experience(l.species, v)?.let { l.withExperience(it) } ?: l }.settled(rules) }
                }, Modifier.width(90.dp))
                NumberField(stringResource(Res.string.party_experience), mon.experience.toInt(), 0..2_000_000, { v -> edit { it.withExperience(v.toLong()) } }, Modifier.width(130.dp))
                NumberField(stringResource(Res.string.personal_friendship), mon.friendship, 0..255, { v -> edit { it.withFriendship(v) } }, Modifier.width(100.dp))
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
        SectionCard(stringResource(Res.string.party_ivs_and_evs), subtitle = stringResource(Res.string.party_ivs_0_to_31_evs_0_to_255_each_the)) {
            FieldFlow {
                STAT_NAMES.forEachIndexed { i, name ->
                    NumberField(stringResource(Res.string.party_iv_x, name), mon.ivs[i], 0..31, { v -> edit { it.withIv(i, v).settled(rules) } }, Modifier.width(100.dp), tag = "IV $i")
                }
            }
            FieldFlow {
                STAT_NAMES.forEachIndexed { i, name ->
                    NumberField(stringResource(Res.string.party_ev_x, name), mon.evs[i], 0..255, { v -> edit { it.withEv(i, v).settled(rules) } }, Modifier.width(100.dp), tag = "EV $i")
                }
            }
            if (mon.evs.sum() > 510) Text(stringResource(Res.string.party_evs_total_x_more_than_the_game_s_5, mon.evs.sum()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        SectionCard(stringResource(Res.string.party_stats), subtitle = stringResource(Res.string.party_as_stored_in_the_save_the_game_sho)) {
            FieldFlow {
                NumberField(stringResource(Res.string.party_hp_now), mon.currentHp, 0..999, { v -> edit { it.withHp(v.coerceAtMost(it.maxHp), it.maxHp) } }, Modifier.width(100.dp))
                NumberField(stringResource(Res.string.party_hp_max), mon.maxHp, 1..999, { v -> edit { it.withHp(minOf(it.currentHp, v), v) } }, Modifier.width(100.dp))
                listOf("Attack", "Defense", "Speed", "Sp. Atk", "Sp. Def").forEachIndexed { i, name ->
                    NumberField(name, mon.stats[i], 1..999, { v -> edit { m -> m.withStats(m.stats.toMutableList().also { it[i] = v }) } }, Modifier.width(100.dp), tag = "Stat $i")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { rules?.baseStats(mon.species)?.let { base -> edit { it.recalculated(base) } } },
                    enabled = rules != null,
                    modifier = Modifier.testTag("party-recalculate"),
                ) { Text(stringResource(Res.string.party_recalculate_from_the_rom_s_base_st)) }
                OutlinedButton(onClick = { model.updateParty { it.without(slot) } }, modifier = Modifier.testTag("party-remove")) { Text(stringResource(Res.string.party_remove_from_the_party)) }
                OutlinedButton(onClick = { model.depositToPc(slot) }, enabled = model.pc?.let { pc -> (0 until PcStorageData.BOXES).any { pc.firstEmpty(it) != null } } == true, modifier = Modifier.testTag("party-deposit")) { Text(stringResource(Res.string.party_move_to_the_pc)) }
            }
        }
    }
}
