package io.github.romulofer.foxhg.core.project

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.arm9.Arm9
import io.github.romulofer.foxhg.core.compression.Blz
import io.github.romulofer.foxhg.core.game.GameNames
import io.github.romulofer.foxhg.core.game.GameText
import io.github.romulofer.foxhg.core.nds.NdsBuilder
import io.github.romulofer.foxhg.core.nds.NdsRom
import io.github.romulofer.foxhg.core.nds.RomEdits
import io.github.romulofer.foxhg.core.nds.RomSink
import io.github.romulofer.foxhg.core.nds.RomSource

/**
 * An opened ROM plus the files the user has changed. The ROM is never modified: edits live in [edits] (keyed by
 * file path) and are applied by [build]. Editors read through [read], so they see their own unsaved changes.
 */
class RomProject(
    val rom: NdsRom,
    private val source: RomSource,
    initialEdits: Map<String, ByteArray> = emptyMap(),
    initialArm9: ByteArray? = null,
) {
    private val edits = LinkedHashMap<String, ByteArray>(initialEdits)
    private var arm9Edit: ByteArray? = initialArm9
    private val originalArm9: FoxResult<ByteArray> by lazy { Arm9.decompressed(rom) }

    val editedPaths: Set<String> get() = edits.keys
    val isDirty: Boolean get() = edits.isNotEmpty() || arm9Edit != null

    /** The ARM9 edit (decompressed image), or null when the ARM9 is as in the ROM. */
    val editedArm9: ByteArray? get() = arm9Edit?.copyOf()

    /** Whether builds store the ARM9 compressed. Default: as the ROM does, which keeps the ROM size. */
    var compressArm9: Boolean = (originalArm9 as? FoxResult.Ok)?.let { Blz.looksCompressed(rom.readArm9()) } ?: false

    /** The decompressed ARM9 including edits; code patches read and write this image. */
    fun readArm9(): FoxResult<ByteArray> = arm9Edit?.let { FoxResult.Ok(it.copyOf()) } ?: when (val o = originalArm9) {
        is FoxResult.Ok -> FoxResult.Ok(o.value.copyOf())
        is FoxResult.Err -> o
    }

    /** Stores [plain] as the new decompressed ARM9; identical to the ROM's own means no edit. */
    fun writeArm9(plain: ByteArray): FoxResult<Unit> {
        val original = (originalArm9 as? FoxResult.Err)?.let { return it } ?: (originalArm9 as FoxResult.Ok).value
        arm9Edit = if (plain.contentEquals(original)) null else plain.copyOf()
        return FoxResult.Ok(Unit)
    }

    fun revertArm9() {
        arm9Edit = null
    }

    /** Message archives and the name lists derived from them, with the user's edits applied. */
    val names: GameNames? by lazy {
        val narcBytes = read("a/0/2/7") ?: return@lazy null
        val narc = (io.github.romulofer.foxhg.core.narc.Narc.parse(narcBytes) as? FoxResult.Ok)?.value ?: return@lazy null
        GameNames(GameText(narc))
    }

    fun read(path: String): ByteArray? = edits[path.trim('/')]?.copyOf() ?: rom.read(path)

    fun isEdited(path: String): Boolean = edits.containsKey(path.trim('/'))

    /** Stores [bytes] as the new content of [path]. A file that ends up identical to the ROM's is not kept as an edit. */
    fun write(path: String, bytes: ByteArray): FoxResult<Unit> {
        val key = path.trim('/')
        val file = rom.file(key) ?: return FoxResult.Err("The ROM has no file '$key'")
        if (bytes.contentEquals(rom.read(file))) edits.remove(key) else edits[key] = bytes.copyOf()
        return FoxResult.Ok(Unit)
    }

    fun revert(path: String) {
        edits.remove(path.trim('/'))
    }

    fun revertAll() {
        edits.clear()
        arm9Edit = null
    }

    fun editedFiles(): Map<String, ByteArray> = edits.mapValues { it.value.copyOf() }

    /** Writes a new `.nds` with every edit applied. The original ROM and project stay untouched. */
    fun build(sink: RomSink): FoxResult<Unit> {
        val byId = HashMap<Int, ByteArray>()
        for ((path, bytes) in edits) {
            val file = rom.file(path) ?: return FoxResult.Err("Edited file '$path' is no longer in the ROM")
            byId[file.id] = bytes
        }
        val storedArm9 = arm9Edit?.let { plain ->
            val footer = Arm9.footer(rom) ?: return FoxResult.Err("This ROM's ARM9 has no module parameters footer")
            when (val packed = Arm9.forRom(plain, footer.paramsOffset, compressArm9)) {
                is FoxResult.Ok -> packed.value
                is FoxResult.Err -> return packed
            }
        }
        return NdsBuilder.build(rom, source, RomEdits(byId, storedArm9), sink)
    }

    companion object {
        fun open(source: RomSource, edits: Map<String, ByteArray> = emptyMap(), arm9: ByteArray? = null): FoxResult<RomProject> =
            when (val rom = NdsRom.open(source)) {
                is FoxResult.Ok -> FoxResult.Ok(RomProject(rom.value, source, edits, arm9))
                is FoxResult.Err -> rom
            }
    }
}
