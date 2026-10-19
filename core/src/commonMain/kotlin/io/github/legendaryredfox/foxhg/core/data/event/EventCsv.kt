package io.github.legendaryredfox.foxhg.core.data.event

/**
 * A read-only CSV dump of the events of every map in one table for each kind (objects, warps, triggers, signs), for
 * spreadsheets and diffs. The `group` column is the event file, `group_name` the maps that load it. To change events
 * in bulk, use the per-map import of the Events editor.
 */
object EventCsv {
    fun export(files: List<EventFile>, kind: EventKind, fileName: (Int) -> String): String = when (kind) {
        EventKind.OVERWORLD -> OverworldEvent.CSV.exportAll(files.map { it.overworlds }, fileName)
        EventKind.WARP -> Warp.CSV.exportAll(files.map { it.warps }, fileName)
        EventKind.TRIGGER -> Trigger.CSV.exportAll(files.map { it.triggers }, fileName)
        EventKind.SIGN -> Spawnable.CSV.exportAll(files.map { it.spawnables }, fileName)
    }
}
