package io.github.legendaryredfox.foxhg.core.project

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/**
 * A project on disk: `<dir>/project.properties` (path of the ROM), `<dir>/files/<rom path>` for every file the
 * user changed and `<dir>/arm9.bin` (decompressed) when the ARM9 was changed, and `<dir>/overlays/<id>.bin` (decompressed) for each changed overlay. Nothing else is stored, so a project is a few megabytes at most and the ROM is never copied.
 * Writes go to a temporary file first and are renamed into place.
 */
object ProjectStore {
    private const val PROPERTIES = "project.properties"
    private const val FILES = "files"
    private const val OVERLAYS = "overlays"
    private const val ARM9_FILE = "arm9.bin" // the decompressed ARM9 image, only when edited
    private const val HISTORY_FILE = "history.bin" // the undo and redo steps, see UndoSnapshot

    fun isProject(directory: Path): Boolean = directory.resolve(PROPERTIES).exists()

    /** Creates an empty project in [directory] (made if needed). Refuses to overwrite an existing project. */
    fun create(directory: Path, romPath: Path): FoxResult<RomProjectFiles> = try {
        if (isProject(directory)) {
            FoxResult.Err("$directory already contains a foxhg project")
        } else {
            Files.createDirectories(directory.resolve(FILES))
            writeProperties(directory, linkedMapOf("rom" to romPath.toAbsolutePath().toString()))
            FoxResult.Ok(RomProjectFiles(directory, romPath))
        }
    } catch (e: IOException) {
        FoxResult.Err("Cannot create project in $directory: ${e.message}")
    }

    /** Points the project at a ROM that moved. The new ROM must open as a ROM foxhg understands. */
    fun relink(directory: Path, newRom: Path): FoxResult<Unit> = try {
        if (!isProject(directory)) {
            FoxResult.Err("$directory is not a foxhg project")
        } else if (!newRom.exists()) {
            FoxResult.Err("$newRom does not exist")
        } else {
            val check = FileRomSource(newRom).use { io.github.legendaryredfox.foxhg.core.nds.NdsRom.open(it) }
            when (check) {
                is FoxResult.Err -> check
                is FoxResult.Ok -> {
                    val properties = readProperties(directory)
                    properties["rom"] = newRom.toAbsolutePath().toString()
                    writeProperties(directory, properties)
                    FoxResult.Ok(Unit)
                }
            }
        }
    } catch (e: IOException) {
        FoxResult.Err("Cannot update project $directory: ${e.message}")
    } catch (e: RuntimeException) {
        FoxResult.Err("$newRom is not a ROM foxhg can read: ${e.message ?: e::class.simpleName}")
    }

    /** The ROM path a project points at, whether or not it still exists. */
    fun romOf(directory: Path): Path? = readProperties(directory)["rom"]?.let { Path.of(it) }

    private fun readProperties(directory: Path): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        val file = directory.resolve(PROPERTIES)
        if (file.exists()) {
            Files.readAllLines(file).forEach { line ->
                val eq = line.indexOf('=')
                if (eq > 0) map[line.substring(0, eq).trim()] = line.substring(eq + 1)
            }
        }
        return map
    }

    private fun writeProperties(directory: Path, properties: Map<String, String>) {
        atomicWrite(directory.resolve(PROPERTIES), properties.entries.joinToString("") { "${it.key}=${it.value}\n" }.encodeToByteArray())
    }

    fun save(directory: Path, project: RomProject, withHistory: Boolean = true): FoxResult<Unit> = try {
        if (!isProject(directory)) return FoxResult.Err("$directory is not a foxhg project")
        val filesDir = directory.resolve(FILES)
        Files.createDirectories(filesDir)
        val properties = readProperties(directory)
        properties["compressArm9"] = project.compressArm9.toString()
        writeProperties(directory, properties)
        val edited = project.editedFiles()
        for ((path, bytes) in edited) {
            // Edit keys come from file names in the ROM: a hacked ROM must not make a project write outside its own folder.
            val target = filesDir.resolve(path).normalize()
            if (!target.startsWith(filesDir.normalize())) return FoxResult.Err("Refusing to save '$path': it lies outside the project folder")
            atomicWrite(target, bytes)
        }
        val arm9File = directory.resolve(ARM9_FILE)
        val arm9 = project.editedArm9
        if (arm9 != null) atomicWrite(arm9File, arm9) else arm9File.deleteIfExists()
        val overlaysDir = directory.resolve(OVERLAYS)
        val overlays = project.editedOverlayImages()
        for ((id, bytes) in overlays) atomicWrite(overlaysDir.resolve("$id.bin"), bytes)
        if (overlaysDir.exists()) {
            Files.list(overlaysDir).use { stream ->
                stream.filter { it.isRegularFile() && it.fileName.toString().removeSuffix(".bin").toIntOrNull() !in overlays }.forEach { it.deleteIfExists() }
            }
        }
        if (withHistory && project.historyDirty) {
            val snapshot = project.historySnapshot()
            val file = directory.resolve(HISTORY_FILE)
            if (snapshot.undo.isEmpty() && snapshot.redo.isEmpty()) file.deleteIfExists() else atomicWrite(file, snapshot.toBytes())
            project.markHistorySaved()
        }
        // Reverted files must disappear too, or they would come back on the next load.
        Files.walk(filesDir).use { stream ->
            stream.filter { it.isRegularFile() }.forEach { file ->
                if (file.relativeTo(filesDir).joinToString("/") !in edited) file.deleteIfExists()
            }
        }
        FoxResult.Ok(Unit)
    } catch (e: IOException) {
        FoxResult.Err("Cannot save project to $directory: ${e.message}")
    }

    /** Opens the ROM named by the project and applies its saved edits. The caller closes [RomProjectFiles.source]. */
    fun load(directory: Path): FoxResult<Loaded> = try {
        if (!isProject(directory)) return FoxResult.Err("$directory is not a foxhg project (no $PROPERTIES)")
        val properties = readProperties(directory)
        val romPath = properties["rom"]?.let { Path.of(it) } ?: return FoxResult.Err("$PROPERTIES has no rom= line")
        if (!romPath.exists()) return FoxResult.Err("The ROM $romPath of this project no longer exists")

        val edits = LinkedHashMap<String, ByteArray>()
        val filesDir = directory.resolve(FILES)
        if (filesDir.exists()) {
            Files.walk(filesDir).use { stream ->
                stream.filter { it.isRegularFile() }.forEach { file ->
                    // A save that was cut short leaves its temp file behind; it is not an edit.
                    if (isTempFile(file)) file.deleteIfExists() else edits[file.relativeTo(filesDir).joinToString("/")] = Files.readAllBytes(file)
                }
            }
        }
        val arm9Path = directory.resolve(ARM9_FILE)
        val arm9 = if (arm9Path.exists()) Files.readAllBytes(arm9Path) else null
        val overlays = LinkedHashMap<Int, ByteArray>()
        val overlaysDir = directory.resolve(OVERLAYS)
        if (overlaysDir.exists()) {
            Files.list(overlaysDir).use { stream ->
                stream.filter { it.isRegularFile() }.forEach { file ->
                    file.fileName.toString().removeSuffix(".bin").toIntOrNull()?.let { overlays[it] = Files.readAllBytes(file) }
                }
            }
        }
        val source = FileRomSource(romPath)
        try {
            when (val project = RomProject.open(source, edits, arm9, overlays)) {
                is FoxResult.Ok -> {
                    properties["compressArm9"]?.toBooleanStrictOrNull()?.let { project.value.compressArm9 = it }
                    // The undo history is a convenience: a missing, damaged or out-of-date file just means no history.
                    directory.resolve(HISTORY_FILE).takeIf { it.exists() }?.let { file ->
                        (UndoSnapshot.parse(Files.readAllBytes(file)) as? FoxResult.Ok)?.let { project.value.restoreHistory(it.value) }
                    }
                    FoxResult.Ok(Loaded(project.value, source, romPath))
                }
                is FoxResult.Err -> {
                    source.close()
                    project
                }
            }
        } catch (e: RuntimeException) {
            // A ROM that parses as far as the header but is not sound must not leave its file open or take the app down.
            source.close()
            FoxResult.Err("Cannot open the ROM $romPath of this project: ${e.message ?: e::class.simpleName}")
        }
    } catch (e: IOException) {
        FoxResult.Err("Cannot read project $directory: ${e.message}")
    }

    private fun isTempFile(file: Path): Boolean = file.fileName.toString().let { it.startsWith(".foxhg") && it.endsWith(".tmp") }

    class Loaded(val project: RomProject, val source: FileRomSource, val romPath: Path)

    private fun atomicWrite(target: Path, bytes: ByteArray) {
        Files.createDirectories(target.parent)
        val temp = Files.createTempFile(target.parent, ".foxhg", ".tmp")
        try {
            // Flushed to disk before the rename, so a power loss cannot leave an empty file under the final name.
            java.nio.channels.FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                channel.write(java.nio.ByteBuffer.wrap(bytes))
                channel.force(true)
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temp.deleteIfExists()
        }
    }
}

/** Handle returned by [ProjectStore.create]. */
class RomProjectFiles(val directory: Path, val romPath: Path)
