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
import io.github.romulofer.foxhg.core.project.EditTarget
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

    /** The settings window; [notice] says why it opened by itself. */
    class Settings(val notice: String? = null) : AppDialog

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
    private val settingsStore: SettingsStore = SettingsStore.default(),
) {
    var settings: AppSettings by mutableStateOf(settingsStore.load())
        private set

    /** A job running in the background, with how far along it is (0..1, null when it cannot say). The window shows it and blocks editing meanwhile. */
    class Task(val title: String, val fraction: Double?)

    var task: Task? by mutableStateOf(null)
        private set
    private var worker: Thread? = null

    /**
     * Runs [work] on a background thread under the progress window. [work] reports its progress with the function it is
     * given. Does nothing while another task runs; an exception in [work] is shown as an error.
     */
    fun startTask(title: String, work: (report: (Double) -> Unit) -> Unit) {
        if (task != null) return
        task = Task(title, 0.0)
        var last = -1.0
        val thread = Thread({
            try {
                work { f -> if (f - last >= 0.005 || f >= 1.0) { last = f; task = Task(title, f.coerceIn(0.0, 1.0)) } }
            } catch (e: Throwable) {
                fail("$title failed: ${e.message ?: e::class.simpleName}")
            } finally {
                task = null
            }
        }, "foxhg-task")
        thread.isDaemon = true
        worker = thread
        thread.start()
    }

    /** Waits for the running task (tests, and closing the window). */
    fun awaitTask(timeoutMillis: Long = 120_000) { worker?.join(timeoutMillis) }

    /** Keeps [new] and writes it to the settings file; a file that cannot be written is reported but the settings still apply this session. */
    fun updateSettings(new: AppSettings) {
        settings = new
        if (!settingsStore.save(new)) fail("Could not save the settings file")
    }

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
    var autosave: Boolean by mutableStateOf(settings.autosave)

    /** Bumped whenever an editor writes to the project, so views of [RomProject.isDirty] refresh. */
    var revision: Int by mutableIntStateOf(0)
        private set
    private var savedRevision: Int by mutableIntStateOf(0)

    /** Bumped by [undo] and [redo]: the editors rebuild their state from the project when it changes. */
    var undoEpoch: Int by mutableIntStateOf(0)
        private set

    /** The record each editor had selected, so rebuilding an editor (undo, switching screens) keeps the place. */
    val selectionMemory = HashMap<EditorKind, Int>()

    /** Opens [kind] on the record [index] (the same number [selectionMemory] keeps for it), for links between editors. */
    fun openAt(kind: EditorKind, index: Int) {
        selectionMemory[kind] = index
        selected = kind
    }

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

    /** Writes the edits and the undo history of the open project when either is not on disk yet. */
    private fun flushProject() {
        if (projectDir != null && (hasUnsavedWork || project?.historyDirty == true)) saveProject()
    }

    private fun attach(newProject: RomProject, newSource: FileRomSource, rom: Path, projectDir: Path?) {
        flushProject()
        cancelPendingSave()
        source?.close()
        source = newSource
        project = newProject
        romPath = rom
        header = newProject.rom.header
        this.projectDir = projectDir
        error = null
        selected = null
        selectionMemory.clear()
        revision++
        savedRevision = revision
    }

    // --- undo ------------------------------------------------------------------------------------------------------

    val canUndo: Boolean get() = revision >= 0 && project?.canUndo == true
    val canRedo: Boolean get() = revision >= 0 && project?.canRedo == true

    /** Name of the next undo step for the menu, e.g. "Undo a/0/1/7". */
    val undoLabel: String get() = if (revision >= 0) project?.undoTargets?.let(::describe)?.let { "Undo $it" } ?: "Undo" else "Undo"
    val redoLabel: String get() = if (revision >= 0) project?.redoTargets?.let(::describe)?.let { "Redo $it" } ?: "Redo" else "Redo"

    private fun describe(targets: List<EditTarget>): String? = when {
        targets.isEmpty() -> null
        targets.size == 1 -> targets[0].label
        else -> "${targets.size} files"
    }

    fun undo() = step("Undid", project?.undo())

    fun redo() = step("Redid", project?.redo())

    private fun step(verb: String, targets: List<EditTarget>?) {
        if (targets == null) return
        undoEpoch++
        projectChanged()
        error = null
        status = "$verb change to ${describe(targets)}"
    }

    // --- saving ----------------------------------------------------------------------------------------------------

    /** Called by editors after they changed the project. */
    fun projectChanged() {
        revision++
        if (autosave && projectDir != null) {
            cancelPendingSave()
            pendingSave = scheduler(autosaveDelayMillis) { pendingSave = null; saveProject(withHistory = false) }
        }
    }

    /**
     * Writes the open project's edits into its folder. The undo history can be large, so autosave leaves it out
     * ([withHistory] false) and it is written by explicit saves, when the project closes or another one opens.
     */
    fun saveProject(withHistory: Boolean = true): Boolean {
        val dir = projectDir ?: return false
        val p = project ?: return false
        cancelPendingSave()
        return when (val r = ProjectStore.save(dir, p, withHistory)) {
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

    /** Where Run writes the ROM it starts: a file in the temporary folder, named after the project or ROM. */
    internal fun runTarget(): Path {
        val name = (projectName ?: romPath?.fileName?.toString()?.removeSuffix(".nds") ?: "rom").filter { it.isLetterOrDigit() || it in "-_." }
        return Path.of(System.getProperty("java.io.tmpdir"), "foxhg-run", "$name.nds")
    }

    /** Builds the ROM and starts it in the emulator set in the settings; opens the settings first when none is set. */
    fun run() {
        if (project == null) return
        val command = settings.emulatorCommand.trim()
        if (command.isEmpty()) {
            dialog = AppDialog.Settings("Set the emulator command to use Run. Use {rom} where the ROM goes.")
            return
        }
        val target = runTarget()
        try {
            Files.createDirectories(target.parent)
        } catch (e: java.io.IOException) {
            fail("Cannot write to ${target.parent}: ${e.message}")
            return
        }
        startTask("Building the ROM to run") { report ->
            build(target, keepBackup = false, report = report)
            if (error != null) return@startTask
            Emulator.launcher(Emulator.command(command, target))?.let { fail("Cannot start the emulator: $it"); return@startTask }
            status = "Started the emulator with $target"
        }
    }

    /** [build] in the background, with the progress window. */
    fun buildInBackground(target: Path) = startTask("Building ${target.fileName}") { report -> build(target, report = report) }

    /** Writes the ROM with every edit applied to [target]. The opened ROM is not modified. */
    fun build(target: Path, keepBackup: Boolean = true, report: (Double) -> Unit = {}) {
        val project = project ?: return
        if (target.toAbsolutePath() == romPath?.toAbsolutePath()) {
            fail("Choose a different file than the ROM you opened")
            return
        }
        // A file that is there already is not lost: it is kept next to the new one as <name>.bak (the one before that goes).
        val backup = target.resolveSibling(target.fileName.toString() + ".bak")
        var kept = false
        if (keepBackup && Files.exists(target)) {
            try {
                Files.move(target, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                kept = true
            } catch (e: java.io.IOException) {
                fail("Cannot keep a backup of $target: ${e.message}")
                return
            }
        }
        FileRomSink(target).use { sink ->
            when (val result = project.build(sink, report)) {
                is FoxResult.Ok -> {
                    error = null
                    status = if (kept) "Built $target (the previous file is ${backup.fileName})" else "Built $target"
                }
                is FoxResult.Err -> {
                    // A failed build must not cost the user the file that was there.
                    if (kept) runCatching { Files.move(backup, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
                    fail(result.message)
                }
            }
        }
    }

    /** Flushes a pending autosave and releases the ROM file. */
    fun close() {
        awaitTask()
        flushProject()
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
