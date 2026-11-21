package io.github.legendaryredfox.foxhg.app

import androidx.compose.runtime.Composable
import io.github.legendaryredfox.foxhg.app.resources.*
import org.jetbrains.compose.resources.stringResource

/** The player's gender as the new game asks for it, in the order of `PlayerData.GENDERS`. */
@Composable
fun playerGenderNames(): List<String> = listOf(stringResource(Res.string.gender_boy), stringResource(Res.string.gender_girl))

/** The gender of a trade's original trainer, in the order of `NpcTrade.GENDERS`. */
@Composable
fun trainerGenderNames(): List<String> = listOf(stringResource(Res.string.gender_male), stringResource(Res.string.gender_female))

/** The four directions a player can face, in the order of `Location.DIRECTIONS`. */
@Composable
fun directionNames(): List<String> = listOf(
    stringResource(Res.string.direction_north), stringResource(Res.string.direction_south),
    stringResource(Res.string.direction_west), stringResource(Res.string.direction_east),
)
