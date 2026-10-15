package io.github.romulofer.foxhg.core.project

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.arm9.Arm9
import io.github.romulofer.foxhg.core.compression.Blz
import io.github.romulofer.foxhg.core.game.GameNames
import io.github.romulofer.foxhg.core.game.GameText
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.narc.Narc
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
    initialOverlays: Map<Int, ByteArray> = emptyMap(),
) {
    private val edits = LinkedHashMap<String, ByteArray>(initialEdits)
    private var arm9Edit: ByteArray? = initialArm9
    private val overlayEdits = LinkedHashMap<Int, ByteArray>(initialOverlays)
    private val originalOverlays = HashMap<Int, FoxResult<ByteArray>>()
    private val originalArm9: FoxResult<ByteArray> by lazy { Arm9.decompressed(rom) }

    val editedPaths: Set<String> get() = edits.keys
    val isDirty: Boolean get() = edits.isNotEmpty() || arm9Edit != null || overlayEdits.isNotEmpty()

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

    /** Overlay ids (ARM9 overlay table) whose decompressed image the user changed. */
    val editedOverlays: Set<Int> get() = overlayEdits.keys

    fun editedOverlayImages(): Map<Int, ByteArray> = overlayEdits.mapValues { it.value.copyOf() }

    private fun overlayEntry(id: Int) = rom.arm9Overlays.firstOrNull { it.id == id }

    private fun originalOverlay(id: Int): FoxResult<ByteArray> = originalOverlays.getOrPut(id) {
        val entry = overlayEntry(id) ?: return@getOrPut FoxResult.Err("The ROM has no overlay $id")
        val stored = rom.readOverlay(entry) ?: return@getOrPut FoxResult.Err("Overlay $id has no file in the ROM")
        if (entry.isCompressed) Blz.decompress(stored) else FoxResult.Ok(stored)
    }

    /** The decompressed overlay [id] including edits. */
    fun readOverlay(id: Int): FoxResult<ByteArray> = overlayEdits[id]?.let { FoxResult.Ok(it.copyOf()) } ?: when (val o = originalOverlay(id)) {
        is FoxResult.Ok -> FoxResult.Ok(o.value.copyOf())
        is FoxResult.Err -> o
    }

    /** Stores [plain] as the new decompressed overlay [id]. Its size must not change; identical to the ROM's means no edit. */
    fun writeOverlay(id: Int, plain: ByteArray): FoxResult<Unit> {
        val original = when (val o = originalOverlay(id)) {
            is FoxResult.Ok -> o.value
            is FoxResult.Err -> return o
        }
        if (plain.size != original.size) return FoxResult.Err("Overlay $id must stay ${original.size} bytes, got ${plain.size}")
        if (plain.contentEquals(original)) overlayEdits.remove(id) else overlayEdits[id] = plain.copyOf()
        return FoxResult.Ok(Unit)
    }

    fun revertArm9() {
        arm9Edit = null
    }

    private var namesCache: GameNames? = null
    private var namesLoaded = false

    /**
     * Message archives and the name lists derived from them, with the user's edits applied. Rebuilt on the next read
     * after the message archives change, so an editor opened later shows renamed species, moves and so on.
     */
    val names: GameNames?
        get() {
            if (!namesLoaded) {
                namesCache = read(HgssLayout.MESSAGES)
                    ?.let { (Narc.parse(it) as? FoxResult.Ok)?.value }
                    ?.let { GameNames(GameText(it)) }
                namesLoaded = true
            }
            return namesCache
        }

    private fun messagesChanged(path: String) {
        if (path.trim('/') == HgssLayout.MESSAGES) namesLoaded = false
    }

    fun read(path: String): ByteArray? = edits[path.trim('/')]?.copyOf() ?: rom.read(path)

    fun isEdited(path: String): Boolean = edits.containsKey(path.trim('/'))

    /** Stores [bytes] as the new content of [path]. A file that ends up identical to the ROM's is not kept as an edit. */
    fun write(path: String, bytes: ByteArray): FoxResult<Unit> {
        val key = path.trim('/')
        val file = rom.file(key) ?: return FoxResult.Err("The ROM has no file '$key'")
        if (bytes.contentEquals(rom.read(file))) edits.remove(key) else edits[key] = bytes.copyOf()
        messagesChanged(key)
        return FoxResult.Ok(Unit)
    }

    fun revert(path: String) {
        edits.remove(path.trim('/'))
        messagesChanged(path)
    }

    fun revertAll() {
        edits.clear()
        arm9Edit = null
        overlayEdits.clear()
        namesLoaded = false
    }

    fun editedFiles(): Map<String, ByteArray> = edits.mapValues { it.value.copyOf() }

    /** Writes a new `.nds` with every edit applied. The original ROM and project stay untouched. */
    fun build(sink: RomSink): FoxResult<Unit> {
        val byId = HashMap<Int, ByteArray>()
        for ((path, bytes) in edits) {
            val file = rom.file(path) ?: return FoxResult.Err("Edited file '$path' is no longer in the ROM")
            byId[file.id] = bytes
        }
        for ((id, plain) in overlayEdits) {
            val entry = overlayEntry(id) ?: return FoxResult.Err("Edited overlay $id is no longer in the ROM")
            byId[entry.fileId] = if (entry.isCompressed) {
                when (val packed = Blz.compress(plain)) {
                    is FoxResult.Ok -> packed.value
                    is FoxResult.Err -> return FoxResult.Err("Overlay $id: ${packed.message}")
                }
            } else plain
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
        fun open(
            source: RomSource,
            edits: Map<String, ByteArray> = emptyMap(),
            arm9: ByteArray? = null,
            overlays: Map<Int, ByteArray> = emptyMap(),
        ): FoxResult<RomProject> =
            when (val rom = NdsRom.open(source)) {
                is FoxResult.Ok -> FoxResult.Ok(RomProject(rom.value, source, edits, arm9, overlays))
                is FoxResult.Err -> rom
            }
    }
}
