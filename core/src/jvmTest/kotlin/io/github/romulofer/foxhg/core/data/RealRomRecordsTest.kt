package io.github.romulofer.foxhg.core.data

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.eggmove.EggMoves
import io.github.romulofer.foxhg.core.data.event.EventFile
import io.github.romulofer.foxhg.core.data.event.OverworldSprites
import io.github.romulofer.foxhg.core.data.evolution.EvolutionData
import io.github.romulofer.foxhg.core.data.evolution.EvolutionMethod
import io.github.romulofer.foxhg.core.data.evolution.TradeEvolutions
import io.github.romulofer.foxhg.core.data.learnset.Learnset
import io.github.romulofer.foxhg.core.data.learnset.LearnsetEntry
import io.github.romulofer.foxhg.core.data.item.ItemData
import io.github.romulofer.foxhg.core.data.trade.NpcTrade
import io.github.romulofer.foxhg.core.graphics.Icons
import io.github.romulofer.foxhg.core.data.map.FixedPos
import io.github.romulofer.foxhg.core.graphics.Camera3d
import io.github.romulofer.foxhg.core.graphics.Render3d
import io.github.romulofer.foxhg.core.graphics.MapScenes
import io.github.romulofer.foxhg.core.sound.Sounds
import io.github.romulofer.foxhg.core.data.map.Collision
import io.github.romulofer.foxhg.core.data.map.MapFile
import io.github.romulofer.foxhg.core.data.map.MapUsages
import io.github.romulofer.foxhg.core.data.map.TileTypes
import io.github.romulofer.foxhg.core.data.move.MoveData
import io.github.romulofer.foxhg.core.data.tmhm.TmHmTable
import io.github.romulofer.foxhg.core.data.fly.FlyTable
import io.github.romulofer.foxhg.core.data.hiddenitem.HiddenItems
import io.github.romulofer.foxhg.core.data.mapheader.MapHeaderTable
import io.github.romulofer.foxhg.core.data.rocksmash.RockSmashItems
import io.github.romulofer.foxhg.core.data.rocksmash.RockSmashSite
import io.github.romulofer.foxhg.core.data.starter.Starters
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.narc.Narc
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassGenders
import io.github.romulofer.foxhg.core.script.ScriptEntry
import io.github.romulofer.foxhg.core.script.ScriptText
import io.github.romulofer.foxhg.core.text.TextArchive
import io.github.romulofer.foxhg.core.data.trainerclass.TrainerClassPrizes
import io.github.romulofer.foxhg.core.nds.FileRomSource
import io.github.romulofer.foxhg.core.project.RomProject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Needs roms/heartgold.nds (or FOXHG_ROM); passes with a notice when absent. */
class RealRomRecordsTest {
    private val romPath: Path = Path.of(System.getenv("FOXHG_ROM") ?: "../roms/heartgold.nds")

    private fun withProject(block: (RomProject) -> Unit) {
        if (!Files.exists(romPath)) {
            println("SKIPPED: no ROM at ${romPath.toAbsolutePath()}")
            return
        }
        FileRomSource(romPath).use { source -> block((RomProject.open(source) as FoxResult.Ok).value) }
    }

    private fun <T> NarcRecords<T>.assertNarcRoundTrips(project: RomProject) {
        assertContentEquals(project.read(path), toNarc().toBytes(), "$path must re-serialize unchanged")
    }

    private fun <T> FoxResult<T>.ok(): T = (this as? FoxResult.Ok)?.value ?: error((this as FoxResult.Err).message)

    @Test
    fun learnsetsRoundTripAndMatchTheGame() = withProject { project ->
        val table = Learnset.load(project).ok()
        println("learnsets=${table.records.size} longest=${table.records.maxOf { it.entries.size }}")
        table.assertNarcRoundTrips(project)
        // Bulbasaur: Tackle (33) at level 1, Growl (45) at level 3.
        assertEquals(listOf(LearnsetEntry(33, 1), LearnsetEntry(45, 3)), table.records[1].entries.take(2))
        assertTrue(table.records.size >= 496)
    }

    @Test
    fun evolutionsRoundTripAndMatchTheGame() = withProject { project ->
        val table = EvolutionData.load(project).ok()
        table.assertNarcRoundTrips(project)
        val bulbasaur = table.records[1].slots[0]
        assertEquals(listOf(EvolutionMethod.LEVEL.id, 16, 2), listOf(bulbasaur.method, bulbasaur.param, bulbasaur.target))
        assertEquals(EvolutionMethod.TRADE.id, table.records[64].slots[0].method) // Kadabra -> Alakazam
        assertEquals(65, table.records[64].slots[0].target)
        val unknown = table.records.flatMap { it.slots }.filter { it.method != 0 && it.known == null }
        assertEquals(emptyList(), unknown, "every method in the ROM should be a known one")

        val (converted, changes) = TradeEvolutions.convert(table.records, 36)
        println("trade evolutions converted=${changes.size}")
        assertTrue(changes.size >= 10)
        assertTrue(converted.none { r -> r.slots.any { it.known?.isTrade == true } })
    }

    @Test
    fun movesRoundTripAndMatchTheGame() = withProject { project ->
        val table = MoveData.load(project).ok()
        table.assertNarcRoundTrips(project)
        val pound = table.records[1]
        assertEquals(listOf(0, 40, 0, 100, 35), listOf(pound.category, pound.power, pound.type, pound.accuracy, pound.pp))
        assertEquals(471, table.records.size, "3 more records than the 468 named moves")
    }

    @Test
    fun itemsRoundTripAndMatchTheGame() = withProject { project ->
        val table = ItemData.load(project).ok()
        table.assertNarcRoundTrips(project)
        val pokeBall = table.records[4]
        assertEquals(200, pokeBall.price)
        assertEquals(1, pokeBall.battleUseFunc, "Poké Ball is a ball")
        assertEquals(ItemData.SIZE, table.records.first().toBytes().size)
    }

    @Test
    fun npcTradesRoundTripAndMatchTheGame() = withProject { project ->
        val table = NpcTrade.load(project).ok()
        table.assertNarcRoundTrips(project)
        assertEquals(13, table.records.size)
        val species = project.names!!.species
        val names = project.names!!.tradeNames
        assertEquals("ONIX", species[table.records[0].giveSpecies])
        assertEquals("BELLSPROUT", species[table.records[0].askSpecies])
        assertEquals("Rocky", names[0])
        assertEquals("Rudy", names[13])
        assertEquals("BELDUM", species[table.records[12].giveSpecies])
        assertTrue(table.records.all { it.toBytes().size == NpcTrade.SIZE })
    }

    @Test
    fun eggMovesRoundTrip() = withProject { project ->
        val table = EggMoves.load(project).ok()
        table.assertNarcRoundTrips(project)
        assertEquals(1, table.records.size)
        val eggs = table.records.single()
        assertEquals(198, eggs.groups.size)
        assertEquals(listOf(113, 130, 219, 204, 80, 345, 320, 174, 275, 267, 133), eggs.groups.first().moves.take(11))
        assertTrue(!eggs.overLimit)
        val first = eggs.groups.first()
        assertEquals(eggs, eggs.withMoves(first.species, first.moves))
        assertEquals(eggs.groups.size - 1, eggs.withMoves(first.species, emptyList()).groups.size)
    }

    @Test
    fun tmHmTableMatchesTheGameAndRoundTrips() = withProject { project ->
        val table = TmHmTable.load(project).ok()
        assertEquals(100, table.moves.size)
        val moves = project.names!!.moves
        assertEquals("Focus Punch", moves[table.moves[0]], "TM01")
        assertEquals("Cut", moves[table.moves[92]], "HM01")
        val plain = project.readArm9().ok()
        assertContentEquals(plain, table.applyTo(plain), "unchanged table must not touch the ARM9")
        TmHmTable.save(project, table.with(0, 1)).ok()
        assertEquals(1, TmHmTable.load(project).ok().moves[0])
        assertTrue(project.editedArm9 != null)
    }

    @Test
    fun moveAndItemCsvRoundTripThroughTheRom() = withProject { project ->
        val moves = MoveData.load(project).ok().records
        val moveNames = project.names!!.moves
        val moveCsv = MoveData.CSV.export(moves) { moveNames.getOrElse(it) { "" } }
        assertEquals(moves, MoveData.CSV.import(moveCsv, moves).ok())
        val items = ItemData.load(project).ok().records
        val itemNames = project.names!!.items
        val itemCsv = ItemData.CSV.export(items) { itemNames.getOrElse(it) { "" } }
        assertEquals(items, ItemData.CSV.import(itemCsv, items).ok())
    }

    @Test
    fun trainerClassGendersMatchTheGame() = withProject { project ->
        val table = TrainerClassGenders.load(project).ok()
        val names = project.names!!.trainerClasses
        assertEquals(128, table.genders.size)
        assertEquals(listOf("Youngster" to 0, "Lass" to 1), listOf(2, 3).map { names[it] to table.genders[it] })
        val plain = project.readArm9().ok()
        assertContentEquals(plain, table.applyTo(plain))
        TrainerClassGenders.save(project, table.with(2, 1)).ok()
        assertEquals(1, TrainerClassGenders.load(project).ok().genders[2])
    }

    @Test
    fun flyTableMatchesTheGameAndSavesBack() = withProject { project ->
        val table = FlyTable.load(project).ok()
        val loc = project.names!!.locations
        val headers = (MapHeaderTable.load(project) as FoxResult.Ok).value
        val flyTo = table.entries.map { loc[headers[it.flyHeader].locationName] }
        assertEquals(30, table.entries.size)
        assertEquals(listOf("New Bark Town", "Cherrygrove City", "Violet City"), flyTo.take(3))
        assertEquals(695 to 397, table.entries[0].let { it.flyX to it.flyY })
        assertEquals(24, table.entries.count { it.isFlyPoint && it.flyHeader == it.unlockHeader && it.flyX == it.unlockX }, "most towns unlock where Fly lands")
        assertTrue(table.entries.all { it.flyHeader < headers.size && it.blackoutHeader < headers.size && it.unlockHeader < headers.size })
        val plain = project.readArm9().ok()
        assertContentEquals(plain, table.applyTo(plain), "an unedited table writes back the same ARM9")

        FlyTable.save(project, table.with(0, table.entries[0].copy(flyX = 700, isFlyPoint = false))).ok()
        assertEquals(700 to false, FlyTable.load(project).ok().entries[0].let { it.flyX to it.isFlyPoint })
        project.revertAll()
    }

    @Test
    fun hiddenItemsMatchTheGameAndSaveBack() = withProject { project ->
        val table = HiddenItems.load(project).ok()
        val items = project.names!!.items
        assertEquals(231, table.entries.size)
        assertEquals(231, table.capacity, "the vanilla table is full")
        assertEquals(listOf("Potion" to 0, "Nugget" to 1, "Nugget" to 225), table.entries.take(3).map { items[it.item] to it.index })
        assertEquals(8000, table.entries[0].script)
        assertEquals(231, table.entries.map { it.index }.toSet().size, "every spot has its own index")
        assertTrue(table.entries.all { it.quantity >= 1 && it.unknown3 == 0 && it.unknown4 == 0 })
        val plain = project.readArm9().ok()
        assertContentEquals(plain, table.applyTo(plain), "an unedited table writes back the same ARM9")

        HiddenItems.save(project, table.with(0, table.entries[0].copy(item = 3, quantity = 5))).ok()
        assertEquals(3 to 5, HiddenItems.load(project).ok().entries[0].let { it.item to it.quantity })
        HiddenItems.save(project, HiddenItems.load(project).ok().removed(230)).ok()
        val shorter = HiddenItems.load(project).ok()
        assertEquals(230, shorter.entries.size)
        val after = project.readArm9().ok()
        val slot = 0xFA558 + 230 * 8
        assertTrue((0 until 8).all { after[slot + it] == 0.toByte() }, "the dropped slot is cleared")
        project.revertAll()
    }

    @Test
    fun startersMatchTheGameFollowEditsAndRevertCleanly() = withProject { project ->
        assertEquals(Starters.VANILLA, Starters.load(project).ok())
        val names = project.names!!
        val before = project.read("a/0/1/2")!!

        val report = Starters.change(project, Starters(listOf(1, 4, 7), heldItem = 17, level = 12)).ok()
        assertEquals(Starters(listOf(1, 4, 7), 17, 12), Starters.load(project).ok())
        assertEquals(7, report.rivalScriptsPatched, "all seven rival scripts follow")
        assertTrue(report.textPatched && report.chooserPatched)

        val scripts = Narc.parse(project.read("a/0/1/2")!!).ok().files
        for (id in HgssLayout.RIVAL_SCRIPT_FILES) {
            val d = scripts[id]
            val at = (0..d.size - 13).first { i -> d[i] == 0xCE.toByte() && d[i + 1] == 0.toByte() && d[i + 2] == 0x0C.toByte() && d[i + 3] == 0x80.toByte() && d[i + 8] == 1.toByte() }
            assertEquals(4, d[at + 17 + (d[at + 13].toInt() and 0xFF) + 4].toInt(), "script $id compares the second branch with the new second species")
        }
        val text = TextArchive.load(project).ok().records[190]
        assertTrue(text.entries[1].text().contains(names.species[1]), text.entries[1].text())
        assertTrue(text.entries[2].text().contains(names.species[4]) && text.entries[2].text().contains("Fire-type"))
        assertTrue(text.entries[6].text().contains(names.species[7]) && text.entries[6].text().contains("Water-type"))
        assertTrue(!text.entries[1].text().contains("CHIKORITA"))

        Starters.change(project, Starters.VANILLA).ok()
        assertEquals(Starters.VANILLA, Starters.load(project).ok())
        assertContentEquals(before, project.read("a/0/1/2")!!, "the scripts are back to the original bytes")
        assertTrue(!project.isDirty, "changing the starters back leaves nothing edited: ${project.editedPaths} ${project.editedOverlays} ${project.editedArm9 != null}")

        assertTrue(Starters.change(project, Starters(listOf(0, 4, 7), 0, 5)) is FoxResult.Err)
        assertTrue(Starters.change(project, Starters(listOf(1, 4, 7), 300, 5)) is FoxResult.Err)
        assertTrue(!project.isDirty, "a refused change writes nothing")
    }

    @Test
    fun rockSmashMatchesTheGuideAndSavesBack() = withProject { project ->
        val sites = NarcRecords.load(project, RockSmashSite.SPEC).ok()
        assertEquals(MapHeaderTable.load(project).ok().size, sites.records.size, "one file per map header")
        assertEquals(mapOf(0 to 538, 1 to 1, 2 to 1), sites.records.groupingBy { it.type }.eachCount())
        assertTrue(sites.records.all { it.odds in 0..100 })
        assertContentEquals(project.read("a/2/5/3")!!, sites.toNarc().toBytes(), "an unedited NARC writes back the same bytes")

        val items = RockSmashItems.load(project).ok()
        val names = project.names!!.items
        fun table(t: Int) = items.tables[t].map { names[it] }
        assertEquals(listOf("Max Ether", "Revive", "Heart Scale", "Red Shard", "Blue Shard", "Green Shard", "Yellow Shard", "Star Piece"), table(RockSmashSite.DEFAULT))
        assertEquals(listOf("Red Shard", "Yellow Shard", "Helix Fossil", "Max Ether", "Blue Shard", "Green Shard", "Old Amber", "Max Revive"), table(RockSmashSite.RUINS_OF_ALPH), "HeartGold's table")
        assertEquals(listOf("Max Ether", "Pearl", "Big Pearl", "Red Shard", "Yellow Shard", "Claw Fossil", "Claw Fossil", "Rare Bone"), table(RockSmashSite.CLIFF_CAVE))
        val plain = project.readOverlay(1).ok()
        assertContentEquals(plain, items.applyTo(plain))

        sites.with(5, RockSmashSite(77, RockSmashSite.CLIFF_CAVE)).save(project).ok()
        RockSmashItems.save(project, items.with(RockSmashSite.DEFAULT, 0, 3)).ok()
        assertEquals(RockSmashSite(77, 2), NarcRecords.load(project, RockSmashSite.SPEC).ok().records[5])
        assertEquals(3, RockSmashItems.load(project).ok().tables[0][0])
        assertEquals(setOf(1), project.editedOverlays)
        project.revertAll()
        assertTrue(!project.isDirty)
    }

    @Test
    fun trainerClassPrizesMatchTheGameAndSurviveAnOverlayEdit() = withProject { project ->
        val table = TrainerClassPrizes.load(project).ok()
        assertEquals(129, table.entries.size)
        assertEquals(listOf(4, 40, 15), listOf(2, 32, 24).map { table.multiplierOf(it) }, "Youngster, Rich Boy, Ace Trainer")
        val plain = project.readOverlay(12).ok()
        assertContentEquals(plain, table.applyTo(plain))
        TrainerClassPrizes.save(project, table.withEntry(2, 9)).ok()
        assertEquals(9, TrainerClassPrizes.load(project).ok().entries[2].multiplier)
        assertEquals(setOf(12), project.editedOverlays)
    }

    @Test
    fun nameListsFollowMessageEdits() = withProject { project ->
        assertEquals("Great Ball", project.names!!.items[3])
        val table = TextArchive.load(project).ok()
        val renamed = table.records[222].withText(3, "Mega Ball").ok()
        table.with(222, renamed).save(project).ok()
        assertEquals("Mega Ball", project.names!!.items[3], "the lists are rebuilt after the text changed")
        project.revertAll()
        assertEquals("Great Ball", project.names!!.items[3], "and again after a revert")
    }

    @Test
    fun eventFilesRoundTripAndMatchTheGame() = withProject { project ->
        val table = EventFile.load(project).ok()
        table.assertNarcRoundTrips(project)
        val files = table.records
        assertEquals(491, files.size)
        assertEquals(7, files.count { it.isEmpty })
        assertEquals(listOf(1034, 2667, 1317, 195), listOf(files.sumOf { it.spawnables.size }, files.sumOf { it.overworlds.size }, files.sumOf { it.warps.size }, files.sumOf { it.triggers.size }))
        assertTrue(files.all { it.trailing.isEmpty() }, "no bytes after the last record in vanilla")
        assertEquals(10, files.flatMap { it.overworlds }.count { it.isAlias })
    }

    @Test
    fun scriptFilesRoundTripAndTheirTextToo() = withProject { project ->
        val table = ScriptEntry.load(project).ok()
        table.assertNarcRoundTrips(project)
        val scripts = table.records.filterIsInstance<ScriptEntry.Script>()
        assertEquals(965, table.records.size)
        assertEquals(492, scripts.size, "script files that parse and write back exactly")
        val levels = table.records.filterIsInstance<ScriptEntry.Level>()
        val raw = table.records.filterIsInstance<ScriptEntry.Raw>()
        assertEquals(468, levels.size, "every level script parses and writes back exactly")
        assertEquals(5, raw.size, "3 empty files and 2 odd script files stay raw bytes")
        for (s in scripts) {
            val back = ScriptText.parse(ScriptText.toText(s.file)).ok()
            assertEquals(s.file, back, "text form of a script file must read back to the same file")
        }
        val notes = ScriptText.Messages { "A message" }
        var annotated = 0
        for (s in scripts) {
            val text = ScriptText.toText(s.file, notes)
            if (text.contains("; FLAG_") || text.contains("; VAR_") || text.contains("; \"A message\"")) annotated++
            assertEquals(s.file, ScriptText.parse(text).ok(), "notes do not change what the text means")
        }
        assertTrue(annotated > 300, "most script files carry flags, variables or messages that get a note, got $annotated")
    }

    @Test
    fun mapFilesRoundTripAndMatchTheGame() = withProject { project ->
        val table = MapFile.load(project).ok()
        table.assertNarcRoundTrips(project)
        val maps = table.records
        assertEquals(676, maps.size)
        assertEquals(2859, maps.sumOf { it.buildings.size })
        assertTrue(maps.all { m -> m.buildings.all { it.width == 16L && it.height == 16L && it.length == 16L } }, "building scales are 16 in vanilla")
        assertEquals("Tall grass", TileTypes.info(2).label)
        assertTrue(TileTypes.info(16).surfable && TileTypes.info(2).encounters)
        val edited = maps[3].withTile(5, 7, type = 2, collision = 0x80)
        assertEquals(listOf(2, 0x80), listOf(edited.type(5, 7), edited.collision(5, 7)))
        assertEquals(maps[3].collision(7, 5), edited.collision(7, 5), "only the (5, 7) tile changed")
        assertContentEquals(MapFile.parse(edited.toBytes()).ok().toBytes(), edited.toBytes())
    }

    @Test
    fun mapPermissionsAreRowMajorAndEveryUsedMapHasAUsage() = withProject { project ->
        val maps = MapFile.load(project).ok().records
        val usages = MapUsages(project)
        val events = EventFile.load(project).ok().records
        val shown = usages.headers.indices.count { h -> usages.mapOf(h)?.let { m -> usages.of(m).any { it.header == h } } == true }
        assertTrue(shown in 300..400, "most map headers show a map (355 in this ROM), $shown do")
        var npcs = 0
        var walkable = 0
        maps.indices.forEach { map ->
            for (u in usages.of(map)) {
                val ev = events.getOrNull(usages.headers[u.header].eventFile) ?: continue
                for (o in ev.overworlds.filter { !it.isAlias && it.x / 32 == u.x && it.y / 32 == u.y }) {
                    npcs++
                    if (!Collision.isBlocked(maps[map].collision(o.x % 32, o.y % 32))) walkable++
                }
            }
        }
        assertTrue(npcs > 1000, "many NPCs sit on a map cell we can find, found $npcs")
        assertTrue(walkable * 100 / npcs >= 90, "NPCs stand on walkable tiles: $walkable of $npcs (row-major axes)")
    }

    @Test
    fun iconsDecodeToRealPictures() = withProject { project ->
        val icons = Icons(project)
        val bulbasaur = icons.pokemon(1)!!
        assertEquals(listOf(32, 32), listOf(bulbasaur.width, bulbasaur.height))
        assertTrue(bulbasaur.opaqueColors >= 4, "an icon has several colours, got ${bulbasaur.opaqueColors}")
        assertTrue(bulbasaur != icons.pokemon(4)!!, "different species have different icons")
        val pokeBall = icons.item(4)!!
        assertEquals(listOf(32, 32), listOf(pokeBall.width, pokeBall.height))
        assertTrue(pokeBall.opaqueColors >= 3)
        assertTrue((1..493).all { icons.pokemon(it) != null }, "every species has an icon")
        assertTrue((1..536).count { icons.item(it) != null } > 500, "nearly every item has an icon")
        // A Poké Ball is red above and white below.
        fun count(rows: IntRange, test: (Int) -> Boolean) = rows.sumOf { y -> (0 until 32).count { x -> pokeBall.argb[y * 32 + x].let { it ushr 24 != 0 && test(it) } } }
        assertTrue(count(4..14) { (it shr 16 and 0xFF) > (it and 0xFF) + 60 } > 20, "the upper half has red")
        assertTrue(count(17..28) { (it and 0xFF) > 150 && (it shr 16 and 0xFF) > 150 && (it shr 8 and 0xFF) > 150 } > 20, "the lower half has white")
    }

    @Test
    fun overworldSpritesDecodeFromTheSpriteTable() = withProject { project ->
        val table = (OverworldSprites.load(project) as FoxResult.Ok).value
        assertEquals(901, table.entries.size)
        assertEquals(0, table.entries[1]!!.spriteFile)
        val icons = Icons(project)
        val boy = icons.overworld(1, 1)!!
        assertEquals(listOf(32, 32), listOf(boy.width, boy.height))
        assertTrue(boy.opaqueColors >= 5, "a character has several colours, got ${boy.opaqueColors}")
        assertTrue(boy != icons.overworld(1, 0)!! && boy != icons.overworld(1, 2)!!, "facings differ")
        assertEquals(null, icons.overworld(60000, 1))
        val missing = table.entries.keys.filter { icons.overworld(it, 1) == null }
        assertTrue(missing.size < 20, "nearly every sprite entry decodes, ${missing.size} do not: ${missing.take(10)}")
        // Every sprite file the table names exists.
        val files = (Narc.parse(project.read(HgssLayout.OVERWORLD_SPRITES)!!) as FoxResult.Ok).value.files.size
        assertTrue(table.entries.values.all { it.spriteFile < files })
    }

    @Test
    fun mapScenesRenderTheTerrainAndBuildings() = withProject { project ->
        val scenes = MapScenes(project)
        val usages = MapUsages(project)
        val maps = MapFile.load(project).ok().records
        val used = maps.indices.filter { usages.of(it).isNotEmpty() }
        val pictures = used.associateWith { scenes.render(it, 4) }
        val missing = pictures.filterValues { it == null }.keys
        assertTrue(missing.size < used.size / 20, "nearly every used map renders, ${missing.size} of ${used.size} do not: ${missing.take(10)}")
        val covered = pictures.values.filterNotNull().map { img -> img.argb.count { it ushr 24 != 0 } * 100 / img.argb.size }
        assertTrue(covered.count { it >= 50 } > covered.size / 2, "most maps are mostly covered by terrain: ${covered.sorted().take(5)}...")
        val first = pictures[0]!!
        assertEquals(listOf(128, 128), listOf(first.width, first.height))
        assertTrue(first.opaqueColors > 30, "New Bark Town has many colours, got ${first.opaqueColors}")
        System.getenv("FOXHG_DUMP_DIR")?.let { dir ->
            for (m in listOf(0, 1, 4, 100, 200, 300)) {
                val img = scenes.render(m, 8) ?: continue
                val out = java.awt.image.BufferedImage(img.width, img.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                out.setRGB(0, 0, img.width, img.height, img.argb, 0, img.width)
                javax.imageio.ImageIO.write(out, "png", java.io.File(dir, "map$m.png"))
            }
        }
    }

    @Test
    fun aMapRendersInPerspectiveFromAnyCamera() = withProject { project ->
        val scenes = MapScenes(project)
        val meshes = scenes.scene(0)!!
        assertTrue(meshes.sumOf { it.vertices.size / 15 } > 1000, "New Bark Town has many triangles")
        val top = scenes.render3d(0, Camera3d(pitch = 1.5, distance = 30.0), 480, 360)!!.image
        val low = scenes.render3d(0, Camera3d(yaw = 0.8, pitch = 0.35, distance = 22.0), 480, 360)!!.image
        assertEquals(listOf(480, 360), listOf(top.width, top.height))
        fun covered(img: io.github.romulofer.foxhg.core.graphics.RgbaImage) = img.argb.count { it ushr 24 != 0 } * 100 / img.argb.size
        assertTrue(covered(top) > 40, "seen from above the map fills much of the picture, ${covered(top)}%")
        assertTrue(low.opaqueColors > 30 && top != low)
        assertTrue(scenes.render3d(0, Camera3d(distance = 2.0), 64, 48) != null, "a camera inside the scene does not crash")
        System.getenv("FOXHG_DUMP_DIR")?.let { dir ->
            val views = listOf(
                Camera3d(), Camera3d(yaw = 0.8, pitch = 0.4, distance = 26.0), Camera3d(yaw = 2.5, pitch = 0.25, distance = 20.0),
            )
            val sheet = java.awt.image.BufferedImage(views.size * 480, 360, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            views.forEachIndexed { k, cam ->
                val img = scenes.render3d(0, cam, 480, 360)!!.image
                for (j in 0 until 360) for (i in 0 until 480) sheet.setRGB(k * 480 + i, j, if (img.argb[j * 480 + i] ushr 24 != 0) img.argb[j * 480 + i] else 0xFF87CEEB.toInt())
            }
            javax.imageio.ImageIO.write(sheet, "png", java.io.File(dir, "view3d.png"))
        }
    }

    @Test
    fun clickingTheSceneFindsTheBuildingAndThePointUnderIt() = withProject { project ->
        val scenes = MapScenes(project)
        val map = MapFile.load(project).ok().records[0]
        // Every building position round-trips through the fixed point numbers.
        for (m in MapFile.load(project).ok().records) for (b in m.buildings) {
            assertEquals(b.x, FixedPos.of(b.x.value)); assertEquals(b.y, FixedPos.of(b.y.value)); assertEquals(b.z, FixedPos.of(b.z.value))
        }
        val index = map.buildings.indexOfFirst { it.modelId == 22L } // a house
        assertTrue(index >= 0)
        val house = map.buildings[index]
        val camera = Camera3d(pitch = 1.5, distance = 30.0, targetX = house.x.value, targetZ = house.z.value)
        val picture = scenes.render3d(0, camera, 400, 300, highlight = index)!!
        assertEquals(index, picture.tagAt(200, 150), "the house stands in the middle of the picture")
        val point = picture.pointAt(200, 150)!!
        assertTrue(kotlin.math.abs(point[0] - house.x.value) < 4 && kotlin.math.abs(point[2] - house.z.value) < 4, "the point under the pointer is on the house: ${point.toList()}")
        assertTrue(picture.tagAt(5, 5) == -1 || picture.tagAt(5, 5) == Render3d.NOTHING || picture.tagAt(5, 5) >= 0)
        assertEquals(null, picture.pointAt(-1, 0))
        val moved = scenes.render3d(0, camera, 400, 300, map.buildings.filterIndexed { i, _ -> i != index })!!
        assertTrue(moved.tagAt(200, 150) != index, "a removed building is gone")
        assertTrue(scenes.renderBuilding(22, 96, 96)!!.opaqueColors > 8, "a building model renders alone")
        assertEquals(null, scenes.renderBuilding(100000, 96, 96))
    }

    @Test
    fun theWalkingSurfaceOfEveryMapRoundTripsAndFlatPlatesMatchTheTerrain() = withProject { project ->
        val maps = MapFile.load(project).ok().records
        var plates = 0
        maps.forEachIndexed { i, m ->
            val bdhc = (m.walkingSurface() as? FoxResult.Ok)?.value ?: error("map $i has no readable BDHC")
            assertContentEquals(m.bdhc, bdhc.toBytes(), "map $i writes back exactly")
            plates += bdhc.plates.size
            assertTrue(bdhc.plates.all { it.corner1 < bdhc.points.size && it.corner2 < bdhc.points.size && it.slope < bdhc.slopes.size && it.height < bdhc.heights.size }, "map $i: plates point at real data")
            assertTrue(bdhc.stripes.all { it.first + it.count <= bdhc.plateIndices.size }, "map $i: stripes stay inside the index list")
        }
        assertTrue(plates > 5000, "many plates, $plates")

        // On the surface of New Bark Town the flat plates say where the ground is; the terrain model agrees.
        val bdhc = (maps[0].walkingSurface() as FoxResult.Ok).value
        val ground = MapScenes(project).heightGrid(0)
        var agree = 0
        var tested = 0
        for (plate in bdhc.plates) {
            val h = bdhc.flatHeight(plate) ?: continue
            val r = bdhc.rectangle(plate)!!
            val tx = ((r[0] + r[2]) / 2 + 16).toInt().coerceIn(0, 31)
            val tz = ((r[1] + r[3]) / 2 + 16).toInt().coerceIn(0, 31)
            tested++
            if (kotlin.math.abs(ground[tz * 32 + tx] - h) < 0.3) agree++
        }
        assertTrue(tested > 5 && agree * 100 / tested >= 80, "flat plates sit on the terrain: $agree of $tested")
    }

    @Test
    fun battleSpritesAndFormIconsDecode() = withProject { project ->
        val icons = Icons(project)
        for (record in 1..507) {
            val front = icons.pokemonFront(record)
            val back = icons.pokemonBack(record)
            assertTrue(front != null && back != null, "record $record has battle pictures")
            assertEquals(listOf(80, 80), listOf(front!!.width, front.height))
            assertTrue(front.opaqueColors >= 3 && back!!.opaqueColors >= 3, "record $record: ${front.opaqueColors} and ${back.opaqueColors} colours")
        }
        assertTrue(icons.pokemonFront(25) != icons.pokemonFront(25, shiny = true), "a shiny Pikachu has other colours")
        assertTrue(icons.pokemonFront(25) != icons.pokemonBack(25))
        assertTrue(icons.pokemonFront(386) != icons.pokemonFront(496), "Deoxys attack differs from normal")
        assertEquals(null, icons.pokemonFront(508))
        for (record in 494..507) {
            val icon = icons.pokemon(record)
            assertTrue(icon != null && icon.opaqueColors >= 3, "record $record has an icon")
        }
        assertTrue(icons.pokemon(496) != icons.pokemon(386), "Deoxys attack has its own icon")
        assertTrue(icons.pokemonIcon(201, form = 5) != icons.pokemonIcon(201, form = 0), "Unown letters differ")
        assertEquals(null, icons.pokemon(508))
        System.getenv("FOXHG_DUMP_DIR")?.let { dir ->
            val records = (494..507).toList()
            val sheet = java.awt.image.BufferedImage(records.size * 80, 80 * 3 + 32 + 80, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            fun draw(img: io.github.romulofer.foxhg.core.graphics.RgbaImage?, x: Int, y: Int) {
                if (img != null) for (j in 0 until img.height) for (i in 0 until img.width) if (img.argb[j * img.width + i] ushr 24 != 0) sheet.setRGB(x + i, y + j, img.argb[j * img.width + i])
            }
            records.forEachIndexed { k, r ->
                draw(icons.pokemon(r), k * 80, 0)
                draw(icons.pokemonFront(r), k * 80, 32)
                draw(icons.pokemonBack(r), k * 80, 112)
                draw(icons.pokemonFront(r, shiny = true), k * 80, 192)
            }
            javax.imageio.ImageIO.write(sheet, "png", java.io.File(dir, "forms.png"))
        }
    }

    @Test
    fun trainerClassPicturesDecodeFromTheirCells() = withProject { project ->
        val icons = Icons(project)
        val classes = (0 until 129).map { icons.trainerClass(it) }
        assertTrue(classes.all { it != null }, "every class has a front picture")
        val first = classes[0]!!
        assertEquals(listOf(80, 80), listOf(first.width, first.height))
        assertTrue(first.opaqueColors >= 8, "a trainer has many colours, got ${first.opaqueColors}")
        assertTrue(first != classes[1]!!, "classes differ")
        assertTrue(classes.all { it!!.opaqueColors >= 4 })
        assertEquals(null, icons.trainerClass(1000))
    }

    @Test
    fun soundsRenderCriesEffectsAndMusic() = withProject { project ->
        val sounds = Sounds(project)
        assertEquals(2379, sounds.sdat!!.sequences.size)
        assertEquals("SEQ_PV001", sounds.name(1))
        assertEquals("SEQ_SE_DP_SELECT", sounds.name(1500))
        fun rms(c: io.github.romulofer.foxhg.core.sound.PcmClip) = Math.sqrt(c.samples.sumOf { it.toDouble() * it } / c.samples.size)
        val bulbasaur = sounds.cry(1)!!
        assertTrue(bulbasaur.seconds in 0.5..1.2 && rms(bulbasaur) > 500, "a cry is under a second and audible: ${bulbasaur.seconds}")
        assertTrue(bulbasaur.samples.toList() != sounds.cry(25)!!.samples.toList(), "cries differ per species")
        assertTrue((1..493).all { sounds.cry(it) != null }, "every species has a cry")
        val select = sounds.sequence(1500)!!
        assertTrue(select.seconds in 0.02..2.0 && rms(select) > 200, "a sound effect is short and audible: ${select.seconds}")
        val title = sounds.sequence(1004)!!
        assertTrue(title.seconds > 20 && rms(title) > 500, "music runs for a while and is audible: ${title.seconds}")
        assertEquals(select.samples.toList(), sounds.sequence(1500)!!.samples.toList(), "rendering is deterministic")
    }
}
