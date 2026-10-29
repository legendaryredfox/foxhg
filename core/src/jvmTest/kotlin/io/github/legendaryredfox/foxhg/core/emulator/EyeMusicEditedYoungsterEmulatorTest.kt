package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainerclass.TrainerEncounterMusic
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.classId
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.ok
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.trainerOfClass
import kotlin.test.Test

/** Records the eye-contact song of a Youngster whose row of the vanilla table was edited in place to the Lass's song, 1113 (see [EyeMusicRun]). */
class EyeMusicEditedYoungsterEmulatorTest {
    @Test
    fun record() = EyeMusicRun.run("youngster-edited") { project ->
        val youngster = classId(project, "Youngster")
        val music = (TrainerEncounterMusic.load(project) as FoxResult.Ok).value
        val row = checkNotNull(music.of(youngster)) { "the Youngster has no music row" }
        ok(TrainerEncounterMusic.save(project, music.with(row.copy(first = 1113, second = 1113))))
        trainerOfClass(project, youngster)
    }
}
