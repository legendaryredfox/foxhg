package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.text.TextArchive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomNewTrainerClassTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    private fun withProject(block: (RomProject) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block(RomProject.open(source).ok()) }
    }

    private fun names(project: RomProject) = TextArchive.load(project).ok().records[TextBanks.TRAINER_CLASSES]
    private fun pictures(project: RomProject) = Narc.parse(project.read(HgssEnglishLayout.trainerFrontSprites)!!).ok().files

    @Test
    fun aClassNeedsTheSyntheticOverlay() = withProject { project ->
        assertTrue(NewTrainerClass.add(project, "FOXHG", 1, 0, 5) is FoxResult.Err)
        assertFalse(project.isDirty, "nothing was written")
    }

    @Test
    fun aNewClassGrowsTheNameThePicturesTheGenderAndThePrizeTables() = withProject { project ->
        check(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
        val genders = TrainerClassGenders.load(project).ok()
        val prizes = TrainerClassPrizes.load(project).ok()
        val count = names(project).entries.size
        val before = pictures(project)
        assertEquals(129, count)
        assertEquals(128, genders.genders.size)
        assertEquals(129, prizes.entries.size)

        val id = NewTrainerClass.add(project, "FOXHG", 1, 1, 77).ok()
        assertEquals(count, id)
        assertEquals("FOXHG", names(project).entries.last().text())
        assertEquals(count + 1, names(project).entries.size)

        val g = TrainerClassGenders.load(project).ok().genders
        assertEquals(count + 1, g.size)
        assertEquals(genders.genders, g.take(128), "the vanilla 128 did not change")
        assertEquals(1, g[id], "the gender of the new class")
        val p = TrainerClassPrizes.load(project).ok()
        assertEquals(prizes.entries, p.entries.take(count), "the vanilla rows did not change")
        assertEquals(PrizeEntry(id, 77), p.entries.last())
        assertEquals(77, p.multiplierOf(id))

        val after = pictures(project)
        assertEquals(before.size + HgssEnglishLayout.filesPerTrainerClassPicture, after.size)
        for (i in 0 until HgssEnglishLayout.filesPerTrainerClassPicture) {
            assertContentEquals(before[5 + i], after[count * 5 + i], "picture file $i is a copy of class 1")
            assertContentEquals(before[i], after[i])
        }

        // the tables stay editable where they are, and a second class follows the first
        TrainerClassGenders.save(project, TrainerClassGenders.load(project).ok().with(id, 2)).ok()
        assertEquals(2, TrainerClassGenders.load(project).ok().genders[id])
        TrainerClassPrizes.save(project, TrainerClassPrizes.load(project).ok().withEntry(count, 9)).ok()
        assertEquals(9, TrainerClassPrizes.load(project).ok().multiplierOf(id))
        assertEquals(count + 1, NewTrainerClass.add(project, "SECOND", id, 0, 3).ok())
        assertEquals(listOf(2, 0), TrainerClassGenders.load(project).ok().genders.takeLast(2))
        assertEquals(9, TrainerClassPrizes.load(project).ok().multiplierOf(id))
        assertEquals(3, TrainerClassPrizes.load(project).ok().multiplierOf(count + 1))
    }

    @Test
    fun aBadRequestChangesNothing() = withProject { project ->
        check(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
        val count = names(project).entries.size
        assertTrue(NewTrainerClass.add(project, "FOXHG", count, 0, 1) is FoxResult.Err, "no such class to copy")
        assertTrue(NewTrainerClass.add(project, "FOXHG", 1, 3, 1) is FoxResult.Err, "no such gender")
        assertTrue(NewTrainerClass.add(project, "A name with {BAD CODE", 1, 0, 1) is FoxResult.Err)
        assertEquals(count, names(project).entries.size)
        assertEquals(128, TrainerClassGenders.load(project).ok().genders.size)
    }

    @Test
    fun aClassWithEyeContactMusicGetsARowAndTheTableMovesOnce() = withProject { project ->
        check(DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
        val vanilla = TrainerEncounterMusic.load(project).ok()
        assertEquals(HgssEnglishLayout.encounterMusicVanillaRows, vanilla.rows.size)
        assertEquals(EncounterMusicRow(2, 1108, 1108), vanilla.rows[0], "the Youngster row")
        val count = names(project).entries.size

        assertEquals(count, NewTrainerClass.add(project, "NOMUSIC", 1, 0, 5).ok())
        assertEquals(vanilla, TrainerEncounterMusic.load(project).ok(), "a class added without music leaves the table where it is")
        assertEquals(count + 1, NewTrainerClass.add(project, "WITHMUSIC", 1, 0, 5, eyeMusic = 1113).ok())
        val moved = TrainerEncounterMusic.load(project).ok()
        assertEquals(vanilla.rows + EncounterMusicRow(count + 1, 1113, 1113), moved.rows)
        assertEquals(1113, moved.of(count + 1)?.first)
        assertEquals(null, moved.of(count), "the class with no music has no row")
        assertEquals(count + 2, NewTrainerClass.add(project, "MORE", 1, 0, 5, eyeMusic = 1112).ok())
        assertEquals(vanilla.rows + EncounterMusicRow(count + 1, 1113, 1113) + EncounterMusicRow(count + 2, 1112, 1112), TrainerEncounterMusic.load(project).ok().rows)
        assertEquals(count + 3, TrainerClassGenders.load(project).ok().genders.size, "the other tables kept growing")
    }
}
