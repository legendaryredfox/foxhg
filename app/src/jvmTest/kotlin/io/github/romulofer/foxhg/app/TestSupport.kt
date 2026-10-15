package io.github.romulofer.foxhg.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertNull

/** Collects autosave callbacks so a test decides when "700 ms later" happens. */
class ManualScheduler {
    private val pending = ArrayList<() -> Unit>()

    val scheduler: Scheduler = { _, action ->
        pending += action
        AutoCloseable { pending.remove(action) }
    }

    val hasPending: Boolean get() = pending.isNotEmpty()

    fun runPending() {
        val copy = pending.toList()
        pending.clear()
        copy.forEach { it() }
    }
}

/** A model whose recents file lives in a temp dir and whose autosave is driven by [manual]. */
fun testModel(manual: ManualScheduler = ManualScheduler()): AppModel {
    val recents = Recents(tempDir("foxhg-recents").resolve("recent.txt"))
    return AppModel(recents, manual.scheduler, settingsStore = testSettings())
}

/** A settings file in a temp dir, with no emulator detected, so tests never touch the real settings. */
fun testSettings(): SettingsStore = SettingsStore(tempDir("foxhg-settings").resolve("settings.properties"), detectEmulator = { "" })

val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

/**
 * True when the test ROM is there. The ROM is never committed, so without it (a fresh clone, CI) the ROM-based tests
 * skip with a notice; set FOXHG_REQUIRE_ROM=1 to make a missing ROM fail instead, e.g. to catch a broken symlink.
 */
fun romAvailable(): Boolean {
    if (Files.exists(romPath)) return true
    val message = "no ROM at ${romPath.toAbsolutePath()}"
    if (System.getenv("FOXHG_REQUIRE_ROM") != null) throw AssertionError("$message (FOXHG_REQUIRE_ROM is set)")
    println("SKIPPED: $message")
    return false
}

/** Shows the app for [model] in a window of the given [height] and runs [block]. */
@OptIn(ExperimentalTestApi::class)
fun runApp(model: AppModel, height: Int = 900, block: DesktopComposeUiTest.() -> Unit) {
    runDesktopComposeUiTest(width = 1280, height = height) {
        setContent { App(model) }
        waitForIdle()
        block()
    }
}

/** Opens the ROM in a fresh model, shows the app at a normal window size and runs [block]; skips without the ROM. */
@OptIn(ExperimentalTestApi::class)
fun withHeartGoldApp(height: Int = 900, block: DesktopComposeUiTest.(AppModel, RomProject) -> Unit) {
    if (!romAvailable()) return
    val model = testModel()
    model.openRom(romPath)
    val project = model.project!!
    try {
        runApp(model, height) { block(model, project) }
    } finally {
        model.close()
    }
}

/** Runs [block] with the file and folder choosers answering from [file] and [directory], then puts the real ones back. */
fun <T> withChoosers(
    file: (title: String, mode: Int, extension: String) -> Path? = { _, _, _ -> error("no file chooser expected") },
    directory: (title: String, start: Path?) -> Path? = { _, _ -> error("no folder chooser expected") },
    block: () -> T,
): T {
    val (oldFile, oldDirectory) = Choosers.file to Choosers.directory
    Choosers.file = file
    Choosers.directory = { title, start, onPick -> directory(title, start)?.let(onPick) }
    try {
        return block()
    } finally {
        Choosers.file = oldFile
        Choosers.directory = oldDirectory
    }
}

@OptIn(ExperimentalTestApi::class)
fun DesktopComposeUiTest.open(title: String) {
    onNodeWithTag("nav:$title").performScrollTo().performClick() // the sidebar scrolls when the window is short
    waitForIdle()
    waitLoaded()
}

@OptIn(ExperimentalTestApi::class)
fun DesktopComposeUiTest.clickFirstText(text: String, substring: Boolean = false) {
    onAllNodesWithText(text, substring = substring).onFirst().performClick()
    waitForIdle()
}

fun SemanticsNodeInteraction.editableText(): String =
    fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.text ?: ""

/** Builds the model's project into a temporary ROM, reopens that and runs [check] on it. */
fun buildAndReopen(model: AppModel, check: (RomProject) -> Unit) {
    val built = tempFile("foxhg-ui-e2e", ".nds")
    try {
        model.build(built)
        assertNull(model.error, model.error)
        FileRomSource(built).use { source -> check((RomProject.open(source) as FoxResult.Ok).value) }
    } finally {
        Files.deleteIfExists(built)
    }
}

/** Types into a number field. [inScrollPane] is false for fields above the list, outside the scrolling detail pane. */
@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.typeNumber(value: Int, inScrollPane: Boolean = true) {
    if (inScrollPane) performScrollTo()
    performTextReplacement(value.toString())
}

/** Presses a button or chip through its semantics action, which also works below the fold. */
@OptIn(ExperimentalTestApi::class)
fun DesktopComposeUiTest.tap(node: SemanticsNodeInteraction, inScrollPane: Boolean = true) {
    (if (inScrollPane) node.performScrollTo() else node).performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
}

/** Opens the [index]th picker called [label] and chooses [option] in its popup (the popup is the last search box). */
@OptIn(ExperimentalTestApi::class)
fun DesktopComposeUiTest.pick(label: String, option: String, index: Int = 0) {
    tap(onAllNodesWithTag("picker:$label")[index])
    onAllNodesWithTag("search:Search…").onLast().performTextInput(option)
    waitForIdle()
    onAllNodesWithText(option).onLast().performClick()
    waitForIdle()
}

/** Waits until no loading screen is up: editors are built in the background (when opened, and again after an undo). */
@OptIn(ExperimentalTestApi::class)
fun DesktopComposeUiTest.waitLoaded() {
    waitUntil(timeoutMillis = 120_000) { onAllNodesWithTag("loading").fetchSemanticsNodes().isEmpty() }
    waitForIdle()
}

/**
 * Temporary files and folders of the tests live under one folder that is removed when the test JVM ends (and
 * `foxhg-run`, where Run puts its ROM, with it), so a run of the suite leaves nothing in the temp folder.
 */
private val tempRoot: java.nio.file.Path = Files.createTempDirectory("foxhg-test").also { root ->
    Runtime.getRuntime().addShutdownHook(Thread {
        root.toFile().deleteRecursively()
        java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "foxhg-run").toFile().deleteRecursively()
    })
}

fun tempDir(prefix: String): java.nio.file.Path = Files.createTempDirectory(tempRoot, prefix)

fun tempFile(prefix: String, suffix: String): java.nio.file.Path = Files.createTempFile(tempRoot, prefix, suffix)
