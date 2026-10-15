package io.github.romulofer.foxhg.core.data.trainer

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.narc.Narc
import io.github.romulofer.foxhg.core.project.RomProject

/** Every trainer: header NARC (`a/0/5/5`) and party NARC (`a/0/5/6`) read together, written back together. */
class TrainerTable(val trainers: List<Trainer>, private val headerNames: ByteArray, private val partyNames: ByteArray) {

    fun with(index: Int, trainer: Trainer): TrainerTable =
        TrainerTable(trainers.toMutableList().also { it[index] = trainer }, headerNames, partyNames)

    fun withAll(newTrainers: List<Trainer>): TrainerTable = TrainerTable(newTrainers, headerNames, partyNames)

    fun save(project: RomProject): FoxResult<Unit> {
        val headers = Narc(trainers.map { it.header.toBytes() }, headerNames).toBytes()
        val parties = Narc(trainers.map { it.partyBytes() }, partyNames).toBytes()
        (project.write(HgssLayout.TRAINER_DATA, headers) as? FoxResult.Err)?.let { return it }
        return project.write(HgssLayout.TRAINER_PARTIES, parties)
    }

    companion object {
        fun load(project: RomProject): FoxResult<TrainerTable> = read(project) { path -> project.read(path) }

        /** The table as the ROM originally has it, ignoring the project's edits. */
        fun original(project: RomProject): List<Trainer> =
            (read(project) { path -> project.rom.read(path) } as? FoxResult.Ok)?.value?.trainers ?: emptyList()

        private fun read(project: RomProject, bytesOf: (String) -> ByteArray?): FoxResult<TrainerTable> {
            val headers = narc(HgssLayout.TRAINER_DATA, bytesOf) ?: return FoxResult.Err("The ROM has no ${HgssLayout.TRAINER_DATA}")
            val parties = narc(HgssLayout.TRAINER_PARTIES, bytesOf) ?: return FoxResult.Err("The ROM has no ${HgssLayout.TRAINER_PARTIES}")
            if (headers.files.size != parties.files.size) {
                return FoxResult.Err("${headers.files.size} trainer headers but ${parties.files.size} parties")
            }
            val trainers = ArrayList<Trainer>(headers.files.size)
            for (i in headers.files.indices) {
                when (val t = Trainer.parse(headers.files[i], parties.files[i])) {
                    is FoxResult.Ok -> trainers += t.value
                    is FoxResult.Err -> return FoxResult.Err("Trainer $i: ${t.message}")
                }
            }
            return FoxResult.Ok(TrainerTable(trainers, headers.nameTable, parties.nameTable))
        }

        private fun narc(path: String, bytesOf: (String) -> ByteArray?): Narc? =
            bytesOf(path)?.let { (Narc.parse(it) as? FoxResult.Ok)?.value }
    }
}
