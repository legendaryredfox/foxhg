package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** How to read and write the records of one NARC. */
class RecordSpec<T>(val path: String, val parse: (ByteArray) -> FoxResult<T>, val serialize: (T) -> ByteArray)

/**
 * The records of one NARC (one file per record) parsed into [T]. Writing it back keeps the archive's name table, and
 * a table that equals the ROM's leaves the project clean.
 */
class NarcRecords<T>(
    val path: String,
    val records: List<T>,
    private val nameTable: ByteArray,
    private val serialize: (T) -> ByteArray,
) {
    fun with(index: Int, record: T): NarcRecords<T> =
        NarcRecords(path, records.toMutableList().also { it[index] = record }, nameTable, serialize)

    fun withAll(newRecords: List<T>): NarcRecords<T> = NarcRecords(path, newRecords, nameTable, serialize)

    fun toNarc(): Narc = Narc(records.map(serialize), nameTable)

    fun save(project: RomProject): FoxResult<Unit> = project.write(path, toNarc().toBytes())

    companion object {
        fun <T> load(project: RomProject, spec: RecordSpec<T>): FoxResult<NarcRecords<T>> =
            load(project, spec.path, spec.parse, spec.serialize)

        /** The records as the ROM originally has them, ignoring the project's edits. */
        fun <T> original(project: RomProject, spec: RecordSpec<T>): List<T> {
            val bytes = project.rom.read(spec.path) ?: return emptyList()
            val narc = (Narc.parse(bytes) as? FoxResult.Ok)?.value ?: return emptyList()
            return narc.files.mapNotNull { (spec.parse(it) as? FoxResult.Ok)?.value }
        }

        fun <T> load(
            project: RomProject,
            path: String,
            parse: (ByteArray) -> FoxResult<T>,
            serialize: (T) -> ByteArray,
        ): FoxResult<NarcRecords<T>> {
            val bytes = project.read(path) ?: return FoxResult.Err("The ROM has no $path")
            val narc = when (val n = Narc.parse(bytes)) {
                is FoxResult.Ok -> n.value
                is FoxResult.Err -> return FoxResult.Err("$path: ${n.message}")
            }
            val records = ArrayList<T>(narc.files.size)
            for ((i, file) in narc.files.withIndex()) {
                when (val r = parse(file)) {
                    is FoxResult.Ok -> records += r.value
                    is FoxResult.Err -> return FoxResult.Err("$path record $i: ${r.message}")
                }
            }
            return FoxResult.Ok(NarcRecords(path, records, narc.nameTable, serialize))
        }
    }
}
