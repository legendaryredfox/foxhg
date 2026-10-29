package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.classId
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.newClassTrainer
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.trainerOfClass
import kotlin.test.Test

/** Records the eye-contact song of a trainer of a new class with no music row, which must play the Youngster's song (see [EyeMusicRun]). */
class EyeMusicNewClassWithoutMusicEmulatorTest {
    @Test
    fun record() = EyeMusicRun.run("new-plain") { newClassTrainer(it, 0) }
}
