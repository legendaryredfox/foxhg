package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.classId
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.newClassTrainer
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.trainerOfClass
import kotlin.test.Test

/** Records the eye-contact song of a trainer of a new class given the music 1113, which must play the Lass's song (see [EyeMusicRun]). */
class EyeMusicNewClassWithMusicEmulatorTest {
    @Test
    fun record() = EyeMusicRun.run("new-music") { newClassTrainer(it, 1113) }
}
