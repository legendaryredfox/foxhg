package io.github.legendaryredfox.foxhg.app

import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.app.ui.PickerField
import io.github.legendaryredfox.foxhg.app.ui.SectionCard
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.hpbar.HpBarSpeed
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The speed of the battle HP bar: one instruction of overlay 12, written on each change. */
class HpBarSpeedEditorModel(private val project: RomProject, private val onChanged: () -> Unit = {}) {
    var speed: HpBarSpeed? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    init {
        when (val loaded = HpBarSpeed.load(project)) {
            is FoxResult.Ok -> speed = loaded.value
            is FoxResult.Err -> error = loaded.message
        }
    }

    fun set(new: HpBarSpeed) {
        when (val r = HpBarSpeed.save(project, new)) {
            is FoxResult.Ok -> { speed = new; error = null; onChanged() }
            is FoxResult.Err -> error = r.message
        }
    }

    companion object {
        /** The speeds offered, slowest first: a shift of 5 to 12 is a speed of 1/8 to 16 times the game's. */
        val SHIFTS = (5..12).toList()
        fun label(shift: Int): String {
            val m = HpBarSpeed(shift).multiplier
            val text = if (m >= 1) "x${m.toInt()}" else "x${m}"
            return if (shift == HpBarSpeed.NORMAL_SHIFT) tr(Res.string.hp_bar_speed_x_the_game_s, text) else text
        }
    }
}

@Composable
fun HpBarSpeedEditor(model: HpBarSpeedEditorModel, modifier: Modifier = Modifier) {
    val speed = model.speed
    if (speed == null) {
        Text(model.error ?: "", color = MaterialTheme.colorScheme.error, modifier = modifier.padding(24.dp))
        return
    }
    DetailPane(modifier) {
        Text(stringResource(Res.string.editor_hp_bar), style = MaterialTheme.typography.headlineSmall)
        model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("hpbar-message")) }
        SectionCard(stringResource(Res.string.hp_bar_speed_battle_hp_bar), subtitle = stringResource(Res.string.hp_bar_speed_how_fast_the_hp_bar_drains_and_fil)) {
            val labels = HpBarSpeedEditorModel.SHIFTS.map(HpBarSpeedEditorModel::label)
            PickerField(stringResource(Res.string.personal_speed), HpBarSpeedEditorModel.SHIFTS.indexOf(speed.shift).coerceAtLeast(0), labels, { v -> model.set(HpBarSpeed(HpBarSpeedEditorModel.SHIFTS[v])) }, Modifier.width(240.dp).testTag("hpbar-speed"))
            if (speed.shift !in HpBarSpeedEditorModel.SHIFTS) Text(stringResource(Res.string.hp_bar_speed_the_rom_has_a_custom_speed_shift_x, speed.shift), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { model.set(HpBarSpeed.VANILLA) }, modifier = Modifier.testTag("hpbar-restore")) { Text(stringResource(Res.string.hp_bar_speed_restore_the_game_s_speed)) }
        }
    }
}
