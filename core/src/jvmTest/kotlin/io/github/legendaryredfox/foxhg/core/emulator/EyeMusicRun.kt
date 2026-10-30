package io.github.legendaryredfox.foxhg.core.emulator

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.trainer.NewTrainer
import io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable
import io.github.legendaryredfox.foxhg.core.data.trainerclass.NewTrainerClass
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.script.ScriptEntry
import io.github.legendaryredfox.foxhg.core.script.ScriptText
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/**
 * One run of the eye-contact music check: the clerk of Cherrygrove's Poke Mart runs `EncounterMusic trainer` (the command a trainer who sees the player runs) and
 * the sound of the next 10 seconds is written to `build/emulator-audio/<tag>.pcm`. The runs differ only in the trainer, so a song that plays twice gives the same
 * samples; [EyeMusicZCompareEmulatorTest] compares them. One core per test class (the DeSmuME core crashes loading a second one), so each run is its own class.
 */
object EyeMusicRun {
    val dir: Path = Path.of("build", "emulator-audio")

    fun ok(r: FoxResult<*>) = check(r is FoxResult.Ok) { (r as FoxResult.Err).message }

    fun classId(project: RomProject, name: String): Int =
        (TextArchive.load(project) as FoxResult.Ok).value.records[HgssEnglishLayout.textBanks.trainerClasses].entries.map { it.text() }.indexOf(name).also { check(it > 0) { "no class $name" } }

    fun trainerOfClass(project: RomProject, classId: Int): Int =
        (TrainerTable.load(project) as FoxResult.Ok).value.trainers.indexOfFirst { it.header.trainerClass == classId }.also { check(it > 0) { "no trainer of class $classId" } }

    /** A trainer of the class [classId], made by [NewTrainer] from a Youngster. */
    fun newTrainerOf(project: RomProject, classId: Int): Int {
        val id = (NewTrainer.add(project, "FOXHG", trainerOfClass(project, classId(project, "Youngster"))) as FoxResult.Ok).value
        val table = (TrainerTable.load(project) as FoxResult.Ok).value
        val made = table.trainers[id]
        ok(table.with(id, made.copy(header = made.header.copy(trainerClass = classId))).save(project))
        return id
    }

    /** A new class (after the ARM9 expansion), with [music] as its eye-contact music when it is not 0, and a trainer of it. */
    fun newClassTrainer(project: RomProject, music: Int): Int {
        ok(DsprePatches.arm9Expansion.apply(project))
        return newTrainerOf(project, (NewTrainerClass.add(project, "FOXCLS", classId(project, "Youngster"), 0, 5, eyeMusic = music) as FoxResult.Ok).value)
    }

    fun run(tag: String, pick: (RomProject) -> Int) {
        if (!EmulatorSupport.available() || !EmulatorSaves.available("party")) return
        val work = Files.createTempDirectory("foxhg-eye-music")
        try {
            val rom = MartInGame.build(work, "$tag.nds") { project ->
                val trainer = pick(project)
                val scripts = (ScriptEntry.load(project) as FoxResult.Ok).value
                val lines = ScriptText.toText((scripts.records[851] as ScriptEntry.Script).file).lines().toMutableList()
                val first = lines.indexOfFirst { it.trim() == "FacePlayer" }
                check(first > 0) { "no FacePlayer in the clerk's script" }
                lines.add(first + 1, "        EncounterMusic $trainer")
                val edited = (ScriptText.parse(lines.joinToString("\n")) as? FoxResult.Ok)?.value ?: error("the edited clerk script does not parse")
                ok(scripts.with(851, ScriptEntry.Script(edited)).save(project))
            }
            EmulatorSaves.install("party", rom, work.resolve("saves"))
            EmulatorDriver(rom, work.resolve("saves")).use { emu ->
                emu.run("s1500,START,s200,A*3,s400,A,s200")
                emu.startRecording()
                emu.run("A*2,s600")
                val samples = emu.stopRecording()
                check(samples.any { it.toInt() != 0 }) { "the run is silent" }
                val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> samples.forEach { b.putShort(it) } }.array()
                Files.createDirectories(dir)
                Files.write(dir.resolve("$tag.pcm"), bytes)
                println("EYEMUSIC $tag: ${samples.size} samples")
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }
}
