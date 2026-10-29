package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.classId
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.newClassTrainer
import io.github.legendaryredfox.foxhg.core.emulator.EyeMusicRun.trainerOfClass
import kotlin.test.Test

/** Records the eye-contact song of a Lass, whose eye-contact song is sequence 1113 (see [EyeMusicRun]). */
class EyeMusicLassEmulatorTest {
    @Test
    fun record() = EyeMusicRun.run("lass") { trainerOfClass(it, classId(it, "Lass")) }
}
