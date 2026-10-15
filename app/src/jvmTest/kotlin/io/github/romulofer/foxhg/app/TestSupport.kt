package io.github.romulofer.foxhg.app

import java.nio.file.Files

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
    val recents = Recents(Files.createTempDirectory("foxhg-recents").resolve("recent.txt"))
    return AppModel(recents, manual.scheduler)
}
