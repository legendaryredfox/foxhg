package io.github.legendaryredfox.foxhg.core.data.trainer

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.csv.Csv
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.text.MessageText
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import io.github.legendaryredfox.foxhg.core.text.TextEntry
import io.github.legendaryredfox.foxhg.core.u16

/** When a battle message shows (the values of the table; 0 to 20 are the ones the game uses, see [TRIGGERS]). */
object MessageTrigger {
    val TRIGGERS = listOf(
        "Pre-battle (overworld)", "Player wins", "Post-battle (overworld)",
        "Double, trainer 1: pre-battle", "Double, trainer 1: player wins", "Double, trainer 1: post-battle", "Double, trainer 1: only one Pokemon",
        "Double, trainer 2: pre-battle", "Double, trainer 2: player wins", "Double, trainer 2: post-battle", "Double, trainer 2: only one Pokemon",
        "Not used 0B", "Not used 0C", "Not used 0D", "Not used 0E",
        "Opponent's last Pokemon", "Opponent's last Pokemon, critical HP", "Rematch",
        "Double, trainer 1: rematch", "Double, trainer 2: rematch", "Player loses",
    )

    fun name(trigger: Int): String = TRIGGERS.getOrElse(trigger) { "Trigger $trigger" }
}

/** One battle message: whose it is, when it shows, and its text. */
data class TrainerMessage(val trainer: Int, val trigger: Int, val text: String)

/**
 * The battle messages of the trainers: `a/0/5/7` lists one `u16 trainer, u16 trigger` pair for each message, in the order of the
 * messages of bank 728; `a/1/3/1` has one `u16` for each trainer, the byte offset of the trainer's first pair (0 for a trainer
 * with none). The game reads a trainer's pairs from that offset while the pair's trainer is the same, so a trainer's messages
 * must sit next to each other. Layout from VSMaker2 and DSPRE, confirmed on the real ROM by `RealRomTrainerMessagesTest`.
 */
class TrainerMessages(
    val trainers: List<Int>,
    val triggers: List<Int>,
    val offsets: List<Int>,
    val texts: TextArchive,
) {
    val size: Int get() = trainers.size

    /** The message numbers (rows of the table) of [trainer], as the game finds them. */
    fun messagesOf(trainer: Int): List<Int> {
        val first = (offsets.getOrNull(trainer) ?: return emptyList()) / 4
        var end = first
        while (end < size && trainers[end] == trainer) end++
        return (first until end).toList()
    }

    fun message(index: Int): TrainerMessage = TrainerMessage(trainers[index], triggers[index], texts.entries[index].text())

    fun all(): List<TrainerMessage> = List(size) { message(it) }

    /** A copy with message [index] showing at [trigger]. */
    fun withTrigger(index: Int, trigger: Int): TrainerMessages =
        TrainerMessages(trainers, triggers.toMutableList().also { it[index] = trigger }, offsets, texts)

    fun withText(index: Int, text: String): FoxResult<TrainerMessages> =
        when (val t = texts.withText(index, text)) {
            is FoxResult.Ok -> FoxResult.Ok(TrainerMessages(trainers, triggers, offsets, t.value))
            is FoxResult.Err -> t
        }

    /**
     * Replaces every message with [messages]: those of one trainer are put next to each other (in the order of the trainer's first
     * message, then the given order) and the offsets are made again. A trainer number the offsets do not cover is refused. Untouched
     * messages keep their packing; a message with a new text is stored plain.
     */
    fun replaceAll(messages: List<TrainerMessage>): FoxResult<TrainerMessages> {
        for ((i, m) in messages.withIndex()) {
            if (m.trainer !in offsets.indices) return FoxResult.Err("Message $i: trainer ${m.trainer} is outside 0..${offsets.lastIndex}")
            if (m.trigger !in 0..0xFFFF) return FoxResult.Err("Message $i: trigger ${m.trigger} is outside 0..65535")
        }
        val grouped = messages.groupBy { it.trainer }.values.flatten()
        val newOffsets = IntArray(offsets.size)
        var at = 0
        for ((i, m) in grouped.withIndex()) {
            if (i == 0 || grouped[i - 1].trainer != m.trainer) {
                newOffsets[m.trainer] = at * 4
            }
            at++
        }
        // keep a message's packing and codes when its trainer, trigger and text did not change
        val current = HashMap<TrainerMessage, TextEntry>(size * 2)
        for (i in 0 until size) current.putIfAbsent(message(i), texts.entries[i])
        val entries = ArrayList<TextEntry>(grouped.size)
        for (m in grouped) {
            entries += current[m] ?: run {
                val chars = when (val r = MessageText.encode(m.text)) {
                    is FoxResult.Ok -> r.value
                    is FoxResult.Err -> return FoxResult.Err("Message of trainer ${m.trainer}: ${r.message}")
                }
                TextEntry(chars)
            }
        }
        return FoxResult.Ok(TrainerMessages(grouped.map { it.trainer }, grouped.map { it.trigger }, newOffsets.toList(), TextArchive(texts.key, entries)))
    }

    fun tableBytes(): ByteArray = ByteArray(size * 4).also { b ->
        for (i in 0 until size) { b.putU16(i * 4, trainers[i]); b.putU16(i * 4 + 2, triggers[i]) }
    }

    fun offsetBytes(): ByteArray = ByteArray(offsets.size * 2).also { b -> offsets.forEachIndexed { i, o -> b.putU16(i * 2, o) } }

    /** Writes the table, the offsets and the messages as one step; an error leaves the project as it was. */
    fun save(project: RomProject): FoxResult<Unit> = project.atomically {
        fun narcOf(path: String, bytes: ByteArray): FoxResult<ByteArray> {
            val old = project.read(path) ?: return FoxResult.Err("The ROM has no $path")
            val narc = when (val n = Narc.parse(old)) {
                is FoxResult.Ok -> n.value
                is FoxResult.Err -> return FoxResult.Err("$path: ${n.message}")
            }
            return FoxResult.Ok(Narc(listOf(bytes), narc.nameTable).toBytes())
        }
        for ((path, bytes) in listOf(project.layout.trainerMessageTable to tableBytes(), project.layout.trainerMessageOffsets to offsetBytes())) {
            val narc = when (val r = narcOf(path, bytes)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return@atomically r }
            when (val w = project.write(path, narc)) { is FoxResult.Err -> return@atomically w; is FoxResult.Ok -> Unit }
        }
        val banks = when (val b = TextArchive.load(project)) { is FoxResult.Ok -> b.value; is FoxResult.Err -> return@atomically b }
        banks.with(TextBanks.TRAINER_MESSAGES, texts).save(project)
    }

    /** `id,trainer,trigger,trigger_name,text`; the trigger name and the id are for reading, import goes by the row order. */
    fun exportCsv(trainerName: (Int) -> String): String =
        Csv.format(
            listOf(listOf("id", "trainer", "trainer_name", "trigger", "trigger_name", "text")) +
                all().mapIndexed { i, m -> listOf(i.toString(), m.trainer.toString(), trainerName(m.trainer), m.trigger.toString(), MessageTrigger.name(m.trigger), m.text) },
        )

    /**
     * Reads a file made by [exportCsv] (the `trainer_name` and `trigger_name` columns are ignored) and replaces every message with its
     * rows, so rows can be added, removed and moved to another trainer. Any bad number rejects the whole file.
     */
    fun importCsv(text: String): FoxResult<TrainerMessages> {
        val rows = when (val parsed = Csv.parse(text)) {
            is FoxResult.Ok -> parsed.value
            is FoxResult.Err -> return parsed
        }
        val header = rows.firstOrNull() ?: return FoxResult.Err("The CSV file is empty")
        val trainerCol = header.indexOf("trainer").also { if (it < 0) return FoxResult.Err("The CSV header has no 'trainer' column") }
        val triggerCol = header.indexOf("trigger").also { if (it < 0) return FoxResult.Err("The CSV header has no 'trigger' column") }
        val textCol = header.indexOf("text").also { if (it < 0) return FoxResult.Err("The CSV header has no 'text' column") }
        val messages = ArrayList<TrainerMessage>(rows.size - 1)
        for ((n, row) in rows.drop(1).withIndex()) {
            val line = n + 2
            if (row.size != header.size) return FoxResult.Err("Line $line has ${row.size} fields, expected ${header.size}")
            val trainer = row[trainerCol].trim().toIntOrNull() ?: return FoxResult.Err("Line $line: bad trainer '${row[trainerCol]}'")
            val trigger = row[triggerCol].trim().toIntOrNull() ?: return FoxResult.Err("Line $line: bad trigger '${row[triggerCol]}'")
            messages += TrainerMessage(trainer, trigger, row[textCol])
        }
        return replaceAll(messages)
    }

    companion object {
        fun parse(table: ByteArray, offsets: ByteArray, texts: TextArchive): FoxResult<TrainerMessages> {
            if (table.size % 4 != 0) return FoxResult.Err("The trainer message table is ${table.size} bytes, not a multiple of 4")
            if (offsets.size % 2 != 0) return FoxResult.Err("The trainer message offsets are ${offsets.size} bytes, not a multiple of 2")
            val n = table.size / 4
            if (texts.entries.size != n) return FoxResult.Err("The table has $n messages but bank ${TextBanks.TRAINER_MESSAGES} has ${texts.entries.size}")
            return FoxResult.Ok(
                TrainerMessages(
                    List(n) { table.u16(it * 4) }, List(n) { table.u16(it * 4 + 2) },
                    List(offsets.size / 2) { offsets.u16(it * 2) }, texts,
                ),
            )
        }

        fun load(project: RomProject): FoxResult<TrainerMessages> {
            fun file0(path: String): FoxResult<ByteArray> {
                val bytes = project.read(path) ?: return FoxResult.Err("The ROM has no $path")
                return when (val n = Narc.parse(bytes)) {
                    is FoxResult.Ok -> n.value.files.firstOrNull()?.let { FoxResult.Ok(it) } ?: FoxResult.Err("$path has no files")
                    is FoxResult.Err -> FoxResult.Err("$path: ${n.message}")
                }
            }
            val table = when (val r = file0(project.layout.trainerMessageTable)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return r }
            val offsets = when (val r = file0(project.layout.trainerMessageOffsets)) { is FoxResult.Ok -> r.value; is FoxResult.Err -> return r }
            val banks = when (val b = TextArchive.load(project)) { is FoxResult.Ok -> b.value; is FoxResult.Err -> return b }
            val texts = banks.records.getOrNull(TextBanks.TRAINER_MESSAGES) ?: return FoxResult.Err("The ROM has no message bank ${TextBanks.TRAINER_MESSAGES}")
            return parse(table, offsets, texts)
        }
    }
}
