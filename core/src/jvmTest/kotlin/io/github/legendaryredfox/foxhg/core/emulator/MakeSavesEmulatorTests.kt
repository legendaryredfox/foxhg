package io.github.legendaryredfox.foxhg.core.emulator

import kotlin.test.Test

/** Makes the saves of [EmulatorSaves] (the `emulatorSetup` task runs these first, one boot each). */
class MakeViridianSaveEmulatorTest {
    @Test
    fun makeTheSaveInViridiansPokeMart() = EmulatorSaves.make("viridian", MartStartRom.VIRIDIAN)
}

class MakePartySaveEmulatorTest {
    @Test
    fun makeTheSaveWithAPartyInCherrygrovesPokeMart() = EmulatorSaves.make("party", MartStartRom.CHERRYGROVE_WITH_PARTY)
}

class MakeCherrygroveSaveEmulatorTest {
    @Test
    fun makeTheSaveInCherrygrovesPokeMart() = EmulatorSaves.make("cherrygrove", MartStartRom.CHERRYGROVE)
}
