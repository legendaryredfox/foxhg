package io.github.romulofer.foxhg.app

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsAndRunTest {
    @Test
    fun settingsAreWrittenAndReadBackAndAMissingFileGivesTheDefaults() {
        val file = tempDir("foxhg-settings").resolve("nested/settings.properties")
        val store = SettingsStore(file, detectEmulator = { "found {rom}" })
        assertEquals(AppSettings(emulatorCommand = "found {rom}"), store.load(), "no file: defaults with the emulator that was found")
        val chosen = AppSettings(ThemeChoice.DARK, "retroarch -L \"/a b/core.so\" {rom}", autosave = false)
        assertTrue(store.save(chosen))
        assertEquals(chosen, SettingsStore(file, detectEmulator = { "other" }).load())
        Files.writeString(file, "theme=sepia\nautosave=maybe\n")
        assertEquals(AppSettings(), store.load(), "unknown values fall back to the defaults")
    }

    @Test
    fun theCommandIsSplitLikeAShellWouldAndTheRomIsPlacedOrAppended() {
        val rom = Path.of("/tmp/my hack/rom.nds")
        assertEquals(listOf("desmume", "/tmp/my hack/rom.nds"), Emulator.command("desmume {rom}", rom))
        assertEquals(listOf("desmume", "--fast", "/tmp/my hack/rom.nds"), Emulator.command("  desmume   --fast ", rom), "no {rom}: it goes last")
        assertEquals(listOf("retroarch", "-L", "/opt/my cores/d.so", "/tmp/my hack/rom.nds"), Emulator.command("retroarch -L '/opt/my cores/d.so' {rom}", rom))
        assertEquals(listOf("emu", "--rom=/tmp/my hack/rom.nds", ""), Emulator.command("emu \"--rom={rom}\" ''", rom))
    }

    @Test
    fun anEmulatorOnThePathIsFound() {
        val bin = tempDir("foxhg-bin")
        assertEquals("", Emulator.detect(listOf(bin.toFile())))
        val exe = bin.resolve("desmume").toFile().apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        assertEquals("desmume {rom}", Emulator.detect(listOf(File(exe.parent))))
    }
}
