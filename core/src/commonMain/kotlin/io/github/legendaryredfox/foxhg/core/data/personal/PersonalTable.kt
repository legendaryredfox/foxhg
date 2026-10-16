package io.github.legendaryredfox.foxhg.core.data.personal

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** Every personal-data record of the ROM, in species order (index = record number). */
class PersonalTable(val records: List<PersonalData>, private val nameTable: ByteArray) {

    fun with(index: Int, record: PersonalData): PersonalTable =
        PersonalTable(records.toMutableList().also { it[index] = record }, nameTable)

    fun toNarc(): Narc = Narc(records.map { it.toBytes() }, nameTable)

    /** Stores the table as the project's personal NARC; unchanged tables leave the project clean. */
    fun save(project: RomProject): FoxResult<Unit> = project.write(HgssLayout.PERSONAL, toNarc().toBytes())

    companion object {
        fun load(project: RomProject): FoxResult<PersonalTable> {
            val bytes = project.read(HgssLayout.PERSONAL) ?: return FoxResult.Err("The ROM has no ${HgssLayout.PERSONAL}")
            val narc = when (val n = Narc.parse(bytes)) {
                is FoxResult.Ok -> n.value
                is FoxResult.Err -> return FoxResult.Err("${HgssLayout.PERSONAL}: ${n.message}")
            }
            val records = ArrayList<PersonalData>(narc.files.size)
            for ((i, file) in narc.files.withIndex()) {
                when (val r = PersonalData.parse(file)) {
                    is FoxResult.Ok -> records += r.value
                    is FoxResult.Err -> return FoxResult.Err("Personal record $i: ${r.message}")
                }
            }
            return FoxResult.Ok(PersonalTable(records, narc.nameTable))
        }
    }
}
