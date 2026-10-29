package io.github.legendaryredfox.foxhg.core.nds

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes the ROM of [project] with every edit applied as an unpacked folder DSPRE opens (see [DspreFolder]) at [folder].
 * [folder] must not exist yet or be empty. The ROM is built into a temporary file next to [folder] first, and the folder is
 * written under a `.part` name and moved into place when it is complete, so a failure leaves nothing half written.
 * [progress] runs from 0 to 1.
 */
fun exportDspreFolder(project: RomProject, folder: Path, progress: (Double) -> Unit = {}): FoxResult<Unit> {
    val target = folder.toAbsolutePath()
    val parent = target.parent ?: return FoxResult.Err("Cannot write the folder $folder")
    val name = target.fileName.toString()
    val part = parent.resolve("$name.part")
    val temp = parent.resolve("$name.rom.part")
    try {
        if (Files.exists(target) && !(Files.isDirectory(target) && Files.list(target).use { !it.findAny().isPresent })) {
            return FoxResult.Err("$target already exists and is not empty")
        }
        Files.createDirectories(parent)
        deleteTree(part)
        Files.deleteIfExists(temp)
        FileRomSink(temp).use { sink ->
            val built = project.build(sink) { progress(0.5 * it) }
            if (built is FoxResult.Err) return built
        }
        FileRomSource(temp).use { source ->
            val rom = when (val opened = NdsRom.open(source)) {
                is FoxResult.Ok -> opened.value
                is FoxResult.Err -> return opened
            }
            val entries = when (val listed = DspreFolder.entries(rom)) {
                is FoxResult.Ok -> listed.value
                is FoxResult.Err -> return listed
            }
            Files.createDirectories(part)
            entries.forEachIndexed { i, entry ->
                val file = part.resolve(entry.path)
                Files.createDirectories(file.parent)
                Files.write(file, entry.bytes())
                progress(0.5 + 0.5 * (i + 1) / entries.size)
            }
        }
        if (Files.exists(target)) Files.delete(target)
        Files.move(part, target)
        return FoxResult.Ok(Unit)
    } catch (e: IOException) {
        return FoxResult.Err("Cannot write $target: ${e.message}")
    } finally {
        runCatching { Files.deleteIfExists(temp) }
        runCatching { deleteTree(part) }
    }
}

private fun deleteTree(path: Path) {
    if (!Files.exists(path)) return
    Files.walk(path).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
}
