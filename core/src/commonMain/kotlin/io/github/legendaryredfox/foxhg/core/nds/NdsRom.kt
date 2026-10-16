package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.ascii
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/** One file of the ROM file system. [start] and [end] are absolute offsets in the `.nds` image. */
data class RomFile(val id: Int, val name: String, val path: String, val start: Long, val end: Long) {
    val size: Long get() = end - start
}

class RomDirectory(val id: Int, val name: String, val path: String) {
    val directories: MutableList<RomDirectory> = mutableListOf()
    val files: MutableList<RomFile> = mutableListOf()
}

/** One row of an overlay table (`y9.bin` for ARM9, `y7.bin` for ARM7). */
data class OverlayEntry(
    val id: Int,
    val ramAddress: Long,
    val ramSize: Long,
    val bssSize: Long,
    val staticInitStart: Long,
    val staticInitEnd: Long,
    val fileId: Int,
    /** Size of the compressed data (low 24 bits of the last word); meaningful when [isCompressed]. */
    val compressedSize: Long,
    val flags: Int,
) {
    val isCompressed: Boolean get() = flags and FLAG_COMPRESSED != 0

    companion object {
        const val ENTRY_SIZE = 32
        const val FLAG_COMPRESSED = 0x01
    }
}

/**
 * Read-only view of a `.nds` file system (FNT + FAT, GBATEK "DS Cartridge Nitro File System").
 * Reading never modifies the ROM. Writing back is `NdsBuilder` (plan.md M1).
 */
class NdsRom private constructor(
    val header: NdsHeader,
    private val source: RomSource,
    val root: RomDirectory,
    private val filesById: Map<Int, RomFile>,
    private val filesByPath: Map<String, RomFile>,
    private val fatBytes: ByteArray,
    val arm9Overlays: List<OverlayEntry>,
    val arm7Overlays: List<OverlayEntry>,
) {
    /** Files that have a name in the file system tree. Overlays and other unnamed FAT entries are not listed here. */
    val files: Collection<RomFile> get() = filesById.values

    /** Number of FAT entries, named or not. */
    val fileCount: Int get() = fatBytes.size / 8

    fun file(path: String): RomFile? = filesByPath[path.trim('/')]

    /** Looks a file up by FAT id. Unnamed entries (overlays) come back with an empty name and path. */
    fun file(id: Int): RomFile? {
        filesById[id]?.let { return it }
        if (id < 0 || id >= fileCount) return null
        val start = fatBytes.u32(id * 8)
        val end = fatBytes.u32(id * 8 + 4)
        return if (start <= end && end <= source.size) RomFile(id, "", "", start, end) else null
    }

    fun read(file: RomFile): ByteArray = source.read(file.start, file.size.toInt())

    fun read(path: String): ByteArray? = file(path)?.let(::read)

    /** Raw ARM9 binary as stored in the ROM (it may carry a compressed footer, see plan.md M1). */
    fun readArm9(): ByteArray = source.read(header.arm9.offset, header.arm9.size.toInt())

    /** [length] bytes right after the ARM9 binary (its footer), or null when they lie outside the image. */
    fun readAfterArm9(length: Int): ByteArray? {
        val at = header.arm9.offset + header.arm9.size
        return if (at + length <= source.size) source.read(at, length) else null
    }

    fun readArm7(): ByteArray = source.read(header.arm7.offset, header.arm7.size.toInt())

    fun readOverlay(entry: OverlayEntry): ByteArray? = file(entry.fileId)?.let(::read)

    companion object {
        private const val ROOT_DIRECTORY_ID = 0xF000
        private const val MAX_FILES = 0x10000

        fun open(source: RomSource): FoxResult<NdsRom> {
            val header = when (val h = NdsHeader.parse(source.read(0, minOf(NdsHeader.SIZE.toLong(), source.size).toInt()))) {
                is FoxResult.Ok -> h.value
                is FoxResult.Err -> return h
            }
            val fnt = header.fileNameTable
            val fat = header.fileAllocationTable
            if (fnt.offset + fnt.size > source.size || fat.offset + fat.size > source.size) {
                return FoxResult.Err("File system tables lie outside the ROM image")
            }
            if (fat.size % 8L != 0L || fat.size / 8 > MAX_FILES) {
                return FoxResult.Err("Bad file allocation table size ${fat.size}")
            }
            val fntBytes = source.read(fnt.offset, fnt.size.toInt())
            val fatBytes = source.read(fat.offset, fat.size.toInt())
            val fileCount = (fat.size / 8).toInt()

            for ((name, region) in listOf("ARM9" to header.arm9, "ARM7" to header.arm7)) {
                if (region.size > Int.MAX_VALUE || region.offset + region.size > source.size) return FoxResult.Err("The $name binary lies outside the ROM image")
            }
            val tree = try {
                buildTree(fntBytes, fatBytes, fileCount, source.size)
            } catch (e: IllegalArgumentException) {
                return FoxResult.Err("Corrupt file name table: ${e.message}")
            } catch (e: IndexOutOfBoundsException) {
                return FoxResult.Err("Corrupt file name table: an entry lies outside the table")
            }
            val (root, files) = tree

            return FoxResult.Ok(
                NdsRom(
                    header = header,
                    source = source,
                    root = root,
                    filesById = files.associateBy { it.id },
                    filesByPath = files.associateBy { it.path },
                    fatBytes = fatBytes,
                    arm9Overlays = readOverlayTable(source, header.arm9Overlays),
                    arm7Overlays = readOverlayTable(source, header.arm7Overlays),
                ),
            )
        }

        private fun buildTree(fnt: ByteArray, fat: ByteArray, fileCount: Int, romSize: Long): Pair<RomDirectory, List<RomFile>> {
            require(fnt.size >= 8) { "table shorter than the root entry" }
            val directoryCount = fnt.u16(6)
            require(directoryCount in 1..0x1000 && fnt.size >= directoryCount * 8) { "bad directory count $directoryCount" }

            val files = ArrayList<RomFile>()
            val visited = HashSet<Int>()

            fun walk(dirIndex: Int, name: String, path: String): RomDirectory {
                require(visited.add(dirIndex)) { "directory $dirIndex is referenced twice" }
                require(dirIndex in 0 until directoryCount) { "directory id out of range: $dirIndex" }
                val dir = RomDirectory(ROOT_DIRECTORY_ID + dirIndex, name, path)
                val tableOffset = fnt.u32(dirIndex * 8)
                require(tableOffset < fnt.size) { "directory $dirIndex starts outside the table" }
                var p = tableOffset.toInt()
                var nextFileId = fnt.u16(dirIndex * 8 + 4)
                while (true) {
                    require(p < fnt.size) { "directory $dirIndex runs past the table" }
                    val tag = fnt[p].toInt() and 0xFF
                    p++
                    if (tag == 0) break
                    val length = tag and 0x7F
                    require(p + length <= fnt.size) { "name runs past the table" }
                    val childName = fnt.ascii(p, length)
                    p += length
                    val childPath = if (path.isEmpty()) childName else "$path/$childName"
                    if (tag and 0x80 != 0) {
                        require(p + 2 <= fnt.size) { "directory id runs past the table" }
                        val childId = fnt.u16(p)
                        p += 2
                        dir.directories += walk(childId - ROOT_DIRECTORY_ID, childName, childPath)
                    } else {
                        require(nextFileId < fileCount) { "file id $nextFileId has no FAT entry" }
                        val start = fat.u32(nextFileId * 8)
                        val end = fat.u32(nextFileId * 8 + 4)
                        require(start <= end && end <= romSize) { "file $nextFileId range $start..$end is outside the ROM" }
                        val file = RomFile(nextFileId, childName, childPath, start, end)
                        dir.files += file
                        files += file
                        nextFileId++
                    }
                }
                return dir
            }

            val root = walk(0, "", "")
            return root to files
        }

        private fun readOverlayTable(source: RomSource, region: RomRegion): List<OverlayEntry> {
            if (region.size == 0L || region.offset + region.size > source.size) return emptyList()
            val table = source.read(region.offset, region.size.toInt())
            return List((region.size / OverlayEntry.ENTRY_SIZE).toInt()) { i ->
                val p = i * OverlayEntry.ENTRY_SIZE
                val sizeAndFlags = table.u32(p + 28)
                OverlayEntry(
                    id = table.u32(p).toInt(),
                    ramAddress = table.u32(p + 4),
                    ramSize = table.u32(p + 8),
                    bssSize = table.u32(p + 12),
                    staticInitStart = table.u32(p + 16),
                    staticInitEnd = table.u32(p + 20),
                    fileId = table.u32(p + 24).toInt(),
                    compressedSize = sizeAndFlags and 0xFFFFFF,
                    flags = (sizeAndFlags ushr 24).toInt(),
                )
            }
        }
    }
}
