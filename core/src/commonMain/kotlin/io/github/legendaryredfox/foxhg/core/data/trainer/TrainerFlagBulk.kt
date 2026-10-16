package io.github.legendaryredfox.foxhg.core.data.trainer

/**
 * Edits of the battle flags of many trainers at once. A flag is a number: 0 to [AI_COUNT] - 1 is a bit of the AI flags
 * of the trainer header, [DOUBLE_BATTLE] is the double battle field. Names and order from DSPRE's `TrainerFlagBulkEditor`.
 */
object TrainerFlagBulk {
    const val AI_COUNT = 11
    const val DOUBLE_BATTLE = AI_COUNT
    const val FLAGS = AI_COUNT + 1

    val NAMES = listOf(
        "AI: Basic", "AI: Evaluate Attack", "AI: Expert", "AI: Setup", "AI: Risky", "AI: Prioritize Extremes",
        "AI: Baton Pass", "AI: Tag Strategy", "AI: Check HP", "AI: Weather", "AI: Harassment", "Double Battle",
    )

    fun has(trainer: Trainer, flag: Int): Boolean =
        if (flag == DOUBLE_BATTLE) trainer.header.doubleBattle != 0L else (trainer.header.aiFlags ushr flag) and 1L == 1L

    fun withFlag(trainer: Trainer, flag: Int, on: Boolean): Trainer {
        if (has(trainer, flag) == on) return trainer
        val header = trainer.header
        // The game stores 2 for a double battle and 0 for a single one.
        val changed = if (flag == DOUBLE_BATTLE) header.copy(doubleBattle = if (on) 2L else 0L)
        else header.copy(aiFlags = if (on) header.aiFlags or (1L shl flag) else header.aiFlags and (1L shl flag).inv())
        return trainer.copy(header = changed)
    }

    /** Trainers (numbers) that have [flag]. */
    fun withFlagSet(table: TrainerTable, flag: Int): List<Int> = table.trainers.indices.filter { has(table.trainers[it], flag) }

    /** [table] with [flag] set to [on] for every trainer in [trainers]; the rest stay. */
    fun set(table: TrainerTable, flag: Int, trainers: Collection<Int>, on: Boolean): TrainerTable {
        var result = table
        for (t in trainers.toSet()) {
            val trainer = table.trainers.getOrNull(t) ?: continue
            val changed = withFlag(trainer, flag, on)
            if (changed !== trainer) result = result.with(t, changed)
        }
        return result
    }

    /** Trainers grouped by trainer class, in class order: class id to the trainer numbers of it. */
    fun byClass(table: TrainerTable): Map<Int, List<Int>> =
        table.trainers.indices.groupBy { table.trainers[it].header.trainerClass }.toSortedMap()
}
