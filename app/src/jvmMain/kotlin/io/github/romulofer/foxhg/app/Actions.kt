package io.github.romulofer.foxhg.app

import io.github.romulofer.foxhg.core.project.ProjectStore

/** What the menu, the buttons and the keyboard shortcuts do. One place, so they cannot drift apart. */
object Actions {
    fun newProject(model: AppModel) = model.guard { model.dialog = AppDialog.NewProject }

    fun openProject(model: AppModel) = model.guard {
        val start = model.recent.firstOrNull { it.kind == RecentKind.PROJECT }?.path?.parent ?: AppModel.defaultProjectsDir().takeIf { it.toFile().exists() }
        chooseDirectory("Open project folder", start)?.let { dir ->
            if (ProjectStore.isProject(dir)) model.openProject(dir) else model.error = "$dir is not a foxhg project folder"
        }
    }

    fun openRom(model: AppModel) = model.guard { chooseRomToOpen()?.let(model::openRom) }

    fun openRecent(model: AppModel, entry: RecentEntry) = model.guard {
        when (entry.kind) {
            RecentKind.PROJECT -> model.openProject(entry.path)
            RecentKind.ROM -> model.openRom(entry.path)
        }
    }

    /** Ctrl+S: saves the project, or asks where to create one when only a ROM is open. */
    fun save(model: AppModel) {
        if (model.project == null) return
        if (model.projectDir != null) model.saveProject() else model.dialog = AppDialog.SaveProjectAs()
    }

    fun saveAs(model: AppModel) {
        if (model.project != null) model.dialog = AppDialog.SaveProjectAs()
    }

    fun build(model: AppModel) {
        if (model.project != null) chooseBuildTarget()?.let(model::build)
    }

    fun quit(model: AppModel, exit: () -> Unit) = model.guard {
        model.close()
        exit()
    }
}
