# Confirmed formats

Facts verified against the clean English HeartGold ROM (`roms/heartgold.nds`, game code `IPKE`) by tests in `core`.
Add an entry here whenever a layout, offset or quirk is confirmed.

## NDS container

- Header is 0x200 bytes of fields; the image has a 0x4000-byte header area (`headerSize` at 0x84). CRC-16/MODBUS of
  bytes 0x000..0x15D is stored at 0x15E (`Crc16`, checked against the real header).
- HeartGold English layout, in file order: header 0x0, ARM9 0x4000 (size 0xBA314, followed by a 12-byte footer that
  starts `2106C0DE`), ARM9 overlay table (`y9`) 0xBE400 (0x1020 bytes), overlay and data files from 0xBF600, ARM7
  0x2F7400, FNT 0x31EC00, FAT 0x31F800, banner 0x320A00 (0x840 bytes). Used size (0x80) is 0x78C763C; the image is
  padded with 0xFF to 128 MB (capacity byte at 0x14 is 10, chip size = 0x20000 << capacity).
- FAT has 513 entries (8 bytes each: start, end). Entries 0..128 are the ARM9 overlays and have **no name** in the
  FNT; 384 files are named (all the `a/x/y/z` NARCs). `NdsRom.files` lists named files, `NdsRom.file(id)` also finds
  unnamed ones.
- FNT: main table of 8-byte entries (subtable offset, first file id, parent id; the root's last field is the
  directory count). Subtable entry: byte `n` = file name length (1..0x7F) followed by the name; `0x80 | n` followed by
  the name and a u16 directory id (`0xF000 + index`); `0` ends the directory. File ids are sequential per directory.
- ARM9 overlay table entry (32 bytes): id, RAM address, RAM size, BSS size, static-init start, static-init end, FAT
  file id, then a word with compressed size (low 24 bits) and flags (high 8 bits, bit 0 = compressed).
- FAT entries never overlap and gaps between files are 0..29 bytes of padding.
- 129 ARM9 overlays, no ARM7 overlays.

## NARC

- Header 16 bytes (`NARC`, BOM `FFFE`, version `0100`, total size, header size 0x10, 3 sections), then `BTAF`
  (file ranges relative to the image start), `BTNF` (name table, kept raw), `GMIF` (data, each file padded to 4 bytes
  with 0xFF).
- **All 308 NARCs of the ROM parse and serialize back byte-for-byte** (`RealRomFileSystemTest`).
- `a/0/0/2` is a NARC (personal data), confirmed present.

## ARM9 replacement

- `RomEdits.arm9` replaces the ARM9 as it must be stored (`Arm9.forRom`: plain with `compressed_static_end` = 0, or BLZ with
  the raw prefix kept up to the module parameters and `compressed_static_end` = RAM base + packed size).
- Everything after the ARM9 (the tail starting at the 0x200-aligned end of ARM9 + footer, here the ARM9 overlay table at
  0xBE400) shifts by a multiple of 0x200. Header offsets (0x30 ARM7, 0x40 FNT, 0x48 FAT, 0x50/0x58 overlay tables, 0x68
  banner, 0x80 used size), the ARM9 size (0x2C), every FAT entry and the header CRC are adjusted; the shifted ROM is a lazy
  `RomSource` view, so file edits then apply on top of it.
- Vanilla ARM9: 762,644 bytes packed, 1,122,040 plain, 759,892 with `Blz.compress`. A plain ARM9 grows the ROM by 359,424
  bytes (134,217,728 -> 134,577,152).
- **Boot-tested:** both the plain and the recompressed ARM9 builds (no other change) reach the same intro frame as vanilla
  after 2400 frames in DeSmuME. All 513 files are byte-identical after the shift (`RealRomArm9Test`).

## Building

- `NdsBuilder` patches a copy of the original image. Unedited build has the same SHA-256 as the input
  (`RealRomBuildTest`). Edited builds keep ARM9 and untouched files identical and re-open cleanly.

## BLZ ("bottom LZ") code compression

- The ARM9 binary and 127 of the 129 ARM9 overlays are BLZ-compressed. Header is the last 8 bytes: `u24 compressedLen`
  + `u8 headerLen` (>= 8, 0xFF padding before the last 8 bytes) then `u32 extraSize`; output length = file length +
  extraSize. The stream is read backwards and writes backwards (LZ10-like: flag byte MSB first; match = 2 bytes,
  `length = (b1 >> 4) + 3`, `disp = (((b1 & 0xF) << 8) | b2) + 3`).
- **Every compressed overlay decompresses to exactly its overlay-table RAM size**; the ARM9 (762,644 bytes packed) gives
  1,122,040 bytes. All of them satisfy the in-place rule (the write pointer never passes unread input), which
  `Blz.isInPlaceSafe` checks and `Blz.compress` guarantees (it keeps a raw prefix when needed).
- Our compressor round-trips (ARM9, a large overlay) and passes the in-place check. Total packed size of the 127
  overlays: 2,291,932 bytes original, 2,291,736 with `Blz.compress`.
- **Boot-tested:** a ROM with all 127 overlays replaced by our recompressed versions reaches the same intro frame as
  vanilla after 2400 frames in DeSmuME (`scripts/boot-test.sh`, byte-identical screenshot). **[UNVERIFIED]** for the
  ARM9: `NdsBuilder` cannot replace it yet.
- **Overlay table entries must match the file.** For a compressed overlay, the last word of its table entry is
  `flags << 24 | compressedSize` and equals the file size in all 127 cases; the loader relies on it. Replacing an
  overlay without updating it gives a white screen (seen in the emulator), so `NdsBuilder` updates it.
- The ARM9 in the ROM is followed by a 12-byte footer: bytes `21 06 C0 DE` (little-endian word `0xDEC00621`), the offset of
  the module-parameter block (0xBA0), 0. It is not part of the BLZ stream.
- **Module parameters** (at the footer's offset, inside the always-raw first 0x41BA bytes): autoload list, autoload list
  end, autoload start, static BSS start/end, **`compressed_static_end` (+0x14) = `0x02000000` + packed size**, SDK
  version, the nitro marker. The startup code decompresses only when `compressed_static_end` is non-zero. Vanilla:
  `0x020BA314`, identical in the packed and unpacked image.

## Message archives (`a/0/2/7`)

- 829 archives, 49,984 messages. Header `u16 count, u16 key`. Table entry `i`: `u32 offset, u32 length` (length in
  u16 units, including the 0xFFFF terminator), both XOR-masked with `k | k << 16` where
  `k = (key * 0x2FD * (i + 1)) & 0xFFFF`. Message `i` characters are XOR-masked with a key that starts at
  `(0x91BD3 * (i + 1)) & 0xFFFF` and adds 0x493D per character.
- **All 829 archives re-serialize byte-for-byte.**
- Messages starting with 0xF100 are 9-bit compressed: values are packed 9 bits at a time into the **low 15 bits** of
  each 16-bit word (bit 15 unused), LSB first, the unused bits of the last word are ones. A 0x1FF value ends the
  string when it fits in the bits left in the last word; a string that fills its words exactly has no end mark. A plain
  0xFFFF word always follows. Vanilla HeartGold uses this only for trainer names (bank 729, all 738 entries); all of
  them decode and re-encode byte-for-byte. Anything that does not re-encode identically is kept as plain codes.
- Names the editors use (bank index in `TextBanks`): species 237 (upper case, index = dex number), moves 750, items 222,
  abilities 720, types 735, trainer names 729 (index = trainer id), trainer classes 730, map names 279. Verified by `RealRomNamesTest`.
- Charmap: generated from the community `charmap.txt` by `scripts/gen-charmap.py` (2892 entries).

## Personal data (`a/0/0/2`)

- 508 records of 44 bytes (496 species + forms). Bytes: 0-5 HP/Atk/Def/Spe/SpA/SpD, 6-7 types, 8 catch rate, 9 base
  EXP, 10-11 EV yield (2 bits each in order HP, Atk, Def, Spe, SpA, SpD), 12-13 and 14-15 held items, 16 gender ratio,
  17 hatch cycles, 18 base friendship, 19 growth rate, 20-21 egg groups, 22-23 abilities, 24 Safari flee rate,
  25 colour (low 7 bits) and flip (bit 7), 26-27 padding, 28-43 TM/HM bits (4 words).
- **All 508 records round-trip**; Bulbasaur and Pikachu match the known stats, types, abilities and catch rates.
- **[UNVERIFIED]**: which of the TM/HM bits beyond TM92/HM08 are move-tutor flags.

## Learnsets (`a/0/3/3`)

- 508 records. Each is a list of u16 `move | level << 9` (move in the low 9 bits) ended by 0xFFFF; bytes after the
  terminator are padding and are kept. The longest vanilla learnset has 20 entries (`Learnset.MAX_ENTRIES`).
- **All records re-serialize unchanged; the NARC re-serializes byte-for-byte.** Bulbasaur: Tackle at level 1, Growl at 3.

## Evolutions (`a/0/3/4`)

- 7 slots of `u16 method, u16 param, u16 target` (42 bytes) plus trailing padding. Method ids 0..26 (see
  `EvolutionMethod`); every method present in the ROM is a known one. Param meaning depends on the method (level, item,
  move, species, friendship, beauty).
- **Round trip is exact.** Vanilla HeartGold has 17 trade evolutions (method 5 trade, 6 trade holding an item);
  `TradeEvolutions.convert` turns trade into level-up at a chosen level and trade-with-item into use-item (method 7).
- Kadabra is record 64 (trade into 65).

## Moves (`a/0/1/1`)

- 471 records of 16 bytes (3 more than the 468 named moves): effect u16, category (0 physical, 1 special, 2 status),
  power, type, accuracy (0 = never misses), PP, effect chance, range u16, priority (signed), flags, contest effect,
  contest type, u16 contest word. **Round trip is exact**; Pound = physical, 40 power, normal, 100 accuracy, 35 PP.

## Trainers (`a/0/5/5` headers, `a/0/5/6` parties)

- 738 trainers; both NARCs have 738 files. Header is 20 bytes: type (bit 0 custom moves, bit 1 held items), class,
  unknown byte, party size, 4 battle items (u16), AI flags (u32), double-battle (u32).
- Party record size depends on the type: 8 bytes (difficulty, gender/ability override, level, species+form, capsule),
  10 with a held item, 16 with custom moves, 18 with both; species is the low 10 bits and the form the high 6 bits.
  Vanilla: 487 default, 177 custom moves, 7 items, 67 both.
- **All 738 headers and parties re-serialize unchanged.** Trainer 0 (a dummy) has 8 spare party bytes, kept in
  `Trainer.trailing`. Falkner is trainer 20: Pidgey L9, Pidgeotto L13, class Leader. Names come from bank 729.
- A ROM with Pidgey replaced everywhere in all parties boots to the vanilla intro frame.

## Wild encounters (`a/0/3/7` HeartGold, `a/1/3/6` SoulSilver)

- 142 records of 196 bytes in each NARC; **both ROMs carry both tables** and the game loads its own. Layout (decomp
  `EncounterData`): 6 rates (walking, surfing, rock smash, old/good/super rod), 2 dummy bytes, then grass: 12 levels (shared
  by the times of day) and 12 morning, 12 day, 12 night species (u16); 2 Hoenn + 2 Sinnoh radio species; surf (5), rock
  smash (2), old rod (5), good rod (5), super rod (5) slots of `min level, max level, species u16`; land swarm, surf swarm,
  night-fishing and fishing-swarm species (u16).
- **Both NARCs re-serialize unchanged.** Route 29 (file 1): Pidgey, Sentret, Rattata at level 2. 53 areas differ between the
  versions. Species only in SoulSilver's table: Ledyba, Weedle, Kakuna, Ekans, Vulpix, Meowth, Delibird, Teddiursa, Skarmory,
  Ursaring, Arbok, Beedrill, Ledian, Gulpin, Persian, Mawile. Only in HeartGold's: Spinarak, Growlithe, Mankey, Phanpy,
  Mantine, Gligar, Sableye, Baltoy, Sandshrew, Caterpie, Metapod, Sandslash, Butterfree, Ariados, Primeape, Donphan. (Spinarak
  is HeartGold's and Ledyba SoulSilver's, as in the decomp's data.)
- A HeartGold ROM whose table was replaced by SoulSilver's boots to the vanilla intro frame. Static encounters, roamers and
  fossils are not in these tables (scripts / overlay data) and are not handled yet.

## Map headers (ARM9)

- 24 bytes each: wild encounter file (0xFF none), area data, packed world-map coordinates (u16), matrix, script, level
  script, text archive, day music, night music, event file (u16 each), location-name index (byte, into message bank 279),
  area properties (byte), 32 packed flag bits (weather, camera, battle background, ...). Layout from DSPRE's `HeaderHGSS`.
- The table is in the **decompressed** ARM9. In English HeartGold header 0 is at `0xF6BE0` (24 bytes after the offset DSPRE
  quotes, because DSPRE's image differs by one entry); there are **540** headers, 135 with encounters, all encounter ids
  below 142. **[UNVERIFIED]** for SoulSilver (assumed `0xF6BE8`).
- Packed fields (from DSPRE's `HeaderHGSS`, all 32+16+8 bits covered so a header round-trips exactly): coordinate word =
  4 unknown bits, world-map X (6), Y (6); area-properties byte = area icon (low 4) and Mom's-call intro (high 4); flag word
  = Kanto flag (bit 0), weather (7 bits), location type (4), camera (6), follower mode (2), battle background (5),
  permissions (7). Permission bits, lowest first: bicycle, running (unused), escape rope, fly, outgoing calls, incoming
  calls, radio. Confirmed on Route 29: Fly allowed, Escape Rope not; 170 maps allow the bicycle and 92 allow Fly.
  **[UNVERIFIED]**: the world-map X/Y of Route 29 decodes as (0, 0), so those two fields may not be where the player's
  position is stored for HGSS; treat them as raw.
- Writing: `MapHeaderTable.write` patches the table into the decompressed ARM9 and stores it with `RomProject.writeArm9`;
  a ROM with edited headers (ARM9 recompressed) boots to the vanilla intro frame.

## Matrices (`a/0/4/1`)

- 288 matrices. Per file: width, height, "has headers" byte, "has altitudes" byte, name length, name (UTF-8), then
  row-major grids: u16 header ids (if flagged), u8 altitudes (if flagged), u16 map-file ids (`0xFFFF` = no map). The path
  `a/0/0/9` from an earlier guess is not the matrix NARC (DSPRE's table says `a/0/4/1`).
- **The NARC re-serializes byte-for-byte; every matrix round-trips.** Matrix 0 `map` is the 47 x 17 overworld with headers
  and altitudes; the other 287 are small (218 are 1 x 1). Only two matrices have the optional sections. Every matrix id used
  by a map header exists, and every header id in the overworld grid is a real map header.

## Wiki path table (to verify per editor)

Taken from the DS Pokémon Hacking wiki "File Structure": personal `a/0/0/2`, learnsets `a/0/3/3`, evolutions `a/0/3/4`,
egg moves `a/2/2/9`, moves `a/0/1/1`, items `a/0/1/7`, scripts `a/0/1/2`, events `a/0/3/2`, encounters `a/0/3/7` (HG) and
`a/1/3/6` (SS), messages `a/0/2/7`. Trainer data and parties are `a/0/5/5` and `a/0/5/6` (not yet read).

## Item data (`a/0/1/7`)

- Records are **34 bytes** (the decomp's `ItemData` comment suggests more; the ROM has 34). 0-1 price, 2 hold effect, 3 hold param,
  4 Pluck, 5 Fling effect, 6 Fling power, 7 Natural Gift power, 8-9 packed bits (type 0-4, prevent toss 5, selectable 6,
  field pocket 7-10, battle pocket 11-15), 10 field use, 11 battle use, 12 party use, 13 pad, 14-33 party-use parameters
  (kept raw). Round trip exact over every record; Poké Ball (4) costs 200, battle use 1.

## Egg moves (`a/2/2/9`)

- NARC with one file, a flat u16 list ending in 0xFFFF: `20000 + species` opens a group, the move ids after it belong to it.
  Vanilla/hg-engine ROM: 198 groups, 4092 bytes. The game (`LoadEggMoves`) scans only the first 2045 words for a header, then
  reads moves until a word above 20000, at most 16 per species. Header index >= 2045 is never found (`EggMoves.overLimit`).

## TM/HM moves (ARM9)

- 100 u16 move ids at 0x1000CC of the **decompressed** ARM9 (same offset DSPRE uses for English HGSS): TM01-TM92, HM01-HM08.
  Verified: TM01 = Focus Punch, HM01 = Cut. Saving replaces the ARM9 on build.

## Trainer class gender (ARM9)

- One byte per class at 0xFFB90 of the decompressed ARM9: 0 male, 1 female, 2 neither (Elite Four class 89 is 2). Vanilla count is 128
  (DSPRE); there are 129 class names. Verified: Youngster 0, Lass 1.

## Trainer class prize multiplier (overlay 12)

- 129 pairs of u16 `{class id, multiplier}` at 0x34C04 of the decompressed overlay 12 (compressed in the ROM, 162464 bytes
  decompressed). Verified: Youngster 4, Rich Boy 40, Ace Trainer 15. Rows are not in class order: row 128 is class 30 (Bird
  Keeper, 8), appended at the end; edit by row.

## Overlay edits

- `RomProject.writeOverlay(id, plain)` takes the decompressed image (same size as the original). A build recompresses it
  (BLZ) when the table entry is flagged compressed, replaces the overlay's FAT file and rewrites the table's size word
  (low 24 bits of the last word of the 32-byte entry). Projects keep `overlays/<id>.bin`. Round trip verified on overlay 12.

## Message commands

- A command inside a message is `0xFFFE`, a command word, an argument count n, then n argument words. Every command in the ROM
  is complete (none truncated); 14 distinct words: `0100/0300/0400/3400` + low byte = string variable (`STRVAR_1/3/4/34`, the
  low byte is the first value, 2 args), `0200 YESNO`(1), `0201 PAUSE`(1), `0202 WAIT`(1), `0203 CURSOR_X`(1), `0204 CURSOR_Y`(1),
  `0205 ALN_CENTER`(0), `0206 ALN_RIGHT`(0), `FF00 COLOR`(1), `FF01 SIZE`(1), `FF02`(2). Written `{NAME args}` by `MessageText`.
  Packed (9-bit) messages cannot hold a command or a code above 0x1FE, so the editor refuses them there.

## Event files (`a/0/3/2`)

- One file per event set (491 in this ROM, 7 empty), picked by `MapHeader.eventFile`. Four lists in this order, each `u32 count`
  then fixed-size records: spawnables (20 bytes: script, type 0 misc/1 board/2 hidden item, x, unk, y, s32 z, unk, direction,
  unk), overworld objects (32: id, sprite entry, movement, type, flag, script, facing, sight range, unk, unk, x range, y range,
  x, y, s32 z; script 0xFFFF = alias), warps (12: x, y, target header, anchor, u32 height), triggers (16: script, x, y, width,
  height, z, expected value, variable). No bytes follow the last record in vanilla; round trip exact over all files.
- x/y are positions on the whole matrix, 32 tiles per cell (`x / 32` is the cell column). Warps may target header 4095 (0x0FFF),
  which is not a map header. Overworld types seen: 0 normal (2260), 1 trainer (403), 4 (3), 7 (1); none of type 3.

## Script files (`a/0/1/2`)

- 965 files. A script file starts with one u32 per script (offset relative to the end of that u32), then the u16 `0xFD13`;
  then the code of every script and function, movement lists among it, zero padding. 492 files parse; 468 have no `0xFD13`
  header and are **level scripts** (see below; map headers index both kinds); 3 are empty; 2 stay raw
  (one has the `0xFD13` marker twice, one has a movement list at an odd offset).
- Command = u16 opcode + parameters whose sizes come from the command table (`scripts/gen-scrcmd.py`, from the MIT-licensed
  scrcmd-database; 853 commands). Variable-length: `0x190-0x192` (u8 mode, u16 more if mode 2), `0x1D1` and `0x1E9`
  (u16 mode picks 0-2 more u16). Jumps hold an i32 relative to the end of the parameter: `0x16 GoTo`, `0x1A Call` (param 0),
  `0x17/0x18/0x19/0x1C/0x1D` (param 1, after a u8), `0x5E ApplyMovement` (param 1, after a u16, goes to a movement list).
  `0x02 End`, `0x16 GoTo` and `0x1B Return` end a chunk of code.
- A movement list is `(u16 id, u16 count)` pairs ending with id `0xFE` (4 bytes, count 0). Lists sit among the code and must
  be halfword aligned (a zero byte pads before them). Some lists, and some code (an unconditional `GoTo` after another), are
  referenced by nothing; the parser finds them by following jumps first, then walking the body in order, guessing that a
  movement list starts where code cannot. Whatever it guesses must write back to the same bytes, or the file stays raw.
- Because scripts share code and fall through into each other, the model is one flat list of items (labels, commands,
  movement steps, padding) in file order, not a list of scripts. Text form: `script_N:` / `function_N:` / `action_N:` labels,
  `Name arg, arg` lines, `.pad N`; see `ScriptText`.

## Level scripts (files of `a/0/1/2` without the 0xFD13 header)

- Entries `u8 type, u32 value`, type 2 map change, 3 screen reset, 4 game load: value = script number of the map's own script
  file (header.script). Type 1 marks variable triggers: its value is 5 * (number of entries after it) + 1, i.e. it points past the
  `00` that ends the list; it can sit anywhere among the entries (468 files: 2 types, marker first, middle or last).
  The list ends with a single `00`. If a type 1 entry exists, `u16 variable, u16 value, u16 script` triples follow, ended by `u16 0`.
  Zero bytes pad the file to 4 bytes. All 468 files round trip; 3 of the script NARC's 5 raw files are empty ones.

## Map files (`a/0/6/5`)

- 676 files. Header: four u32 section sizes (permissions always 2048, buildings, NSBMD model, BDHC), then the background sound
  block (`0x1234`, u16 length, data), the permissions, the buildings, the model, the BDHC. Sizes add up to the file size in every
  vanilla file; round trip exact.
- Permissions: 1024 pairs `u8 type, u8 collision`, **row-major**: tile (x, y) is at `(y * 32 + x) * 2`. Verified: 2568 of 2657 NPCs
  stand on collision-free tiles with this order, 1629 with x/y swapped; warps sit on the warp/door tile types only with this order.
  Type = the game's tile behaviour (`TILE_BEHAVIOR_*`, names generated into `TileTypeData.kt`: 2 tall grass, 16 river water,
  17 whirlpool, ...); collision bit 0x80 blocks, the other bits (0-13 seen) are further flags (4 = grass rustle per the wiki).
- Building (48 bytes): u32 model id, three fixed point coordinates `u16 fraction, s16 whole` (in file order x, y, z), three u32
  rotations, one unused byte, u32 scales x3 (all 16 in vanilla), 7 unused bytes. y is ~1 everywhere, so the plan position is x, z.
  **[UNVERIFIED]**: the origin is the middle of the map, so the tile is (x + 16, z + 16): with it a building is on average 6.8
  tiles from the nearest warp, against 24 without the offset; x and z swapped scores 7.3, so the axes are not proven.
- A map file belongs to a matrix cell (`a/0/4/1`): outdoor matrices store a header per cell, indoor ones use the header that
  owns the matrix; the event file of that header holds the cell's events at matrix coordinates (32 tiles per cell).

## Icons

- NCLR ("RLCN") holds BGR555 colours (`PLTT` section: data at section + 0x18), NCGR ("RGCN") the 8x8 tiles (`CHAR` section:
  height/width in tiles, pixel format 3 = 4 bits, 4 = 8 bits, data at section + 0x20); width -1 means "not stated" (icons are 4 tiles
  wide). Colour 0 is transparent.
- Pokémon icons `a/0/2/0`: file 0 is the NCLR (16 palettes of 16), file `species + 7` is a 32x64 NCGR holding two animation frames
  (the top one is used). The palette index (0-2) per species is code data in the ARM9 (`sPokemonPalNoBySpeciesAndForm` in the
  decomp), generated into `IconData.kt` by `scripts/gen-icons.py`; forms and eggs use extra slots (501+) not decoded yet.
- Item icons `a/0/1/8`: per item an NCGR (32x32, 4 bits) and an NCLR (first 16 colours used); which files belong to which item is
  the decomp's `sItemNarcIds` (item 1 Master Ball = files 2 and 3), generated by the same script. 537 items, all with icons.

## Sound archive (`data/sound/gs_sound_data.sdat`)

- Not a NARC: a plain file of the ROM, "SDAT" with blocks SYMB (names), INFO (records), FAT (file offsets) and FILE. 2379 sequence
  slots (1372 with data, names like `SEQ_SE_DP_SELECT`, `SEQ_GS_TITLE`), 561 banks, wave archives. The sequence id used by the
  game and by scripts (`PlaySE 1500`) is the index in the sequence list; `include/constants/sndseq.h` of the decomp agrees.
- INFO records are 12 bytes. Sequence: u32 file, u16 bank, u8 volume, 3 more bytes. Bank: u32 file, 4 x u16 wave archive (0xFFFF none).
  Wave archive: u32 file. SYMB lists give the name offsets per kind (sequence 0, bank 2, wave archive 3).
- SSEQ bytecode starts at the offset in the file header (0x1C): notes `0-0x7F velocity length`, rest 0x80, program 0x81, open track
  0x93, jump 0x94, call 0x95, tempo 0xE1 (48 ticks per beat), volume 0xC1, pan 0xC0, expression 0xD5, loop 0xD4/0xFC, end 0xFF.
- SBNK instruments: types 1 (sample), 2 (pulse), 3 (noise), 16 (one region per key), 17 (regions by key range), regions carry wave id,
  wave archive slot, base note and attack/decay/sustain/release. SWAR holds waves (PCM8, PCM16, IMA ADPCM).
- **Cries**: sequence 1 (`SEQ_PV001`, a one-note template with note length 0) played with bank = species number; each bank
  `BANK_PV<n>` uses wave archive n with one wave (Bulbasaur: 8184 samples at 10512 Hz, 0.78 s). Length 0 therefore means "play the
  sample to its end". Species 1-493 all have one.

## Script symbols

- Flags run 0..16447 and variables 0x4000.. (0x8000+ are the special ones, `VAR_SPECIAL_RESULT` = 32780); names come from the
  script command database (synced from the decomp), generated into `ScriptData.kt` by `scripts/gen-scrcmd.py`. Several names can
  share an id (aliases such as `MAPTEMP_FLAG_BASE`); the `FLAG_`/`VAR_` one is shown.
- Flag parameters are known by command (`SetFlag`, `ClearFlag`, `CheckFlag` take a flag number); a value of 0x4000 or more in any
  parameter is a variable. Message parameters are the ones named `text_slot`, `message`, ... in the database; the message is
  read from the text bank in the `textArchive` field of the first map header that uses the script file.
