package io.github.legendaryredfox.foxhg.app

import androidx.compose.runtime.Composable
import io.github.legendaryredfox.foxhg.app.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The shown name of an editor. [EditorKind.title] stays the English name, which is the id tests and the sidebar tags use. */
@Composable
fun EditorKind.displayTitle(): String = stringResource(titleResource())

/** The shown name of a sidebar group (the English group name is the id, see [EditorKind.group]). */
@Composable
fun displayGroup(group: String): String = stringResource(
    when (group) {
        "Pokémon" -> Res.string.group_pokemon
        "Moves and items" -> Res.string.group_moves_items
        "Trainers" -> Res.string.group_trainers
        "World" -> Res.string.group_world
        "Scripts and text" -> Res.string.group_scripts_text
        "Audio" -> Res.string.group_audio
        "Tools" -> Res.string.group_tools
        SAVE_GROUP -> Res.string.group_save
        else -> error("No translation for the group $group")
    },
)

private fun EditorKind.titleResource(): StringResource = when (this) {
    EditorKind.PERSONAL -> Res.string.editor_personal
    EditorKind.LEARNSET -> Res.string.editor_learnset
    EditorKind.EVOLUTIONS -> Res.string.editor_evolutions
    EditorKind.EGG_MOVES -> Res.string.editor_egg_moves
    EditorKind.TYPE_CHART -> Res.string.editor_type_chart
    EditorKind.GROWTH -> Res.string.editor_growth
    EditorKind.PICKUP -> Res.string.editor_pickup
    EditorKind.BATTLE_DISPLAY -> Res.string.editor_battle_display
    EditorKind.STARTERS -> Res.string.editor_starters
    EditorKind.STATICS -> Res.string.editor_statics
    EditorKind.ROAMERS -> Res.string.editor_roamers
    EditorKind.FOSSILS -> Res.string.editor_fossils
    EditorKind.SHINY_RATES -> Res.string.editor_shiny_rates
    EditorKind.TRADES -> Res.string.editor_trades
    EditorKind.HP_BAR -> Res.string.editor_hp_bar
    EditorKind.TMHM -> Res.string.editor_tmhm
    EditorKind.MARTS -> Res.string.editor_marts
    EditorKind.TMHM_BULK -> Res.string.editor_tmhm_bulk
    EditorKind.MOVES -> Res.string.editor_moves
    EditorKind.ITEMS -> Res.string.editor_items
    EditorKind.GROUND_ITEMS -> Res.string.editor_ground_items
    EditorKind.HIDDEN_ITEMS -> Res.string.editor_hidden_items
    EditorKind.BATTLE_RULES -> Res.string.editor_battle_rules
    EditorKind.TRAINERS -> Res.string.editor_trainers
    EditorKind.BATTLE_TOWER_TRAINERS -> Res.string.editor_battle_tower_trainers
    EditorKind.BATTLE_TOWER_SETS -> Res.string.editor_battle_tower_sets
    EditorKind.TRAINER_FLAGS -> Res.string.editor_trainer_flags
    EditorKind.TRAINER_CLASSES -> Res.string.editor_trainer_classes
    EditorKind.ENCOUNTERS -> Res.string.editor_encounters
    EditorKind.ROCK_SMASH -> Res.string.editor_rock_smash
    EditorKind.FLY -> Res.string.editor_fly
    EditorKind.HEADBUTT -> Res.string.editor_headbutt
    EditorKind.BUG_CONTEST -> Res.string.editor_bug_contest
    EditorKind.SAFARI -> Res.string.editor_safari
    EditorKind.TOWN_MAP -> Res.string.editor_town_map
    EditorKind.PHOTOS -> Res.string.editor_photos
    EditorKind.HEADERS -> Res.string.editor_headers
    EditorKind.MATRICES -> Res.string.editor_matrices
    EditorKind.MAPS -> Res.string.editor_maps
    EditorKind.EVENTS -> Res.string.editor_events
    EditorKind.SCRIPTS -> Res.string.editor_scripts
    EditorKind.RESEARCH -> Res.string.editor_research
    EditorKind.TEXT -> Res.string.editor_text
    EditorKind.SOUNDS -> Res.string.editor_sounds
    EditorKind.TOOLBOX -> Res.string.editor_toolbox
    EditorKind.FILES -> Res.string.editor_files
    EditorKind.OVERLAYS -> Res.string.editor_overlays
    EditorKind.SAVE_PLAYER -> Res.string.editor_save_player
    EditorKind.SAVE_PARTY -> Res.string.editor_save_party
    EditorKind.SAVE_PC -> Res.string.editor_save_pc
    EditorKind.SAVE_BAG -> Res.string.editor_save_bag
    EditorKind.SAVE_POKEDEX -> Res.string.editor_save_pokedex
    EditorKind.SAVE_FLAGS -> Res.string.editor_save_flags
    EditorKind.SAVE_POSITION -> Res.string.editor_save_position
    EditorKind.SAVE_FILE -> Res.string.editor_save_file
}
