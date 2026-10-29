package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.arm9.Arm9

/**
 * The unpacked folder of a ROM in the layout DSPRE opens (the one `ndstool -x` makes): `header.bin`, `arm9.bin`,
 * `arm7.bin`, `y9.bin` and `y7.bin` (the overlay tables), `banner.bin`, `overlay/overlay_NNNN.bin` for each ARM9 overlay as
 * stored in the ROM, and the file system under `data/`. [entries] lists what to write; the bytes are read when asked for,
 * so a 128 MB ROM is never held whole.
 */
object DspreFolder {
    /** DSPRE's folder for a ROM called `x.nds` is `x_DSPRE_contents`. */
    const val SUFFIX = "_DSPRE_contents"

    /** The size of a banner version 1, which is what `ndstool` writes. */
    const val BANNER_SIZE = 0x840

    private const val NITRO_FOOTER_SIZE = 12

    class Entry(val path: String, val bytes: () -> ByteArray)

    fun folderName(romName: String): String = romName.removeSuffix(".nds").removeSuffix(".NDS") + SUFFIX

    /** A path of a ROM file that stays inside the folder: no empty, `.` or `..` part, no backslash or colon. */
    fun isSafe(path: String): Boolean =
        path.isNotEmpty() && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && it.none { c -> c == '\\' || c == ':' || c.code < 0x20 } }

    fun entries(rom: NdsRom): FoxResult<List<Entry>> {
        val header = rom.header
        val list = ArrayList<Entry>()
        list += Entry("header.bin") { rom.readRange(0, NdsHeader.SIZE) }
        list += Entry("arm9.bin") {
            // The three words after the ARM9 (the module parameters' marker) belong to the file ndstool extracts.
            val body = rom.readArm9()
            val footer = if (Arm9.footer(rom) != null) rom.readAfterArm9(NITRO_FOOTER_SIZE) else null
            if (footer == null) body else body + footer
        }
        list += Entry("arm7.bin") { rom.readArm7() }
        list += Entry("y9.bin") { rom.readRange(header.arm9Overlays.offset, header.arm9Overlays.size.toInt()) }
        list += Entry("y7.bin") { rom.readRange(header.arm7Overlays.offset, header.arm7Overlays.size.toInt()) }
        if (header.bannerOffset != 0L) list += Entry("banner.bin") { rom.readRange(header.bannerOffset, BANNER_SIZE) }
        for (entry in rom.arm9Overlays) {
            if (rom.file(entry.fileId) == null) return FoxResult.Err("Overlay ${entry.id} has no file in the ROM")
            list += Entry("overlay/overlay_${entry.id.toString().padStart(4, '0')}.bin") { rom.readOverlay(entry)!! }
        }
        for (file in rom.files.sortedBy { it.path }) {
            if (!isSafe(file.path)) return FoxResult.Err("The ROM has a file called '${file.path}', which cannot be written inside a folder")
            list += Entry("data/${file.path}") { rom.read(file) }
        }
        return FoxResult.Ok(list)
    }
}
