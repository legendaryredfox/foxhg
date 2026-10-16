package io.github.legendaryredfox.foxhg.core.patch

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** The binary a [ByteEdit] changes: it knows how to read itself from a project and how to write itself back (journaled, so undo works). */
sealed interface PatchTarget {
    fun read(project: RomProject): FoxResult<ByteArray>
    fun write(project: RomProject, bytes: ByteArray): FoxResult<Unit>

    /** The decompressed ARM9; offsets are file offsets (the runtime address is 0x02000000 + offset). */
    data object Arm9 : PatchTarget {
        override fun read(project: RomProject) = project.readArm9()
        override fun write(project: RomProject, bytes: ByteArray) = project.writeArm9(bytes)
        override fun toString() = "ARM9"
    }

    /** The decompressed overlay [id]. */
    data class Overlay(val id: Int) : PatchTarget {
        override fun read(project: RomProject) = project.readOverlay(id)
        override fun write(project: RomProject, bytes: ByteArray) = project.writeOverlay(id, bytes)
        override fun toString() = "overlay $id"
    }

    /** Member 0 of [HgssLayout.SYNTHETIC_OVERLAY]; offsets are file offsets (the runtime address is [HgssLayout.SYNTHETIC_OVERLAY_ADDRESS] + offset). */
    data object SyntheticOverlay : PatchTarget {
        private fun narc(project: RomProject): FoxResult<Narc> {
            val bytes = project.read(HgssLayout.SYNTHETIC_OVERLAY) ?: return FoxResult.Err("The ROM has no ${HgssLayout.SYNTHETIC_OVERLAY}")
            return Narc.parse(bytes)
        }

        override fun read(project: RomProject): FoxResult<ByteArray> = when (val n = narc(project)) {
            is FoxResult.Ok -> n.value.files.firstOrNull()?.let { FoxResult.Ok(it.copyOf()) } ?: FoxResult.Err("${HgssLayout.SYNTHETIC_OVERLAY} has no files")
            is FoxResult.Err -> n
        }

        override fun write(project: RomProject, bytes: ByteArray): FoxResult<Unit> = when (val n = narc(project)) {
            is FoxResult.Ok -> project.write(HgssLayout.SYNTHETIC_OVERLAY, Narc(n.value.files.toMutableList().also { it[0] = bytes }, n.value.nameTable).toBytes())
            is FoxResult.Err -> n
        }

        override fun toString() = "synthetic overlay"
    }
}

/** Replace [original] with [replacement] (same length) at [offset] of [target]. [what] says what the bytes are, for the change list. */
class ByteEdit(val target: PatchTarget, val offset: Int, val original: ByteArray, val replacement: ByteArray, val what: String) {
    init {
        require(original.size == replacement.size) { "An edit keeps the length of what it replaces" }
    }

    fun describe(): String = "$target 0x${offset.toString(16).uppercase()}: $what (${original.hex()} -> ${replacement.hex()})"

    internal fun matches(image: ByteArray, expected: ByteArray): Boolean =
        offset >= 0 && offset + expected.size <= image.size && expected.indices.all { image[offset + it] == expected[it] }

    internal fun isApplied(image: ByteArray) = matches(image, replacement)
    internal fun isOriginal(image: ByteArray) = matches(image, original)

    internal fun write(image: ByteArray) = replacement.copyInto(image, offset)

    private fun ByteArray.hex(): String =
        if (size <= 16) joinToString(" ") { "%02X".format(it) } else take(8).joinToString(" ") { "%02X".format(it) } + " ... ($size bytes)"
}

/** Builds an edit from hex strings ("FF 01"). */
fun edit(target: PatchTarget, offset: Int, original: String, replacement: String, what: String) =
    ByteEdit(target, offset, hexBytes(original), hexBytes(replacement), what)

/** An edit into an area that must be empty (all zero) now. */
fun fill(target: PatchTarget, offset: Int, bytes: ByteArray, what: String) = ByteEdit(target, offset, ByteArray(bytes.size), bytes, what)

fun hexBytes(text: String): ByteArray = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()

/**
 * A patch made only of [ByteEdit]s. It is [PatchStatus.Applied] when every edit's replacement is in place,
 * [PatchStatus.Available] when every edit's original bytes are, and unavailable otherwise (also when [requires] is not applied).
 * A patch whose edits depend on the ROM overrides [editsFor].
 */
open class BytePatch(
    override val id: String,
    override val title: String,
    override val description: String,
    private val fixedEdits: List<ByteEdit> = emptyList(),
    protected val requires: RomPatch? = null,
) : RomPatch {
    val edits: List<ByteEdit> get() = fixedEdits

    /** The edits for [project]; a failure says why the patch can not be built for this ROM. */
    open fun editsFor(project: RomProject): FoxResult<List<ByteEdit>> = FoxResult.Ok(fixedEdits)

    override val changes: List<String> get() = fixedEdits.map { it.describe() }

    override fun changesFor(project: RomProject): List<String> = (editsFor(project) as? FoxResult.Ok)?.value?.map { it.describe() } ?: changes

    private fun images(project: RomProject, edits: List<ByteEdit>): FoxResult<Map<PatchTarget, ByteArray>> {
        val out = LinkedHashMap<PatchTarget, ByteArray>()
        for (target in edits.map { it.target }.distinct()) {
            when (val r = target.read(project)) {
                is FoxResult.Ok -> out[target] = r.value
                is FoxResult.Err -> return r
            }
        }
        return FoxResult.Ok(out)
    }

    override fun status(project: RomProject): PatchStatus {
        val edits = when (val e = editsFor(project)) {
            is FoxResult.Ok -> e.value
            is FoxResult.Err -> return PatchStatus.Unavailable(e.message)
        }
        val loaded = when (val r = images(project, edits)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return PatchStatus.Unavailable(r.message)
        }
        if (edits.all { it.isApplied(loaded.getValue(it.target)) }) return PatchStatus.Applied
        requires?.let { if (it.status(project) != PatchStatus.Applied) return PatchStatus.Unavailable("Apply \"${it.title}\" first") }
        if (edits.all { it.isOriginal(loaded.getValue(it.target)) }) return PatchStatus.Available
        return PatchStatus.Unavailable("This ROM does not hold the bytes the patch replaces (another patch or edit changed them)")
    }

    override fun apply(project: RomProject): FoxResult<Unit> {
        when (val s = status(project)) {
            PatchStatus.Available -> Unit
            PatchStatus.Applied -> return FoxResult.Err("\"$title\" is already applied")
            is PatchStatus.Unavailable -> return FoxResult.Err(s.reason)
        }
        val edits = (editsFor(project) as FoxResult.Ok).value
        val loaded = (images(project, edits) as FoxResult.Ok).value.mapValues { it.value.copyOf() }
        edits.forEach { it.write(loaded.getValue(it.target)) }
        for ((target, bytes) in loaded) {
            val written = target.write(project, bytes)
            if (written is FoxResult.Err) return written
        }
        return FoxResult.Ok(Unit)
    }
}
