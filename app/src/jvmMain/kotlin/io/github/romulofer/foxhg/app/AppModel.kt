package io.github.romulofer.foxhg.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.nds.FileRomSink
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.nds.NdsHeader
import io.github.romulofer.foxhg.core.nds.readNdsHeader
import io.github.romulofer.foxhg.core.project.ProjectStore
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path

/** Something the window shows on top of the current screen. */
sealed interface AppDialog {
    object NewProject : AppDialog

    /** Name and place a project for the open ROM session; [then] runs once it is saved. */
    class SaveProjectAs(val then: (() -> Unit)? = null) : AppDialog

    /** Work that is not saved anywhere would be lost by [then]; ask what to do first. */
    class UnsavedWork(val then: () -> Unit) : AppDialog

    /** The ROM of the project in [projectDir] is gone; ask where it went. */
    class RelinkRom(val projectDir: Path, val message: String) : AppDialog
}

/** Runs [action] after [delayMillis]; the returned handle cancels it. Swapped for a manual one in tests. */
typealias Scheduler = (delayMillis: Long, action: () -> Unit) -> AutoCloseable

internal val swingScheduler: Scheduler = { delay, action ->
    val timer = javax.swing.Timer(delay.toInt()) { action() }.apply { isRepeats = false; start() }
    AutoCloseable { timer.stop() }
}

/**
 * Window state. Depends on `core` only; no Compose widgets, so it can be tested without a window.
 *
 * A session is either a bare ROM (edits live in memory until built) or a project (edits are saved into the project
 * folder, automatically after every change unless [autosave] is off).
 */
class AppModel(
    private val recents: Recents = Recents.default(),
    private val scheduler: Scheduler = swingScheduler,
    private val autosaveDelayMillis: Long = 700,
) {
    var recent: List<RecentEntry> by mutableStateOf(recents.load())
        private set
    var romPath: Path? by mutableStateOf(null)
        private set
    var header: NdsHeader? by mutableStateOf(null)
        private set
    var project: RomProject? by mutableStateOf(null)
        private set
    /** The folder of the open project, or null when only a ROM is open. */
    var projectDir: Path? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
    var status: String? by mutableStateOf(null)
    var selected: EditorKind? by mutableStateOf(null)
    var dialog: AppDialog? by mutableStateOf(null)
    var autosave: Boolean by mutableStateOf(true)

    /** Bumped whenever an editor writes to the project, so views of [RomProject.isDirty] refresh. */
    var revision: Int by mutableIntStateOf(0)
        private set
    private var savedRevision: Int by mutableIntStateOf(0)

    private var source: FileRomSource? = null
    private var pendingSave: AutoCloseable? = null

    val projectName: String? get() = projectDir?.fileName?.toString()

    // Both properties read [revision] first: Compose only re-reads them when a state they actually read changes, and `isDirty` is not state.

    /** Changes that exist only in memory: bare-ROM edits, or a project whose latest edits are not on disk yet. */
    val hasUnsavedWork: Boolean
        get() = revision >= 0 && project != null && if (projectDir == null) project?.isDirty == true else revision != savedRevision

    /** True for a bare ROM with edits: those are lost on exit unless saved into a project or built. */
    val needsProjectToKeepWork: Boolean get() = revision >= 0 && project != null && projectDir == null && project?.isDirty == true

    val isDirty: Boolean get() = revision >= 0 && project?.isDirty == true

    // --- opening ---------------------------------------------------------------------------------------------------

    /** Opens a ROM with no project. Edits stay in memory until saved as a project or built. */
    fun openRom(path: Path) {
        val headerResult = readNdsHeader(path)
        if (headerResult is FoxResult.Err) {
            fail(headerResult.message)
            return
        }
        val romHeader = (headerResult as FoxResult.Ok).value
        if (!romHeader.game.supported) {
            fail("${romHeader.game.displayName} is not supported yet (${romHeader.gameCode})")
            return
        }
        val newSource = try {
            FileRomSource(path)
        } catch (e: java.io.IOException) {
            fail("Cannot open $path: ${e.message}")
            return
        }
        when (val opened = RomProject.open(newSource)) {
            is FoxResult.Ok -> {
                attach(opened.value, newSource, path, projectDir = null)
                recent = recents.add(RecentKind.ROM, path)
                status = "Opened ${path.fileName}"
            }
            is FoxResult.Err -> {
                newSource.close()
                fail(opened.message)
            }
        }
    }

    /** Creates a project in [directory] for [rom] and opens it. */
    fun newProject(rom: Path, directory: Path): Boolean {
        when (val created = ProjectStore.create(directory, rom)) {
            is FoxResult.Err -> { fail(created.message); return false }
            is FoxResult.Ok -> {}
        }
        return openProject(directory)
    }

    /** Opens the project in [directory]. A missing ROM opens the relink dialog instead of failing outright. */
    fun openProject(directory: Path): Boolean {
        when (val loaded = ProjectStore.load(directory)) {
            is FoxResult.Err -> {
                if (loaded.message.contains("no longer exists")) {
                    dialog = AppDialog.RelinkRom(directory, loaded.message)
                } else {
                    fail(loaded.message)
                }
                return false
            }
            is FoxResult.Ok -> {
                val l = loaded.value
                attach(l.project, l.source, l.romPath, directory)
                recent = recents.add(RecentKind.PROJECT, directory)
                status = "Opened project ${directory.fileName}"
                return true
            }
        }
    }

    /** Points the project at a moved ROM, then opens it. */
    fun relinkRom(directory: Path, rom: Path): Boolean {
        when (val r = ProjectStore.relink(directory, rom)) {
            is FoxResult.Err -> { fail(r.message); return false }
            is FoxResult.Ok -> {}
        }
        dialog = null
        return openProject(directory)
    }

    private fun attach(newProject: RomProject, newSource: FileRomSource, rom: Path, projectDir: Path?) {
        cancelPendingSave()
        source?.close()
        source = newSource
        project = newProject
        romPath = rom
        header = newProject.rom.header
        this.projectDir = projectDir
        error = null
        selected = null
        revision++
        savedRevision = revision
    }

    // --- saving ----------------------------------------------------------------------------------------------------

    /** Called by editors after they changed the project. */
    fun projectChanged() {
        revision++
        if (autosave && projectDir != null) {
            cancelPendingSave()
            pendingSave = scheduler(autosaveDelayMillis) { pendingSave = null; saveProject() }
        }
    }

    /** Writes the open project's edits into its folder. */
    fun saveProject(): Boolean {
        val dir = projectDir ?: return false
        val p = project ?: return false
        cancelPendingSave()
        return when (val r = ProjectStore.save(dir, p)) {
            is FoxResult.Ok -> {
                savedRevision = revision
                error = null
                status = "Saved"
                true
            }
            is FoxResult.Err -> { fail(r.message); false }
        }
    }

    /** Turns the open session into a project in [directory] (made if needed) and saves it there. */
    fun saveProjectAs(directory: Path): Boolean {
        val p = project ?: return false
        val rom = romPath ?: return false
        if (!ProjectStore.isProject(directory)) {
            when (val created = ProjectStore.create(directory, rom)) {
                is FoxResult.Err -> { fail(created.message); return false }
                is FoxResult.Ok -> {}
            }
        }
        cancelPendingSave()
        return when (val r = ProjectStore.save(directory, p)) {
            is FoxResult.Ok -> {
                projectDir = directory
                savedRevision = revision
                error = null
                status = "Saved project ${directory.fileName}"
                recent = recents.add(RecentKind.PROJECT, directory)
                true
            }
            is FoxResult.Err -> { fail(r.message); false }
        }
    }

    private fun cancelPendingSave() {
        pendingSave?.close()
        pendingSave = null
    }

    // --- guarding --------------------------------------------------------------------------------------------------

    /**
     * Runs [action] now when nothing would be lost; otherwise asks first. A project is flushed to disk instead of
     * asking, since its edits are already meant to be kept.
     */
    fun guard(action: () -> Unit) {
        if (projectDir != null && hasUnsavedWork) saveProject()
        if (needsProjectToKeepWork) dialog = AppDialog.UnsavedWork(action) else action()
    }

    // --- building --------------------------------------------------------------------------------------------------

    /** Writes the ROM with every edit applied to [target]. The opened ROM is not modified. */
    fun build(target: Path) {
        val project = project ?: return
        if (target.toAbsolutePath() == romPath?.toAbsolutePath()) {
            fail("Choose a different file than the ROM you opened")
            return
        }
        FileRomSink(target).use { sink ->
            when (val result = project.build(sink)) {
                is FoxResult.Ok -> {
                    error = null
                    status = "Built $target"
                }
                is FoxResult.Err -> fail(result.message)
            }
        }
    }

    /** Flushes a pending autosave and releases the ROM file. */
    fun close() {
        if (projectDir != null && hasUnsavedWork) saveProject()
        cancelPendingSave()
        source?.close()
        source = null
    }

    private fun fail(message: String) {
        error = message
        status = null
    }

    companion object {
        /** A new project folder inside [parent] for a project called [name]. Null when the name cannot be a folder name. */
        fun projectFolder(parent: Path, name: String): Path? {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.any { it == '/' || it == '\\' || it == ':' } || trimmed == "." || trimmed == "..") return null
            return parent.resolve(trimmed)
        }

        fun defaultProjectsDir(): Path = Path.of(System.getProperty("user.home"), "foxhg-projects")

        fun isUsableProjectFolder(folder: Path): Boolean = !Files.exists(folder.resolve("project.properties"))
    }
}
