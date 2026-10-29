package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.classId
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.newClassTrainer
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.trainerOfClass
import kotlin.test.Test

/** Records the eye-contact song of a Youngster, whose eye-contact song is sequence 1108 (see [EyeMusicRun]). */
class EyeMusicYoungsterEmulatorTest {
    @Test
    fun record() = EyeMusicRun.run("youngster") { trainerOfClass(it, classId(it, "Youngster")) }
}
