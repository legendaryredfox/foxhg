package io.github.legendaryredfox.foxhg.core.emulator

import dev.kotlinds.libretrokmp.Device
import dev.kotlinds.libretrokmp.JoypadButton
import dev.kotlinds.libretrokmp.LibretroCore
import dev.kotlinds.libretrokmp.LibretroFrontend
import dev.kotlinds.libretrokmp.MemoryType
import dev.kotlinds.libretrokmp.PointerId
import dev.kotlinds.libretrokmp.VideoFrame
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** A button of the DS pad. */
enum class Pad(val id: Int) {
    A(JoypadButton.A), B(JoypadButton.B), X(JoypadButton.X), Y(JoypadButton.Y), START(JoypadButton.START), SELECT(JoypadButton.SELECT),
    UP(JoypadButton.UP), DOWN(JoypadButton.DOWN), LEFT(JoypadButton.LEFT), RIGHT(JoypadButton.RIGHT), L(JoypadButton.L), R(JoypadButton.R),
}

/**
 * Runs a DS ROM on the DeSmuME libretro core (through libretro-kmp, Apache-2.0) for tests: steps frames, holds buttons or touches the
 * bottom screen, saves a picture of both screens and reads the 4 MB main RAM by its DS address (0x02000000 and up). Only for tests: it never
 * writes the ROM and keeps the in-game save in [saveDir]. One driver at a time in a process (the core keeps global state).
 */
class EmulatorDriver(rom: Path, private val saveDir: Path, corePath: Path = defaultCore()) : AutoCloseable {
    private val held = HashSet<Int>()
    private var touch: Pair<Int, Int>? = null
    private var lastFrame: VideoFrame? = null

    private val frontend = object : LibretroFrontend {
        override val systemDirectory: String = Files.createDirectories(saveDir.resolve("system")).toString()
        override val saveDirectory: String = Files.createDirectories(saveDir).toString()
        override fun variable(key: String): String? = OPTIONS[key].also { if (System.getenv("FOXHG_LOG_OPTIONS") != null) println("CORE OPTION $key -> $it") }
        override fun onVideoFrame(frame: VideoFrame) { lastFrame = frame }
        override fun inputState(port: Int, device: Int, index: Int, id: Int): Short = also { if (System.getenv("FOXHG_LOG_INPUT") != null && device != Device.JOYPAD) println("INPUT port=$port device=$device index=$index id=$id touch=$touch") }.let { _ -> when {
            port != 0 -> 0
            device == Device.JOYPAD -> if (id in held) 1 else 0
            device == Device.POINTER -> touch.let { t ->
                when (id) {
                    PointerId.PRESSED -> if (t != null) 1 else 0
                    PointerId.X -> if (t != null) ((t.first * 2 * 0x7FFF) / SCREEN_WIDTH - 0x7FFF).toShort() else 0
                    PointerId.Y -> if (t != null) (((t.second + SCREEN_HEIGHT) * 2 * 0x7FFF) / (2 * SCREEN_HEIGHT) - 0x7FFF).toShort() else 0
                    else -> 0
                }
            }
            else -> 0
        } }
    }

    private val core = LibretroCore(corePath.toString(), frontend)

    init {
        core.loadGame(rom.toString())
        core.setControllerPortDevice(0, Device.JOYPAD)
    }

    /** Frames run so far. */
    var frames: Int = 0
        private set

    /** Runs [count] frames with the buttons now held. */
    fun step(count: Int) {
        repeat(count) { core.run(); frames++ }
    }

    /** Holds [button] for [hold] frames, then lets go and runs [after] more frames. */
    fun press(button: Pad, hold: Int = 4, after: Int = 12) {
        held += button.id
        step(hold)
        held -= button.id
        step(after)
    }

    /** Touches the bottom screen at ([x], [y]) of its 256 x 192 pixels for [hold] frames. */
    fun tap(x: Int, y: Int, hold: Int = 6, after: Int = 12) {
        touch = x to y
        step(hold)
        touch = null
        step(after)
    }

    /** The last frame as an image (both screens, top above bottom), or null before the first frame. */
    fun screenshot(): BufferedImage? = lastFrame?.let { f ->
        BufferedImage(f.width, f.height, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(0, 0, f.width, f.height, f.pixels, 0, f.width) }
    }

    fun savePicture(target: Path) {
        val image = screenshot() ?: error("no frame yet")
        Files.createDirectories(target.toAbsolutePath().parent)
        ImageIO.write(image, "png", target.toFile())
    }

    private fun ram(): ByteArray = core.readMemory(MemoryType.SYSTEM_RAM) ?: error("the core exposes no system RAM")

    /** [length] bytes of the main RAM at DS address [address] (0x02000000 to 0x023FFFFF). */
    fun read(address: Long, length: Int): ByteArray {
        val at = (address - RAM_BASE).toInt()
        val all = ram()
        require(at >= 0 && at + length <= all.size) { "0x${address.toString(16)} is outside the main RAM of ${all.size} bytes" }
        return all.copyOfRange(at, at + length)
    }

    fun readU16(address: Long): Int = read(address, 2).u16(0)
    fun readU32(address: Long): Long = read(address, 4).u32(0)

    /** Addresses in the main RAM where the u16 values [sequence] appear one after another (for example an item list a shop copied). */
    fun findU16s(sequence: List<Int>): List<Long> {
        val all = ram()
        val needle = ByteArray(sequence.size * 2).also { b -> sequence.forEachIndexed { i, v -> b[2 * i] = v.toByte(); b[2 * i + 1] = (v shr 8).toByte() } }
        val found = ArrayList<Long>()
        var at = 0
        while (at <= all.size - needle.size) {
            if (needle.indices.all { all[at + it] == needle[it] }) { found += RAM_BASE + at; at += needle.size } else at += 2
        }
        return found
    }

    /**
     * Plays a script: a comma separated list of steps. `s600` runs 600 frames; `A` `B` `X` `Y` `START` `SELECT` `UP` `DOWN` `LEFT` `RIGHT` `L` `R`
     * press that button (`A*20` presses it 20 times, `LEFT:16*3` holds it 16 frames each time: a walking step); `tap:x:y` or `tap:x:y:frames`
     * touches the bottom screen (the DeSmuME core of this system ignores it: use the buttons); `shot:file.png` writes a picture to [shotDir];
     * `state:file` writes a save state and `load:file` loads one, in [shotDir].
     */
    fun run(script: String, shotDir: Path? = null) {
        for (token in script.split(',').map { it.trim() }.filter { it.isNotEmpty() }) {
            val name = token.substringBefore('*').substringBefore(':')
            val times = token.substringAfter('*', "1").toIntOrNull() ?: 1
            fun file(prefix: String) = (shotDir ?: error("this step needs a directory")).resolve(token.removePrefix(prefix))
            when {
                token.startsWith("s") && token.drop(1).all { it.isDigit() } -> step(token.drop(1).toInt())
                token.startsWith("shot:") -> savePicture(file("shot:"))
                token.startsWith("state:") -> saveState()?.let { Files.write(file("state:"), it) }
                token.startsWith("load:") -> loadState(Files.readAllBytes(file("load:")))
                token.startsWith("tap:") -> token.split(':').let { tap(it[1].toInt(), it[2].toInt(), hold = it.getOrNull(3)?.toInt() ?: 6) }
                else -> {
                    val pad = Pad.entries.firstOrNull { it.name == name } ?: error("unknown step '$token'")
                    val hold = token.substringBefore('*').substringAfter(':', "4").toInt()
                    repeat(times) { press(pad, hold = hold, after = if (hold > 8) 2 else 12) }
                }
            }
        }
    }

    /** The in-game save as the core keeps it (DeSmuME's own layout, not always the raw 512 KB). */
    fun saveMemory(): ByteArray? = core.readMemory(MemoryType.SAVE_RAM)

    fun saveState(): ByteArray? = core.saveState()
    fun loadState(state: ByteArray) = core.loadState(state)

    override fun close() = core.close()

    companion object {
        const val RAM_BASE = 0x02000000L
        const val SCREEN_WIDTH = 256
        const val SCREEN_HEIGHT = 192

        /** The core options the tests use: interpreter (reproducible), screens stacked with no gap, the pointer is the touch screen. */
        val OPTIONS = mapOf(
            "desmume_cpu_mode" to "interpreter",
            "desmume_screens_layout" to "top/bottom",
            "desmume_screens_gap" to "0",
            "desmume_pointer_mouse" to "enabled",
            "desmume_pointer_type" to "touch",
            "desmume_firmware_language" to "English",
            "desmume_frameskip" to "0",
            "desmume_internal_resolution" to "256x192",
        )

        /** The DeSmuME core: `DESMUME_CORE`, or the Debian package's place. Null when there is none (the emulator tests skip). */
        fun findCore(): Path? = (System.getenv("DESMUME_CORE") ?: "/usr/lib/x86_64-linux-gnu/libretro/desmume_libretro.so").let(Path::of).takeIf { Files.exists(it) }

        fun defaultCore(): Path = findCore() ?: error("no DeSmuME libretro core: set DESMUME_CORE")
    }
}
