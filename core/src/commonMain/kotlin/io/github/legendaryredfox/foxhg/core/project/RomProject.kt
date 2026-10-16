package io.github.legendaryredfox.foxhg.core.project

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.arm9.Arm9
import io.github.legendaryredfox.foxhg.core.compression.Blz
import io.github.legendaryredfox.foxhg.core.game.GameNames
import io.github.legendaryredfox.foxhg.core.game.GameText
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.NdsBuilder
import io.github.legendaryredfox.foxhg.core.nds.NdsRom
import io.github.legendaryredfox.foxhg.core.nds.RomEdits
import io.github.legendaryredfox.foxhg.core.nds.RomSink
import io.github.legendaryredfox.foxhg.core.nds.RomSource
import kotlin.time.TimeSource

/** A piece of the ROM an edit can change; what an undo step reports it touched. */
sealed interface EditTarget {
    val label: String

    data class File(val path: String) : EditTarget {
        override val label: String get() = path
    }

    data object Arm9 : EditTarget {
        override val label: String get() = "ARM9"
    }

    data class Overlay(val id: Int) : EditTarget {
        override val label: String get() = "overlay $id"
    }
}

/**
 * An opened ROM plus the files the user has changed. The ROM is never modified: edits live in [edits] (keyed by
 * file path) and are applied by [build]. Editors read through [read], so they see their own unsaved changes.
 *
 * Every change is journaled, so [undo] and [redo] step back and forth through them. Successive writes to the same
 * target within [COALESCE_MILLIS] (typing in a field) count as one step.
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

    private class Change(val target: EditTarget, val before: ByteArray?, val after: ByteArray?)
    private class Step(val changes: List<Change>, val at: Long)

    private val undoSteps = ArrayList<Step>()
    private val redoSteps = ArrayList<Step>()
    private val clockOrigin = TimeSource.Monotonic.markNow()

    /** Milliseconds for the journal's coalescing; tests replace it with a manual clock. */
    var clock: () -> Long = { clockOrigin.elapsedNow().inWholeMilliseconds }

    /** Counts every change of the journal, so a saver can tell whether the history on disk is behind. */
    var historyVersion: Int = 0
        private set
    private var historySavedVersion = 0

    /** True when the journal changed since it was last marked saved. */
    val historyDirty: Boolean get() = historyVersion != historySavedVersion

    fun markHistorySaved() { historySavedVersion = historyVersion }

    /** The journal as data for `history.bin`, at most [maxBytes] of it. */
    @kotlin.jvm.Synchronized
    fun historySnapshot(maxBytes: Long = MAX_HISTORY_BYTES): UndoSnapshot =
        UndoSnapshot(undoSteps.map { s -> s.changes.map { UndoChange(it.target, it.before, it.after) } }, redoSteps.map { s -> s.changes.map { UndoChange(it.target, it.before, it.after) } })
            .trimmed(maxBytes)

    /**
     * Puts a saved journal back after the project was loaded. It is only taken when it fits the files: the newest undo
     * step must end in what each of its targets is now (and the next redo step must start from it); otherwise the
     * project was changed outside foxhg and the history is dropped. Returns whether it was taken.
     */
    @kotlin.jvm.Synchronized
    fun restoreHistory(snapshot: UndoSnapshot): Boolean {
        val lastUndo = snapshot.undo.lastOrNull()
        val nextRedo = snapshot.redo.lastOrNull()
        if (lastUndo != null && lastUndo.any { !same(stateOf(it.target), it.after) }) return false
        if (nextRedo != null && nextRedo.any { !same(stateOf(it.target), it.before) }) return false
        if (lastUndo == null && nextRedo == null) return false
        undoSteps.clear(); redoSteps.clear()
        // Restored steps are old: a new edit must not merge into them.
        val old = Long.MIN_VALUE / 2
        snapshot.undo.mapTo(undoSteps) { step -> Step(step.map { Change(it.target, it.before, it.after) }, old) }
        snapshot.redo.mapTo(redoSteps) { step -> Step(step.map { Change(it.target, it.before, it.after) }, old) }
        historySavedVersion = historyVersion
        return true
    }

    val canUndo: Boolean get() = undoSteps.isNotEmpty()
    val canRedo: Boolean get() = redoSteps.isNotEmpty()

    /** What the next [undo] would change, or empty. */
    val undoTargets: List<EditTarget> get() = undoSteps.lastOrNull()?.changes?.map { it.target } ?: emptyList()

    /** What the next [redo] would change, or empty. */
    val redoTargets: List<EditTarget> get() = redoSteps.lastOrNull()?.changes?.map { it.target } ?: emptyList()

    /** Reverts the latest step; returns what it touched, or null when there is nothing to undo. */
    @kotlin.jvm.Synchronized
    fun undo(): List<EditTarget>? {
        val step = undoSteps.removeLastOrNull() ?: return null
        historyVersion++
        step.changes.asReversed().forEach { setRaw(it.target, it.before) }
        redoSteps += step
        return step.changes.map { it.target }
    }

    /** Re-applies the step the last [undo] reverted; returns what it touched, or null when there is nothing to redo. */
    @kotlin.jvm.Synchronized
    fun redo(): List<EditTarget>? {
        val step = redoSteps.removeLastOrNull() ?: return null
        historyVersion++
        step.changes.forEach { setRaw(it.target, it.after) }
        undoSteps += step
        return step.changes.map { it.target }
    }

    private fun stateOf(target: EditTarget): ByteArray? = when (target) {
        is EditTarget.File -> edits[target.path.trim('/')]
        EditTarget.Arm9 -> arm9Edit
        is EditTarget.Overlay -> overlayEdits[target.id]
    }

    /** Sets [target] without journaling. Stored arrays are never modified in place, so the journal can share them. */
    private fun setRaw(target: EditTarget, bytes: ByteArray?) {
        when (target) {
            is EditTarget.File -> {
                val key = target.path.trim('/')
                if (bytes == null) edits.remove(key) else edits[key] = bytes
                messagesChanged(key)
            }
            EditTarget.Arm9 -> arm9Edit = bytes
            is EditTarget.Overlay -> if (bytes == null) overlayEdits.remove(target.id) else overlayEdits[target.id] = bytes
        }
    }

    private fun same(a: ByteArray?, b: ByteArray?): Boolean = if (a == null || b == null) a == null && b == null else a.contentEquals(b)

    /** Journals the change of [target] from [before] to what it is now; nothing when it did not change. */
    private fun record(target: EditTarget, before: ByteArray?) = push(listOf(Change(target, before, stateOf(target))))

    private fun push(changes: List<Change>) {
        val real = changes.filterNot { same(it.before, it.after) }
        if (real.isEmpty()) return
        historyVersion++
        redoSteps.clear()
        val now = clock()
        val last = undoSteps.lastOrNull()
        if (real.size == 1 && last != null && last.changes.size == 1 && last.changes[0].target == real[0].target && now - last.at <= COALESCE_MILLIS) {
            undoSteps.removeLast()
            val merged = Change(real[0].target, last.changes[0].before, real[0].after)
            if (!same(merged.before, merged.after)) undoSteps += Step(listOf(merged), now)
            return
        }
        undoSteps += Step(real, now)
        if (undoSteps.size > MAX_STEPS) undoSteps.removeFirst()
    }

    val editedPaths: Set<String> get() = edits.keys
    val isDirty: Boolean get() = edits.isNotEmpty() || arm9Edit != null || overlayEdits.isNotEmpty()

    /** The ARM9 edit (decompressed image), or null when the ARM9 is as in the ROM. */
    val editedArm9: ByteArray? get() = arm9Edit?.copyOf()

    /** Whether builds store the ARM9 compressed. Default: as the ROM does, which keeps the ROM size. */
    var compressArm9: Boolean = (originalArm9 as? FoxResult.Ok)?.let { Blz.looksCompressed(rom.readArm9()) } ?: false

    /** The decompressed ARM9 including edits; code patches read and write this image. */
    @kotlin.jvm.Synchronized
    fun readArm9(): FoxResult<ByteArray> = arm9Edit?.let { FoxResult.Ok(it.copyOf()) } ?: when (val o = originalArm9) {
        is FoxResult.Ok -> FoxResult.Ok(o.value.copyOf())
        is FoxResult.Err -> o
    }

    /** Stores [plain] as the new decompressed ARM9; identical to the ROM's own means no edit. */
    @kotlin.jvm.Synchronized
    fun writeArm9(plain: ByteArray): FoxResult<Unit> {
        val original = (originalArm9 as? FoxResult.Err)?.let { return it } ?: (originalArm9 as FoxResult.Ok).value
        val before = arm9Edit
        arm9Edit = if (plain.contentEquals(original)) null else plain.copyOf()
        record(EditTarget.Arm9, before)
        return FoxResult.Ok(Unit)
    }

    /** Overlay ids (ARM9 overlay table) whose decompressed image the user changed. */
    val editedOverlays: Set<Int> get() = overlayEdits.keys

    @kotlin.jvm.Synchronized
    fun editedOverlayImages(): Map<Int, ByteArray> = overlayEdits.mapValues { it.value.copyOf() }

    private fun overlayEntry(id: Int) = rom.arm9Overlays.firstOrNull { it.id == id }

    private fun originalOverlay(id: Int): FoxResult<ByteArray> = originalOverlays.getOrPut(id) {
        val entry = overlayEntry(id) ?: return@getOrPut FoxResult.Err("The ROM has no overlay $id")
        val stored = rom.readOverlay(entry) ?: return@getOrPut FoxResult.Err("Overlay $id has no file in the ROM")
        if (entry.isCompressed) Blz.decompress(stored) else FoxResult.Ok(stored)
    }

    /** The decompressed overlay [id] including edits. */
    @kotlin.jvm.Synchronized
    fun readOverlay(id: Int): FoxResult<ByteArray> = overlayEdits[id]?.let { FoxResult.Ok(it.copyOf()) } ?: when (val o = originalOverlay(id)) {
        is FoxResult.Ok -> FoxResult.Ok(o.value.copyOf())
        is FoxResult.Err -> o
    }

    /** Stores [plain] as the new decompressed overlay [id]. Its size must not change; identical to the ROM's means no edit. */
    @kotlin.jvm.Synchronized
    fun writeOverlay(id: Int, plain: ByteArray): FoxResult<Unit> {
        val original = when (val o = originalOverlay(id)) {
            is FoxResult.Ok -> o.value
            is FoxResult.Err -> return o
        }
        if (plain.size != original.size) return FoxResult.Err("Overlay $id must stay ${original.size} bytes, got ${plain.size}")
        val before = overlayEdits[id]
        if (plain.contentEquals(original)) overlayEdits.remove(id) else overlayEdits[id] = plain.copyOf()
        record(EditTarget.Overlay(id), before)
        return FoxResult.Ok(Unit)
    }

    @kotlin.jvm.Synchronized
    fun revertArm9() {
        val before = arm9Edit
        arm9Edit = null
        record(EditTarget.Arm9, before)
    }

    private var namesCache: GameNames? = null
    private var namesLoaded = false

    /**
     * Message archives and the name lists derived from them, with the user's edits applied. Rebuilt on the next read
     * after the message archives change, so an editor opened later shows renamed species, moves and so on.
     */
    val names: GameNames?
        @kotlin.jvm.Synchronized get() {
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

    @kotlin.jvm.Synchronized
    fun read(path: String): ByteArray? = edits[path.trim('/')]?.copyOf() ?: rom.read(path)

    @kotlin.jvm.Synchronized
    fun isEdited(path: String): Boolean = edits.containsKey(path.trim('/'))

    /** Stores [bytes] as the new content of [path]. A file that ends up identical to the ROM's is not kept as an edit. */
    @kotlin.jvm.Synchronized
    fun write(path: String, bytes: ByteArray): FoxResult<Unit> {
        val key = path.trim('/')
        val file = rom.file(key) ?: return FoxResult.Err("The ROM has no file '$key'")
        val before = edits[key]
        if (bytes.contentEquals(rom.read(file))) edits.remove(key) else edits[key] = bytes.copyOf()
        messagesChanged(key)
        record(EditTarget.File(key), before)
        return FoxResult.Ok(Unit)
    }

    @kotlin.jvm.Synchronized
    fun revert(path: String) {
        val key = path.trim('/')
        val before = edits[key]
        edits.remove(key)
        messagesChanged(path)
        record(EditTarget.File(key), before)
    }

    @kotlin.jvm.Synchronized
    fun revertAll() {
        val changes = edits.map { (path, bytes) -> Change(EditTarget.File(path), bytes, null) } +
            listOfNotNull(arm9Edit?.let { Change(EditTarget.Arm9, it, null) }) +
            overlayEdits.map { (id, bytes) -> Change(EditTarget.Overlay(id), bytes, null) }
        edits.clear()
        arm9Edit = null
        overlayEdits.clear()
        namesLoaded = false
        push(changes)
    }

    @kotlin.jvm.Synchronized
    fun editedFiles(): Map<String, ByteArray> = edits.mapValues { it.value.copyOf() }

    /** Writes a new `.nds` with every edit applied. The original ROM and project stay untouched. */
    fun build(sink: RomSink, progress: (Double) -> Unit = {}): FoxResult<Unit> {
        // Compressing the changed overlays and the ARM9 is the slow start (a share of 0.3), copying the ROM the rest.
        val preparing = overlayEdits.size + (if (arm9Edit != null) 1 else 0)
        var prepared = 0
        fun prepareStep() { prepared++; progress(0.3 * prepared / maxOf(1, preparing)) }
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
            prepareStep()
        }
        val storedArm9 = arm9Edit?.let { plain ->
            val footer = Arm9.footer(rom) ?: return FoxResult.Err("This ROM's ARM9 has no module parameters footer")
            when (val packed = Arm9.forRom(plain, footer.paramsOffset, compressArm9)) {
                is FoxResult.Ok -> packed.value
                is FoxResult.Err -> return packed
            }.also { prepareStep() }
        }
        return NdsBuilder.build(rom, source, RomEdits(byId, storedArm9), sink) { progress(0.3 + 0.7 * it) }
    }

    companion object {
        /** Writes to the same target closer together than this are one undo step. */
        const val COALESCE_MILLIS = 1000L

        /** Undo steps kept; the oldest go first. */
        const val MAX_STEPS = 200

        /** The most data of the journal that is written to the project folder. */
        const val MAX_HISTORY_BYTES = 96L * 1024 * 1024

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
