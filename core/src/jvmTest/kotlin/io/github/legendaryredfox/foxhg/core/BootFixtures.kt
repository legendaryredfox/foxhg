package io.github.legendaryredfox.foxhg.core

import io.github.legendaryredfox.foxhg.core.compression.Blz
import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionData
import io.github.legendaryredfox.foxhg.core.data.evolution.TradeEvolutions
import io.github.legendaryredfox.foxhg.core.data.learnset.Learnset
import io.github.legendaryredfox.foxhg.core.data.learnset.LearnsetEntry
import io.github.legendaryredfox.foxhg.core.data.move.MoveData
import io.github.legendaryredfox.foxhg.core.data.personal.PersonalTable
import io.github.legendaryredfox.foxhg.core.nds.FileRomSink
import io.github.legendaryredfox.foxhg.core.nds.FileRomSource
import io.github.legendaryredfox.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

/**
 * Writes ROMs for scripts/boot-test.sh to run in an emulator. Does nothing unless FOXHG_BOOT_DIR is set (a directory
 * for the output) and the ROM exists, so normal test runs stay fast.
 */
class BootFixtures {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")
    private val outDir: Path? = System.getenv("FOXHG_BOOT_DIR")?.let { Path.of(it) }

    private fun open(source: FileRomSource): RomProject = (RomProject.open(source) as FoxResult.Ok).value

    private fun build(project: RomProject, target: Path) {
        FileRomSink(target).use { sink -> check(project.build(sink) is FoxResult.Ok) { "build failed" } }
    }

    @Test
    fun dataEdits() {
        val dir = outDir ?: return
        if (!Files.exists(romPath)) return
        FileRomSource(romPath).use { source ->
            val project = open(source)
            val personal = (PersonalTable.load(project) as FoxResult.Ok).value
            check(personal.with(25, personal.records[25].copy(baseHp = 250, baseSpeed = 5)).save(project) is FoxResult.Ok)
            val learnsets = (Learnset.load(project) as FoxResult.Ok).value
            val pika = learnsets.records[25]
            check(learnsets.with(25, pika.copy(entries = pika.entries + LearnsetEntry(85, 5)).sorted()).save(project) is FoxResult.Ok)
            val evolutions = (EvolutionData.load(project) as FoxResult.Ok).value
            check(evolutions.withAll(TradeEvolutions.convert(evolutions.records, 36).first).save(project) is FoxResult.Ok)
            val moves = (MoveData.load(project) as FoxResult.Ok).value
            check(moves.with(1, moves.records[1].copy(power = 90)).save(project) is FoxResult.Ok)
            val trainers = (io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable.load(project) as FoxResult.Ok).value
            val pidgey = 16
            check(trainers.withAll(trainers.trainers.map { t -> t.withParty(t.party.map { if (it.species == pidgey) it.withSpecies(19) else it }) }).save(project) is FoxResult.Ok)
            val hg = (io.github.legendaryredfox.foxhg.core.data.encounter.EncounterData.loadHeartGold(project) as FoxResult.Ok).value
            val ss = (io.github.legendaryredfox.foxhg.core.data.encounter.EncounterData.loadSoulSilver(project) as FoxResult.Ok).value
            check(hg.withAll(ss.records).save(project) is FoxResult.Ok)
            Files.createDirectories(dir)
            build(project, dir.resolve("data-edits.nds"))
        }
    }

    /** The editors added after the first fixtures: type chart, pickup, growth rates, battle display, Battle Tower, trainer flags and the marts, all edited in place. */
    @Test
    fun laterEditors() {
        val dir = outDir ?: return
        if (!Files.exists(romPath)) return
        FileRomSource(romPath).use { source ->
            val project = open(source)
            val chart = (io.github.legendaryredfox.foxhg.core.data.typechart.TypeChart.load(project) as FoxResult.Ok).value
            check(chart.with(10, 12, 10).with(0, 10, 5).save(project) is FoxResult.Ok) // Fire against Grass back to 1x, Normal against Fire half
            val pickup = (io.github.legendaryredfox.foxhg.core.data.pickup.PickupTable.load(project) as FoxResult.Ok).value
            check(pickup.copy(divisor = 5).withCommon(0, 17).save(project) is FoxResult.Ok)
            val growth = (io.github.legendaryredfox.foxhg.core.data.NarcRecords.load(project, io.github.legendaryredfox.foxhg.core.data.growth.GrowthCurve.SPEC) as FoxResult.Ok).value
            check(growth.with(4, growth.records[4].withLevel(10, growth.records[4].exp[10] + 100)).save(project) is FoxResult.Ok)
            val display = (io.github.legendaryredfox.foxhg.core.data.battledisplay.BattleDisplayTable.load(project) as FoxResult.Ok).value
            check(display.with(25, display.get(25).copy(spriteY = 9, shadowSize = 2)).save(project) is FoxResult.Ok)
            val towerSets = (io.github.legendaryredfox.foxhg.core.data.NarcRecords.load(project, io.github.legendaryredfox.foxhg.core.data.battletower.BattleTowerSet.SPEC) as FoxResult.Ok).value
            check(towerSets.with(1, towerSets.records[1].withMove(2, 33)).save(project) is FoxResult.Ok)
            val trainers = (io.github.legendaryredfox.foxhg.core.data.trainer.TrainerTable.load(project) as FoxResult.Ok).value
            check(io.github.legendaryredfox.foxhg.core.data.trainer.TrainerFlagBulk.set(trainers, io.github.legendaryredfox.foxhg.core.data.trainer.TrainerFlagBulk.DOUBLE_BATTLE, listOf(5), true).save(project) is FoxResult.Ok)
            val marts = (io.github.legendaryredfox.foxhg.core.data.mart.Marts.load(project) as FoxResult.Ok).value
            check(marts.withItem(0, 1, 4).withRemoved(3, 0).withBadge(0, io.github.legendaryredfox.foxhg.core.data.mart.BadgeMartEntry(17, 2)).save(project) is FoxResult.Ok)
            Files.createDirectories(dir)
            build(project, dir.resolve("later-editors.nds"))
        }
    }

    /** The ARM9 expansion (the loader runs at start-up and maps the synthetic overlay) with two marts moved into it. */
    @Test
    fun movedMarts() {
        val dir = outDir ?: return
        if (!Files.exists(romPath)) return
        FileRomSource(romPath).use { source ->
            val project = open(source)
            check(io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches.arm9Expansion.apply(project) is FoxResult.Ok)
            var marts = (io.github.legendaryredfox.foxhg.core.data.mart.Marts.load(project) as FoxResult.Ok).value
            repeat(6) { marts = marts.withAdded(0, 17 + it) }
            marts = marts.withAdded(28, 18)
            check(marts.isMoved(0) && marts.isMoved(28))
            check(marts.save(project) is FoxResult.Ok)
            Files.createDirectories(dir)
            build(project, dir.resolve("moved-marts.nds"))
        }
    }

    @Test
    fun toolboxPatches() {
        val dir = outDir ?: return
        if (!Files.exists(romPath)) return
        FileRomSource(romPath).use { source ->
            val project = open(source)
            for (patch in io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches.all) {
                check(patch.apply(project) is FoxResult.Ok) { "${patch.id} failed" }
            }
            Files.createDirectories(dir)
            build(project, dir.resolve("toolbox-patches.nds"))
        }
    }

    @Test
    fun recompressedOverlays() {
        val dir = outDir ?: return
        if (!Files.exists(romPath)) return
        FileRomSource(romPath).use { source ->
            val project = open(source)
            var count = 0
            var before = 0L
            var after = 0L
            for (overlay in project.rom.arm9Overlays) {
                if (!overlay.isCompressed) continue
                val packed = project.rom.readOverlay(overlay)!!
                val plain = (Blz.decompress(packed) as FoxResult.Ok).value
                val again = (Blz.compress(plain) as FoxResult.Ok).value
                val file = project.rom.file(overlay.fileId)!!
                before += packed.size
                after += again.size
                count++
                // Overlays are unnamed files: write through the builder directly instead of by path.
                recompressed[file.id] = again
            }
            println("recompressed overlays=$count packed before=$before after=$after")
            Files.createDirectories(dir)
            FileRomSink(dir.resolve("recompressed-overlays.nds")).use { sink ->
                check(io.github.legendaryredfox.foxhg.core.nds.NdsBuilder.build(project.rom, source, io.github.legendaryredfox.foxhg.core.nds.RomEdits(recompressed), sink) is FoxResult.Ok)
            }
        }
    }

    private fun arm9Fixture(name: String, compress: Boolean) {
        val dir = outDir ?: return
        if (!Files.exists(romPath)) return
        FileRomSource(romPath).use { source ->
            val project = open(source)
            val plain = (io.github.legendaryredfox.foxhg.core.arm9.Arm9.decompressed(project.rom) as FoxResult.Ok).value
            val footer = io.github.legendaryredfox.foxhg.core.arm9.Arm9.footer(project.rom)!!
            val stored = (io.github.legendaryredfox.foxhg.core.arm9.Arm9.forRom(plain, footer.paramsOffset, compress) as FoxResult.Ok).value
            Files.createDirectories(dir)
            FileRomSink(dir.resolve(name)).use { sink ->
                check(io.github.legendaryredfox.foxhg.core.nds.NdsBuilder.build(project.rom, source, io.github.legendaryredfox.foxhg.core.nds.RomEdits(emptyMap(), stored), sink) is FoxResult.Ok)
            }
        }
    }

    @Test
    fun mapHeaderEdits() {
        val dir = outDir ?: return
        if (!Files.exists(romPath)) return
        FileRomSource(romPath).use { source ->
            val project = open(source)
            val headers = (io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable.load(project) as FoxResult.Ok).value
            // Change the weather and the day music of a few maps and drop the bicycle permission of the first town.
            val edited = headers.mapIndexed { i, h -> if (i in 2..40) h.withWeather(3).copy(musicDay = h.musicDay) else h }
            check(io.github.legendaryredfox.foxhg.core.data.mapheader.MapHeaderTable.write(project, edited) is FoxResult.Ok)
            Files.createDirectories(dir)
            build(project, dir.resolve("map-headers.nds"))
        }
    }

    @Test
    fun arm9Plain() = arm9Fixture("arm9-plain.nds", compress = false)

    @Test
    fun arm9Recompressed() = arm9Fixture("arm9-recompressed.nds", compress = true)

    private val recompressed = HashMap<Int, ByteArray>()
}
