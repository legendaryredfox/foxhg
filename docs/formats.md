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
- Flags byte (bit 0 first): contact, protect, magic coat, snatch, mirror move, King's Rock, keep HP bar (animation), hide shadow
  (animation). Range u16 is one flag bit: 0 one adjacent opponent or ally, 1 varies, 2 one random opponent, 4 all adjacent
  opponents, 8 all adjacent Pokémon, 16 user, 32 user side, 64 every side, 128 opponent side, 256 one ally, 512 user or ally,
  1024 one opponent. Contest type: 0 cool, 1 beauty, 2 cute, 3 smart, 4 tough (Super Contest, Diamond/Pearl/Platinum). Names as in
  DSPRE's `MoveData` and the move editing guide of the DS Pokémon Hacking wiki, `CONTEST_TYPE` values as in the decomp
  (`constants/pokemon.h`). **[UNVERIFIED]** in game: only the byte layout is checked against the ROM.

## Rock Smash (`a/2/5/3`, overlay 1)

- `a/2/5/3`: 540 files of 4 bytes, member N = map header N: drop chance u16 (percent, 0 to 100, 0 turns drops off) and table type u16
  (0 default, 1 Ruins of Alph, 2 Cliff Cave). Vanilla: 538 default, one Ruins of Alph, one Cliff Cave; chances seen 0, 10, 15, 20, 25, 30, 50.
  The item is only rolled when the wild encounter roll fails (encounter rate byte of the encounter file).
- Overlay 1 (decompressed) holds the three item tables of 8 u16 items back to back: Ruins of Alph 0x23D04, default 0x23D14, Cliff Cave 0x23D24;
  the roll picks slot 0 to 7 with 25, 20, 10, 10, 10, 10, 10, 5 percent (Serene Grace and Super Luck shift it one slot). HeartGold values read from
  the ROM match the wiki guide (default: Max Ether, Revive, Heart Scale, four shards, Star Piece); SoulSilver's Ruins and Cliff Cave tables hold other
  shards and fossils. Layout from the DS Pokémon Hacking wiki's Rock Smash guide and DSPRE's `RockSmashData`; `RealRomRecordsTest`.

## Headbutt (`a/2/5/2`)

- `a/2/5/2`: one file per map header (540). Maps without trees are the 4-byte stub `00 00 00 00` (no encounters stored); the others are
  `4 + 72 + 24 * (normal groups + special groups)` bytes: u8 normal group count, pad, u8 special group count, pad; 12 normal encounters (the
  common table of 6, then the rare table of 6) and 6 special ones (u16 species, u8 min level, u8 max level); then the tree groups, normal first,
  each 6 tiles of u16 x, u16 y in map-wide coordinates (matrix cell x 32 + tile), unused tiles 65535 x 65535. A table slot is picked with
  50, 15, 15, 10, 5, 5 percent. Normal groups use table A or B depending on the Trainer ID's last digit and the group's index, special groups
  always table C. A tree only works on a tile of type 6 that is blocked. All files of the ROM parse and write back byte for byte, and no
  vanilla group is empty (`RealRomRecordsTest`). Layout from the DS Pokémon Hacking wiki's Headbutt guide and DSPRE's `HeadbuttEncounterFile`.
  **[UNVERIFIED]** in game: only the layout is checked against the ROM.

## Save file (HeartGold and SoulSilver `.sav`)

- 512 KB (0x80000) of flash, as the game's `save.c` lays it out and as confirmed on the two saves made by the emulator tests (`RealSaveTest`, `SaveFileTest`): two copies (slots) of the save, at file offsets 0 and 0x40000; the game writes the other slot each time and keeps a counter, so one is always whole. A new game's first save goes to slot 1 (counter 1) and slot 0 stays all 0xFF.
- A slot holds two blocks: the **general** block at +0x0000, 0xF628 bytes (the 41 chunks of `SAVE_SYSINFO` to `SAVE_TRAINER_HOUSE`: player, party, bag, flags, position, Pokédex, ...), and the **storage** block (the PC boxes) at +0xF700 (the next multiple of 0x100), 0x12310 bytes. Each block ends with a 16 byte footer: u32 save counter, u32 block size, u32 magic `0x20060623` (`SAVE_CHUNK_MAGIC`), u16 block number (0 general, 1 storage), u16 CRC. The CRC is **CRC-16/CCITT-FALSE** (polynomial 0x1021, start value 0xFFFF, most significant bit first; the decomp's `GF_CalcCRC16` is NitroSDK's `MATH_CalcCRC16CCITT`) over the block without its footer (check value of "123456789": 0x29B1). The game loads each block from the sound slot (footer size, magic, block number and CRC right) and, when both are sound, from the one with the higher counter, 0xFFFFFFFF counting as older than 0 (`SaveCounterCompare`).
- Chunk table (`gSaveChunkHeaders`, ARM9 0xF64C4 in the English HeartGold, 42 entries of 16 bytes: u32 id, u32 block, u32 pointer to the size function, u32 pointer to the init function): the chunks follow one another in the block, each taking its `*_sizeof` rounded up to 4 bytes plus 4 (where a CRC-16 of the chunk goes, kept only by some chunks), 41 chunks in the general block and `SAVE_PCSTORAGE` alone in the storage block. The sizes were read from the ARM9 by decoding each size function (a THUMB `mov`, `lsl`, `ldr pc` and `bx lr`), and they are exactly right: they add up to 0xF618 and 0x12300 bytes of data, the block sizes minus the 16 byte footer, and six chunks keep their own CRC (CRC-16/CCITT-FALSE over the rounded-up data, stored right after it as a u16) where these offsets put it on the real save. Sizes (hex): SYSINFO 5C, PLAYERDATA 2C, PARTY 5B0, BAG 79C, FLAGS 44C, LOCAL_FIELD_DATA 80, POKEDEX 340, DAYCARE 1E0, PALPAD 880, MISC 2E0, MAP_OBJECTS 1400, LINK_BATTLE_RULESET 20, DRESSUP_DATA 834, MAILBOX 460, FRIEND_GROUP 108, TRAINER_CARD 620, GAMESTATS 1C0, SEAL_CASE 170, CHATOT 3EC, UNK_19 (the Battle Frontier) 1628, SPECIAL_RIBBONS E, ROAMER 68, UNK_22 F8, UNK_23 BC8, RANKINGS EA0, UNK_25 8C0, WIFI_HISTORY FF8, MYSTERY_GIFT 1680, UNK_28 688, POKEATHLON_FRIENDSHIP_RECORDS 28, EASY_CHAT 8, UNK_31 40, UNK_32 8, FOLLOW_MON 8, POKEGEAR 658, SAFARI_ZONE 5FC, PHOTO_ALBUM 1294, POKEATHLON B80, APRICORN_BOX 80, POKEWALKER 134, TRAINER_HOUSE F00, PCSTORAGE 122FC. The chunks that keep their own CRC: 23, 24, 26, 27, 30, 31 (WIFI_HISTORY, MYSTERY_GIFT and EASY_CHAT are those the decomp updates). The fields inside each chunk are **[UNVERIFIED]** until its editor checks them against a real save. In the fresh save the player data chunk (offset 0x60 of the general block) has the name as Gen IV characters and the other player fields, the system chunk starts with the game's identification: both not decoded yet.
- **Player data** (`PLAYERDATA`, chunk 1, 0x2C bytes, at 0x60 of the general block; decomp `player_data.h`, `options.h`, `igt.h`; decoded and checked on the real saves and in the game): u16 options word (text speed bits 0-3, sound 4-5, battle style 6, battle scene 7, button mode 8-9, frame 10-14), u16 padding, the name as 8 u16 text codes (7 characters then 0xFFFF; the rest is zeros as `PlayerProfile_Init` clears), u32 trainer ID, u32 money (the shop screens showed 1500 and 2750 as the saves said), u8 gender (0 boy), u8 language (2 English), u8 Johto badges, u8 avatar (the sprite: 0x1F in the Cherrygrove save, 0x46 in the Viridian one, set by the game, not decoded), u8 version (7 HeartGold), u8 flags (bit 0 game cleared, bit 1 National Dex), u8 dummy, u8 Kanto badges, u16 coins, u16 hours, u8 minutes, u8 seconds (0:01:10 in the saves, the Continue screen showed 0:01), u16 padding. A save with the name FOXHG, 12:34:05 and 3 badges written by foxhg loads in the vanilla ROM and the Continue screen shows exactly that (`EditedSaveInGameEmulatorTest`); once Continue is chosen the profile is in RAM (name, ID at +0x10, money at +0x14, Johto badges at +0x1A, Kanto at +0x1F from the name). The badge order (Zephyr, Hive, Plain, Fog, Storm, Mineral, Glacier, Rising; Boulder, Cascade, Thunder, Rainbow, Soul, Marsh, Volcano, Earth) is **[UNVERIFIED]**, only the count was seen.
- **Field data** (`LOCAL_FIELD_DATA`, chunk 5, 0x80 bytes, at 0x1234 of the general block; decomp `save_local_field_data.c`; read on both real saves): five `Location`s of five 32-bit words each (map header number, warp number or -1, tile x, tile y, direction 0 north, 1 south, 2 west, 3 east): the current position, the entrance, the previous position, the dynamic warp and the special spawn; then u16 music, u16 weather, u16 last spawn, u8 camera type (4), a byte of padding, the player's running shoes u16, lock u16 and state i32, u16 poison steps, u16 Safari steps, u16 Safari balls and 6 filler bytes. The two real saves hold (map 68, -1, 3, 6, west) and (map 500, -1, 3, 4, west), the Poke Marts they were made in, as both the current and the previous position.
- **Flags and variables** (`FLAGS`, chunk 4, 0x44C bytes): 0x170 u16 script variables (variable 0x4000 + n is entry n), then the flag bitmap of 2912 flags (flag n is bit n % 8 of byte n / 8 of the bitmap, so byte 0x2E0 + n / 8 of the chunk; flag 0 is never set). Flag 0x966 (`FLAG_UNK_966`, tested by `CheckFlag966`) changes how Continue starts (`FieldTask_ContinueGame_Normal`): with it set the game takes the dynamic warp as the position, clears the flag and builds the map's objects from the map's events; without it it restores the player and the NPCs from the saved `MAP_OBJECTS` chunk. **Moving a save**: changing the current position alone does not move the player (the objects of the old map say where he stands: he stood at the old tile next to the old clerk), and clearing the objects chunk leaves a black screen (Continue restores the player from it); setting the current, previous and dynamic warp positions and flag 0x966 works: the Cherrygrove save, moved by foxhg to Viridian's Poke Mart (map 500, tile 3, 4, west), continued next to Viridian's special clerk and showed special mart 26 (`MovedSaveInGameEmulatorTest`). The dynamic warp stays at the new place afterwards. The tile must be one the player can stand on: the game does not check. Read on both real saves: the four flags 0x11B to 0x11E the Poke Mart start ROM's init script sets are set, plus about a hundred in 0x194 to 0x2C8 and beyond that the game sets itself (map and object state); writing the chunk back unchanged changes no byte, and a variable written as a little-endian u16 at 2 * n reads back (`RealSaveTest`).
- DeSmuME's `.dsv` is the 512 KB followed by a 122 byte footer ending `|-DESMUME SAVE-|`; `SaveFile` reads both and writes the footer back. Other blocks of the 512 KB (Hall of Fame and the Battle Frontier extra chunks, the first save slot's unused space) are kept as they are.
- `core/.../save/SaveFile.kt` and `SaveChunks.kt`: parse, the blocks with their footers checked, the block the game would load, the 42 chunks with their offsets, and `withChunk` / `withData` / `withFixedChecksums` that rewrite a chunk (and its own CRC where it has one) and the block CRC. Nothing else writes a save yet.
- Party (`SAVE_PARTY`, chunk 2, 0x5B0 bytes; from the decomp's `PartyCore` and `Pokemon`, confirmed on `party.dsv` made by the emulator): u32 capacity (6), u32 count, six 0xEC byte Pokémon, 0x20 bytes after them that the game keeps (kept as they are). A Pokémon is a **box part** of 0x88 bytes and a **party part** of 100 bytes. Box part: u32 personality value, u16 flags (the decomp names bit 0 the party lock, bit 1 the box lock and bit 2 the checksum-fail flag: **[UNVERIFIED]** here, kept as they are), u16 checksum, then four 32 byte blocks A, B, C, D, **shuffled** by `((personality >> 13) & 31) % 24` (the 24 orders of ABCD, in lexicographic order) and **encrypted**: each u16 is XORed with the high half of a linear congruential generator, `seed = seed * 0x41C64E6D + 0x6073`, seeded with the checksum. The checksum is the sum of the 64 u16 of the decrypted blocks, in 16 bits (a slot whose checksum does not fit is refused by foxhg; what the game does with one is **[UNVERIFIED]**). The party part is encrypted the same way, seeded with the personality value, and not shuffled. Block A: u16 species, u16 held item, u32 OT id, u32 experience, u8 friendship, u8 ability, u8 markings, u8 language, six u8 EVs (HP, Attack, Defense, Speed, Sp. Atk, Sp. Def), six u8 contest stats. Block B: four u16 moves, four u8 PP, four u8 PP-ups, u32 packed IVs (5 bits each in the EV order, bit 30 egg, bit 31 has nickname), the ribbons (not decoded), the forme (bits 3 and up of the byte at 0x38) and gender (bits 1 and 2). Block C: the nickname (11 u16 in the game's text table, ended by 0xFFFF). Block D: the OT name (8 u16) and the dates and places. Party part: u32 status, u8 level, u8 capsule, u16 current HP, u16 max HP, then Attack, Defense, Speed, Sp. Atk, Sp. Def (u16 each), the rest unused by foxhg and kept. Empty slots are written by the game encrypted too (they decrypt to zeros), never as plain zeros. The nature is `personality % 25`. The stored stats are the game's formula with the nature and the species' base stats: `(2 * base + IV + EV / 4) * level / 100 + 5` (HP: `+ level + 10`), times 110% or 90% for the raised and lowered stat; the stored values of the three Pokémon of `party.dsv` match this (checked in `RealSaveTest`). The game does not recompute stored stats when a save loads, so an edited level or IV needs the stats written too (the party editor does this when a ROM is open).
- In the game: a save written by foxhg with an edited party (a nickname, Pikachu at level 50 with 125000 experience and 120 HP) shows exactly that on the party screen and the party in RAM is the bytes foxhg wrote (`EditedPartyInGameEmulatorTest`). The start menu shows **POKéMON** only when `FLAG_GOT_STARTER` (flag 0x6A) is set; the new game of the test ROM has it unset because the intro never gave a starter (the test sets the flag in the save). Flag 0x6B (`FLAG_GOT_POKEDEX`) is the same kind of switch, the Pokédex entry; not tried.
- Bag (`SAVE_BAG`, chunk 3, 0x79C bytes; decomp `bag_types_def.h`, `constants/items.h`; read on `cherrygrove.dsv` and `viridian.dsv`): 486 slots of 4 bytes (u16 item id, u16 quantity), then two u16 registered items (the touch screen buttons), 486 * 4 + 4 = 0x79C. The pockets follow in the save in this order, with their capacities: items 165, key items 50, TMs and HMs 101, mail 12, medicine 40, berries 64, Poke Balls 24, battle items 30. This is not the order of the field pocket number in the item data (items 0, medicine 1, balls 2, TMs 3, berries 4, mail 5, battle items 6, key items 7). Used slots come first, then `0, 0`; the quantity is at most 999, 99 for TMs and HMs. The two real saves agree: the Cherrygrove one has Potion (17) x5 in the medicine slots, the Viridian one item 142 x5 in the mail slots, nothing else. Item ids run 1 to 536 (`ITEMS_COUNT` 537). Written back it is byte-exact (`RealSaveTest`).
- PC storage (`SAVE_PCSTORAGE`, chunk 41, 0x122FC bytes, alone in the storage block; decomp `pokemon_storage_system.h`, read on the real saves): 18 boxes of 0x1000 bytes (30 Pokemon of 0x88 bytes, then 16 bytes the game keeps), then at 0x12000 the open box (u32), at 0x12004 a modified-boxes bitmask (u32), at 0x12008 the 18 box names (20 u16 text codes each, ending 0xFFFF; the game allows 8 characters, `BOX 1` to `BOX 18` at the start), at 0x122D8 the 18 wallpaper bytes, at 0x122EA the unlocked bonus wallpapers bitmask and 0x11 filler bytes. A boxed Pokemon is the party Pokemon without its 100 byte party part: the same personality value, flags, checksum and four encrypted, shuffled blocks (0x88 bytes), so a box slot is the first 0x88 bytes of `Pokemon.toBytes()`; level, HP and stats are not stored and the game recalculates them when the Pokemon joins the party. An empty slot is the encrypted zeros of `Pokemon.EMPTY`, not zero bytes (the first u16 of the blocks is 0 because the cipher's first key word is 0, the rest is the cipher stream). Wallpaper ids: 0 to 15 (Forest, City, Desert, Savanna, Crag, Volcano, Snow, Cave, Beach, Seafloor, River, Sky, Poke Center, Machine, Checks, Simple) and the bonus 32 to 39 (Heart, Soul, Big Brother, Pokeathlon, Trio, Spiky-eared Pichu, Kimono Girl, Revival); new games give box n the wallpaper n mod 16. Byte-exact on both real saves, and a PC written by foxhg (a Pikachu in box 6 slot 8, box 6 renamed) is held by the game in RAM as written (`EditedPcInGameEmulatorTest`).
- Pokédex (`SAVE_POKEDEX`, chunk 6, 0x340 bytes; decomp `pokedex.h` and `pokedex.c`): u32 magic 0xBEEFCAFE at 0, caught bitmap at 4 and seen bitmap at 0x44 (64 bytes each, bit species - 1, so 493 species; a species counts as caught only with both bits), seen genders at 0x84 and 0xC4 (64 bytes each, two bitmaps), u32 Spinda personality at 0x104, Shellos, Gastrodon, Burmy and Wormadam form orders at 0x108 to 0x10B, Unown seen order at 0x10C and caught order at 0x128 (28 bytes each), the caught languages at 0x144 (496 bytes, one byte per species), then `canDetectForms` 0x334, `enabledInternational` 0x335, `dexEnabled` 0x336, `nationalDex` 0x337, Rotom form order u32 at 0x338, Shaymin, Giratina and Pichu form orders at 0x33C to 0x33E. The init (`Save_Pokedex_Init`) writes the magic and 0xFF into every form order and Unown order, and the last byte of the caught and seen bitmaps is 0xFF (the eight flags past the species list): this is byte for byte the Pokedex of the real new-game saves (`RealSaveTest`).
- New save (`NewSave.create`): a save made without the game loads in the vanilla English HeartGold. What it writes: slot 1 only, counter 1, slot 0 left 0xFF; every chunk zero except PLAYERDATA (name, gender, language 2, version 7, money, ID), PARTY (6 slots of encrypted empty Pokemon, capacity 6, count 0), LOCAL_FIELD_DATA (current, entrance, previous and dynamic warp all the chosen place, warp -1, running shoes flag 1), FLAGS (0x11B bag, 0x11C trainer card, 0x11D save button, 0x11E options button so the start menu has them, and 0x966 so Continue takes the dynamic warp) and PCSTORAGE (empty slots, `BOX 1` to `BOX 18`, wallpapers n mod 16). Everything else left at zero (SYSINFO, Pokedex, map objects, daycare, mail, the chunks with 0xFF fills such as RANKINGS and TRAINER_HOUSE, and the ones that hold encrypted empty Pokemon) does not stop the game: Continue shows the Cherrygrove Poke Mart with 3000 money and the badge clerk's own list (`NewSaveInGameEmulatorTest`). Not checked: playing on from such a save beyond the mart (walking out, saving again), and the Pokedex and the real random seeds the game's own init writes.

## Emulator tests (DeSmuME libretro core)

- `EmulatorDriver` (test sources of `core`, `libretro-kmp` 0.1.2 from Maven Central, Apache-2.0, a `jvmTest` dependency only) runs a ROM on the DeSmuME core (Debian's `desmume_libretro.so`, or `DESMUME_CORE`): runs frames, holds buttons, writes a picture of both screens (256 x 384) and reads the 4 MB main RAM by DS address. **The core ignores the libretro pointer (touch) device in this build**: the answers are read (the core polls them) but a touch never reaches the game, so everything is done with the buttons (the info screens, the menus and Save all take the D-pad and A). A libretro core cannot be loaded twice in one process, so the Gradle task `emulatorTest` gives every test class its own JVM, and `emulatorSetup` makes the saved games first. The core exposes no `SAVE_RAM` through `readMemory`: it writes the in-game save into its save directory when it closes.
- Saves made by playing the new game with the buttons on a ROM whose new game starts in a Poke Mart (`MartStartRom`: the start location `sLocation_PlayerRoom`, found in the ARM9 by its 20 bytes, is patched to the map and tile; the init script, std script 9600 = file 149, sets the bag, trainer card, save and options flags so the start menu has Save): title (`START`), the info screens ("No info needed" is the third entry), A pressed through the Professor's speech (the name is left at the default `AAAAAAA`, the player is a boy), then the clerk's menu, the start menu (cursor starts on Bag: Right, Up is Save) and Save. Cherrygrove's Poke Mart is map 68 (the clerk of the badge mart at (2, 6), player at (3, 6) facing west), Viridian's is map 500 (the second clerk at (2, 4) sells special mart 26, player at (3, 4)). Saving takes about 2000 frames ("Saving a lot of data...").
- The save file is DeSmuME's `.dsv`: 524288 bytes of raw save memory followed by a 122 byte footer, the text `|<--Snip above here to create a raw sav by excluding this DeSmuME savedata footer:` and a few numbers (the save size 0x80000, the type, ...) and `|-DESMUME SAVE-|`. Cut the footer to get a raw `.sav`. The save of a fresh game (just after "Saving a lot of data...") has 130719 bytes that are not 0xFF, and the chunk footer magic `0x20060623` (`SAVE_CHUNK_MAGIC`) at 0x4F620 and 0x61A08. A save made on the patched ROM loads in the vanilla ROM ("Continue" shows `AAAAAAA`, 0:01, 0 badges; Continue puts the player next to the clerk), because the patch only sets the starting position and flags. The save is kept in `core/build/emulator-saves/` and never committed.

## Poké Marts (ARM9)

- In the decompressed ARM9 (RAM address minus 0x02000000), HeartGold English: a table of 30 u32 pointers at 0x10FA3C, one per `SpMartScreen n` (script command), each pointing to a list of u16 item numbers ended by 0xFFFF (vanilla: mart 0 is Air Mail and Heal Ball at 0xFBA54, mart 3 starts with Potion, mart 28 is TinyMushroom, Poké Ball, Potion). The 30 lists sit one after another in 0xFBA54 to about 0xFBC80, so a list keeps its place while it has at most its vanilla item count. A longer one is written to the synthetic overlay (needs the ARM9 expansion) in a block foxhg reserves at synthetic offset 0x4000, 0x1000 bytes (runtime 0x023CC000), lists laid out one after another in mart order, and its pointer in the table is set to that address; a list back at its old size returns to its place and the pointer to the original, and the block is zeroed first, so a fully shrunk table restores the ARM9 and the overlay byte for byte (`RealRomRecordsTest`). The block lies after the script command table copy (0x200) and before the camera routine (0x115B0), and foxhg refuses to grow marts when something else is in it. The game counts a mart's items in a loop of at most 256 and keeps the count in a byte, so at most 255 items. The shop code (`ov03_02256C2C`) skips item 4 (Poké Ball) when a flag is off; which marts that concerns is not studied, so the editor adds Potion by default. The standard clerk (`MartBuy`) uses a table of 19 entries of u16 item and u16 tier at 0xFBF22 and sells every entry whose tier is at or below the player's: 0 badges is tier 1, 1 to 2 tier 2, 3 to 4 tier 3, 5 to 6 tier 4, 7 tier 5, 8 or more tier 6. Prices are not stored in marts but in the first two bytes of each item record (`a/0/1/7`). Not covered yet: the apricorn marts (14 lists of u16 item and u16 price at 0xFBC82 and after, pointers at 0x10FA04), the Pokéathlon data card marts (pointers at 0x10F9D4) and the seal and decoration marts. Addresses from the decomp's `scrcmd_mart.c` and the wiki guide "Adding New Poké Marts"; all 30 lists and the badge table were read from the ROM and match the decomp, and an unedited table writes back the same ARM9 (`RealRomRecordsTest`). Boot test (2026-10-16, RetroArch with DeSmuME, 2400 frames, `scripts/boot-test.sh`, fixtures `BootFixtures.movedMarts` and `laterEditors`): a ROM with the ARM9 expansion and marts 0 and 28 moved into the synthetic overlay, and a ROM with edits in the type chart, Pickup, growth rates, battle display, Battle Tower sets, trainer flags and the marts, both reach the same intro frame as the vanilla ROM, byte for byte. That shows the expansion loader maps the overlay at start-up and nothing edited breaks the boot; it does not open a mart. **Verified in the running game** (2026-10-16, DeSmuME libretro core driven by `libretro-kmp`, tests in `core/src/jvmTest/.../emulator/`, see "Emulator tests" below): the standard clerk's badge list with Antidote replaced by Rare Candy (price 1234 from the item data) and Parlyz Heal moved to tier 6 shows Potion and Rare Candy only; special mart 26 shows its vanilla list (Steel Mail 50, Net Ball 1000, Heal Ball 300); and mart 26 grown to six items (moved into the synthetic overlay) shows all six with the right prices (Rare Candy 4800, Net Ball, Heal Ball, Potion 300, Super Potion 700, Hyper Potion 1200). The shop copies the clerk's list into RAM when the buy list opens, which the test finds as the u16 sequence. The shop code hides the Poke Ball (item 4) in these lists, as the decomp's `ov03_02256C2C` says.

## Battle Tower (`a/2/0/2`, `a/2/0/3`, text banks 27 and 724)

- `a/2/0/2`: 315 trainer files (the wiki calls it `pl_btdtr`, "same as Platinum"): u16 trainer class, u16 set count n, n u16 set numbers, zero padded to a multiple of 4 bytes (4 to 204 bytes; trainers 0 to 4 and most others list 50). `a/2/0/3` (`pl_btdpm`): 951 files of 16 bytes, one per Pokémon set (set 0 is all zeros, sets 1 to 950 are used): u16 species, 4 u16 moves, u8 EV flags (bit 0 HP, 1 Attack, 2 Defense, 3 Speed, 4 Sp. Atk, 5 Sp. Def: the stats that get maximum EVs), u8 nature (0 to 24, the usual order from Hardy), u16 held item, u16 form. Text: bank 27 entry n is the name of trainer n (315 entries), bank 724 entries 3n to 3n + 2 are its three messages (945 entries). Every set number of every trainer exists and every species, move and nature of the sets is valid; both NARCs write back byte for byte (`RealRomRecordsTest`). Layout from DSPRE's `BattleTowerTrainerFile` and `BattleTowerPokemonSetFile` (US text bank numbers from `RomInfo`). **[UNVERIFIED]** in game: how the game picks from the list and which trainers each Tower round uses are not checked.

## Trainer battle flags (header of `a/0/5/5`)

- Bytes 12 to 15 of the 20-byte trainer header are the AI flags (u32): bit 0 Basic, 1 Evaluate Attack, 2 Expert, 3 Setup, 4 Risky, 5 Prioritize Extremes, 6 Baton Pass, 7 Tag Strategy, 8 Check HP, 9 Weather, 10 Harassment (names from DSPRE's `TrainerFlagBulkEditor`; the decomp only calls the field `TRATTR_AIFLAGS`). Bytes 16 to 19 are the battle type (u32): 2 for a double battle, 0 for a single one; all 738 vanilla trainers use one of the two, 25 are doubles. In the ROM bits 0 to 9 appear in some trainer, 737 trainers have Basic and 288 Expert. Editing one flag keeps every other bit; the party format bits (byte 0) are not flags and are not touched (`RealRomRecordsTest`). **[UNVERIFIED]** in game.

## Growth rates (`a/0/0/3`)

- A NARC of 8 members of 404 bytes: 101 u32 values, the total experience needed at levels 0 to 100 (levels 0 and 1 are 0), one member per growth rate in the personal data's order: Medium Fast, Erratic, Fluctuating, Medium Slow, Fast, Slow, then two unused ones equal to Medium Fast. The game looks levels up in these tables (decomp `LoadGrowthTable`). Level 100 totals in the ROM: 1000000, 600000, 1640000, 1059860, 800000, 1250000; Medium Fast at level 50 is 125000. Editing a level means moving every later one by the same amount (wiki guide). All eight round-trip byte for byte (`RealRomRecordsTest`). **[UNVERIFIED]** in game.

## Type chart (overlay 12)

- In the decompressed overlay 12, HeartGold English: the effectiveness table at 0x353BC, 112 slots of 3 bytes (u8 attacker type, u8 defender type, u8 multiplier times 10: 0, 5 or 20 only, neutral pairs are not listed). Slots 0 to 107 are the main entries, slot 108 is the divider `FE FE 00` (the decomp's `TYPE_FORESIGHT`), slots 109 and 110 are Normal and Fighting against Ghost (0), slot 111 is the terminator `FF FF 00` (`TYPE_ENDTABLE`). The battle code reads every entry whose attacker is the move's type, for each of the target's types; when the target is under Foresight or Odd-Eye, or the attacker has Scrappy, it stops at the divider, which is why those two immunities come after it. The table is completely full in the retail ROM, so the next bytes after the terminator are other data and a new non-neutral pair needs a slot freed first. Types: 0 Normal, 1 Fighting, 2 Flying, 3 Poison, 4 Ground, 5 Rock, 6 Bug, 7 Ghost, 8 Steel, 9 ???, 10 Fire, 11 Water, 12 Grass, 13 Electric, 14 Psychic, 15 Ice, 16 Dragon, 17 Dark (text bank 735). The table reads and writes back byte for byte (`RealRomRecordsTest`). Table and logic from the decomp's `sTypeEffectiveness` and `ov12_02251D28`. **[UNVERIFIED]** in game; the Trainer AI's own use of the table is not checked.

## Pickup (overlay 12)

- In the decompressed image of overlay 12 (battle), HeartGold English: 18 u16 common items at 0x34B44, 11 u16 rare items at 0x34A4C, the activation divisor (1 byte) at 0xC852 and 9 weight bytes at 0x3518C. Vanilla: divisor 10, weights 30, 40, 50, 60, 70, 80, 90, 94, 98 (slots of 30, 10, 10, 10, 10, 10, 10, 4, 4 rolls out of 100), the common table starts with Potion, Antidote, Super Potion and ends with PP Up, Max Elixir, the rare one starts with Hyper Potion, Nugget, King's Rock.
- Logic (decomp `BtlCmd_GenerateEndOfBattleItem`): a Pokémon with Pickup and no held item activates when a random number modulo the divisor is 0; it rolls 0 to 99 and bracket = (level - 1) / 10, at most 9. The first slot j with weight above the roll gives `common[bracket + j]`; a roll of 98 or 99 that no weight caught gives `rare[bracket + 99 - roll]`. A table written back unedited is identical to the overlay (`RealRomRecordsTest`). Offsets from DSPRE's `RomInfo`; the other languages have other offsets and are not supported yet. **[UNVERIFIED]** in game.

## Battle display (`a/1/8/0`, `a/0/0/5`)

- `a/1/8/0` (`pl_poke_data.narc` in the wiki file table): a NARC with one member of 43966 bytes = 494 records of 89 bytes, one per species (index 0 is the egg slot). In each record byte 1 is a movement byte (raw, 24 distinct values in the ROM, meaning unknown), and the last three bytes are the battle sprite Y (signed), the shadow X (signed) and the shadow size (0 to 3 in vanilla). The rest holds two-frame animation data and is kept as read.
- `a/0/0/5` (`height.narc`): a NARC of 1976 = 494 x 4 members of 1 byte (or 0 bytes when the species has no sprite for that slot), in the order female back, male back, female front, male front. Heights are unsigned. 1812 members hold a byte, 164 are empty.
- Both read and write back byte for byte (`RealRomRecordsTest`). Layout from DSPRE's `BattleDisplayEditor` (same for HeartGold and SoulSilver). **[UNVERIFIED]** in game: the movement byte meaning and the visual effect of the offsets.

## Bug Catching Contest (`data/mushi/mushi_encount.bin`)

- A plain file, not a NARC: 320 bytes, 4 sets of 10 entries of 8 bytes (u16 species, u8 min level, u8 max level, u8 rate, u8 score, u16
  padding, always 0). Set 0 is used without the National Dex and on Sunday and Monday; with it, set = weekday / 2 (Tuesday and Wednesday 1,
  Thursday and Friday 2, Saturday 3), from the DS clock. The game rolls 0 to 99 and takes the first slot whose rate is at or below the roll,
  so the chance of slot 0 is 100 minus its rate and of each later slot the gap to the slot above; rates must fall and the last must be 0, or
  a slot never appears or some rolls match nothing. Score is the rarity part of the judge's score. Vanilla: all four sets use rates 80, 60,
  50, 40, 30, 20, 15, 10, 5, 0 (20, 20, 10, 10, 10, 10, 5, 5, 5, 5 percent); set 0 starts with Caterpie 7-18 score 60 and ends with Pinsir
  13-14 score 100. The file reads and writes back byte for byte (`RealRomRecordsTest`). Layout from the DS Pokémon Hacking wiki's Bug
  Catching Contests guide and DSPRE's `BugContestEncounterFile`. **[UNVERIFIED]** in game.

## Starters (ARM9, chooser overlay, scripts, text)

- Not a table: the chooser code of the decompressed ARM9 builds `{CHIKORITA, CYNDAQUIL, TOTODILE}` (decomp `choose_starter.c`). The three
  species are u32 literal-pool words 4 bytes apart (vanilla at 0x108514: 152, 155, 158); the pool is found by the 8 bytes `03 03 1A 12 01 23 00 00`
  that follow it 13 bytes later (exactly one match). Level (5) is a byte at 0x960E2 followed by `22 20`; the held item (0) is a byte at 0x960FE
  followed by `20 09 90 60 19 06 21 09 AA D8 F7 9A FD 06 98 EC 35` (one byte: items above 255 do not fit). Held item and level places from
  DSPRE's `RomInfo`, checked against the ROM.
- Overlay 61 (the chooser app, decomp `choose_starter_app.c` `sSpecies`) keeps its own table of three u32 species, 32 bytes after the pattern
  `00 04 00 0C 10 BD ...` (found once, vanilla 152, 155, 158 at overlay offset 6808).
- The rival's counter-pick: script files 7, 23, 96, 110, 819, 850 and 866 of `a/0/1/2` each contain once the commands
  `CE 00 0C 80 | 11 00 0C 80 <u16 first species> | 1C 00 05 <s32 jump>`; the block the jump reaches (jump + position + 17) starts with `11` and
  compares with the second species at +4. All seven match in the ROM (second branch at +31 each).
- The chooser text (bank 190): messages 1 to 3 ask about each starter and 4 to 6 describe it, with the species name and the type name
  ("Professor Elm: So, you like CHIKORITA, the Grass-type Pokémon?"); editing a starter swaps those two words. Changing the three back restores
  every byte (`RealRomRecordsTest.startersMatchTheGameFollowEditsAndRevertCleanly`). **[UNVERIFIED]** in game.

## Hidden items (ARM9)

- Table in the decompressed ARM9 at 0xFA558, 8-byte entries: item u16, quantity u8, a byte and a u16 that are 0 in every vanilla entry
  (kept as read), index u16. The byte at 0x405E4 is the number of entries the game reads, the byte at 0x405E8 how many fit. Vanilla
  HeartGold English: 231 entries, capacity 231 (full), first entries Potion #0, Nugget #1, Nugget #225, Potion #2, last #230. Offsets from
  DSPRE's `RomInfo`, layout from the decomp's `HiddenItemData`; all checked against the ROM (`RealRomRecordsTest`).
- A spot is script 8000 + index (`_std_hidden_item` = 8000 in `std_script.h`) with the "picked up" flag 800 + index
  (`HIDDEN_ITEMS_FLAG_BASE`); indexes are not in table order (#225 is third). Shortening the table clears the dropped slots and nothing else.
  **[UNVERIFIED]** in game.

## Fly table (ARM9)

- 30 entries of 18 bytes at 0xF9E80 of the decompressed ARM9 (offset from DSPRE's `RomInfo`, layout from its fly editor): flag index u8,
  flags u8 (bit 0 blackout spawn, bit 1 fly point), blackout map header u16, blackout tile X u8 and Y u8, fly map header u16, fly global
  X u16 and Y u16, unlock map header u16, unlock global X u16 and Y u16. The table has a fixed length (the decomp has no fly table
  symbol, nothing found past entry 29), so entries are edited, not added or removed.
- Checked against the ROM (`RealRomRecordsTest.flyTableMatchesTheGameAndSavesBack`): entries 0-22 are the Johto and Kanto towns in
  Town Map order (New Bark Town first, Saffron City last), 23 Safari Zone Gate, 24 Frontier Access, 25 Pokéathlon Dome, 26-29 Route 26,
  32, 3 and 10 (no fly point, blackout spawn only for 27-29). 24 entries unlock where Fly lands; Frontier Access and Pokéathlon Dome
  unlock elsewhere. Flag bits other than the two known ones are 0 in the vanilla table and are not kept. **[UNVERIFIED]** in game.

## ROM toolbox patches (ARM9, overlay 1, synthetic overlay)

Ported from DSPRE's `PatchToolboxDialog` / `ToolboxDB` into `core/.../patch/dspre/` (AGPL-3.0-only files, see `THIRD_PARTY_NOTICES.md`).
HeartGold English only. Every edit lists the bytes it replaces and a patch refuses a ROM that does not hold them. ARM9 offsets are file
offsets (runtime address 0x02000000 + offset). Checked against the ROM: every original value, that all patches apply together and undo
exactly (`PatchToolboxRealRomTest`), and that a ROM with all of them boots to the same intro frame as the vanilla one (`BootFixtures.toolboxPatches`
and `scripts/boot-test.sh`, 2400 frames). Nothing was tried in game past the intro: **[UNVERIFIED]**.

- **Synthetic overlay**: member 0 of `a/0/2/8` (HGSS; Platinum uses `weather_sys.narc`). Vanilla it is a 552 byte file. After the ARM9 expansion it is
  0x16000 bytes, loaded to 0x023C8000 at start-up (offsets in it are runtime address - 0x023C8000).
- **ARM9 expansion**: ARM9 0xCD0 `00 20 03 21` (mov r0,#0; mov r1,#3) becomes `0F F1 30 FB`, a `bl` to a 28 byte loader routine written at ARM9 0x110334
  (vanilla: 0xFF filler); the routine ends with the two replaced instructions. It calls the function at 0x02007508 with the load address
  0x023C8000, 0x1C and 0 (presumably the NARC id and member of `a/0/2/8`; not checked). The vanilla 552 byte member 0 is replaced by 0x16000 zero bytes, as DSPRE does.
- **Matrix expansion**: 13 ARM9 constants (limits `01BF`, `0136`, `0189`, `0109` raised by 0x40, buffer sizes doubled or moved) at 0x3AEC6, 0x3AEC8,
  0x3AF60, 0x3AF7A, 0x3AF94, 0x3AF98, 0x3AFB0, 0x3B000, 0x3B110, 0x3B1F8, 0x3B264, 0x3B090, 0x3B0C4. What each one is exactly is not decoded.
- **Trainer name length**: ARM9 0x7342E is the immediate of `movs r2,#8`; 12 gives 11 usable characters.
- **Dynamic camera**: ARM9 0x23174 `FF F7 06 FF` -> `B6 F3 2E FA`; overlay 1 0x1574C and 0x15864 get a `ldr r3,[pc]; bx r3` stub with the address
  0x023D9C41 and 0x023D9C01 (synthetic 0x11C41 and 0x11C01); the 1816 byte routine (marker "GetCamBox") goes to synthetic 0x115B0.
- **Building rotation**: overlay 1 0xE1C6 `0F A9 00 28` becomes a THUMB `bl` from 0x021F3AC6 to the 20 byte routine at synthetic 0x11CE8, which
  has a `bl` (offset 8) to the game function 0x02020D2C and ends with the two replaced instructions.
- **Script command table repoint**: the count (0x355 = 853, ARM9 0xFAC90) and table (0xFAD00) pointers live at ARM9 0x400E0 and 0x400E4. The table is
  copied to the synthetic overlay at 0x200 as `"COUN"`, count, `"TABL"`, table, and the pointers set to 0x023C8000 + 0x204 and + 0x20C. No command is added.
- **Disable texture animations**: u16 at offset 4 of every `a/0/4/2` file set to 0xFFFF.
- Not ported yet: dynamic headers (moves the header table to `a/0/5/0`; the Map headers editor reads the ARM9 table only), Sentence Case names,
  Standardize items.

## Trainer battle messages (`a/0/5/7`, `a/1/3/1`, bank 728)

HeartGold English, confirmed on the real ROM (`RealRomTrainerMessagesTest`); layout from VSMaker2 `RomFileMethods` and DSPRE `RomInfo` (`trainerTextTable`, `trainerTextOffset`, `trainerMessageTextNumber`).

- `a/0/5/7` is a NARC with one file: 1717 pairs of `u16 trainer, u16 trigger`. Pair `n` goes with message `n` of text bank 728 (also 1717 entries). The pairs are not sorted by trainer, but the pairs of one trainer are next to each other.
- `a/1/3/1` is a NARC with one file: 735 `u16`, one for each trainer number 0 to 734 (the trainer names bank has 738). Value = byte offset into the pair table of the trainer's first pair (always a multiple of 4), 0 for a trainer without messages. The game reads pairs from there while the pair's trainer number matches, so a trainer's messages must stay contiguous.
- Triggers used in vanilla (count): 0 pre-battle 383, 1 player wins 616, 2 post-battle 383, 3 to 10 double battle lines (14, 20, 14, 14, 14, 20, 14, 14), 15 last Pokémon 68, 16 last Pokémon at critical HP 71, 17 rematch 45, 18 and 19 double rematch 2 each, 20 player loses 23. 11 to 14 are not used.
- Rebuilding the tables with every trainer's messages grouped in order of first appearance reproduces the vanilla bytes exactly, so foxhg regroups on every add, remove and CSV import (`TrainerMessages.replaceAll`) and the numbers of the messages after the change move; the text of a message that did not change keeps its stored form.
- Not checked in the running game **[UNVERIFIED]**: a trainer given a new message in a place the game did not use before (for example a trigger the trainer never had).

## Trainers (`a/0/5/5` headers, `a/0/5/6` parties)

- 738 trainers; both NARCs have 738 files. Header is 20 bytes: type (bit 0 custom moves, bit 1 held items), class,
  unknown byte, party size, 4 battle items (u16), AI flags (u32), double-battle (u32). The game stores 2 for a double battle and 0 for a single one
  (DSPRE reads `== 2`); the editor writes exactly those two values.
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
- Slot chances are fixed by the game (decomp `EncounterSlot_WildMonSlotRoll_*`, not stored in the file): grass 20, 20, 10, 10, 10, 10, 5, 5,
  4, 4, 1, 1; surf 60, 30, 5, 4, 1; rock smash 80, 20; all three rods 40, 30, 15, 10, 5. No check for species 0 was found in the decomp's slot roll (**[UNVERIFIED]** in game),
  so the editor never leaves a group with an empty slot: "add Pokémon" takes slots from the Pokémon with most, "remove" hands the slots to
  the others (the last Pokémon of a group cannot be removed). An all-empty group (rate 0 areas) gives every slot to the first Pokémon added.
- A HeartGold ROM whose table was replaced by SoulSilver's boots to the vanilla intro frame. Static encounters, roamers and
  fossils are not in these tables (scripts / overlay data) and are not handled yet.

## Map headers (ARM9)

- 24 bytes each: wild encounter file (0xFF none), area data, packed world-map coordinates (u16), matrix, script, level
  script, text archive, day music, night music, event file (u16 each), location-name index (byte, into message bank 279),
  area properties (byte), 32 packed flag bits (weather, camera, battle background, ...). Layout from DSPRE's `HeaderHGSS`.
- The table is in the **decompressed** ARM9. In English HeartGold header 0 is at `0xF6BE0` (24 bytes after the offset DSPRE
  quotes, because DSPRE's image differs by one entry); there are **540** headers, 135 with encounters, all encounter ids
  below 142. **[UNVERIFIED]** for SoulSilver (assumed to be the same `0xF6BE0`, as in DSPRE).
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

## Event editing rules (app)

- Events of a map cell are the ones of its header's event file whose position is inside the cell (x / 32, y / 32 equal the matrix
  cell). Moving an event keeps it in its cell. Triggers cover `width x height` tiles from their position. New events copy the last
  event of their kind (objects get the next free object id); an event file shared by several headers is edited for all of them.

## Trainer class pictures (`a/0/5/8`, `trfgra`)

- 645 files = 129 classes x 5, in class order (the class id of the trainer header is the index): `NCGR` (4 bit, 3200 bytes of tiles,
  width -1), `NCLR` (256 colours; the 4 bit graphic uses palette 0 of 16), `NCER`, `NANR`, then a second `NCGR` (the other frame
  sheet, not decoded).
- The picture is cell 0 of the NCER: 6 OAM parts (a 64x64 block plus 16x32 and 16x16 strips) placed around an origin, bounding
  box 80x80. NCER `KBEC` section: u16 cell count, u16 bank type (bit 0 = cells carry 8 bytes of bounds, so 16 bytes per cell,
  else 8), u32 offset of the cells, u32 mapping shift; a cell is u16 OAM count, u16 attributes, u32 offset into the OAM list; an OAM is
  three u16 (attr0: y 8 bit signed, shape in bits 14-15; attr1: x 9 bit signed, size in bits 14-15; attr2: tile in bits 0-9, palette
  in bits 12-15). Tile `n` of an OAM is at byte `(n << mapping) * 32` of the NCGR data (mapping is 1 here: tiles are numbered in
  steps of 2); the tiles of one OAM follow each other row by row. Verified by eye on classes 0-3, 10, 20, 50, 100 and in the app
  (Silver = Rival, Wade = Bug Catcher).

## Undo journal (app)

- Edits live in `RomProject`; every write, revert, ARM9 and overlay change is journaled as (target, bytes before, bytes after).
  Writes to the same target less than 1 s apart merge into one step (typing in a field); `revertAll` is one step; 200 steps are
  kept; a new edit clears redo. After undo/redo the open editor is rebuilt from the project (selection of the record editors is
  kept in `AppModel.selectionMemory`; other editors go back to their first record).

## Overworld sprites (`a/0/8/1`, sprite table in overlay 1)

- The "sprite entry" of an overworld event is a key of the table, not a row. The table is in overlay 1 (decompressed); the pointer
  to it sits at RAM address 0x021F92FC (English; overlay 1 loads at 0x021E5900 here), a main memory address (top byte 2). Entries
  are three u16 (entry, sprite file, properties) up to a 0xFFFF entry id: 901 entries, ids 1..1049, sprite files 0..862. Properties
  are flags we do not read (20007 on 585 entries, 0 on 182, 21000 on 34). Entry 1 -> file 0 (a baby boy), 1037-1039 -> the three legendary beasts.
- Sprite files are `BTX0` texture archives (32x32, 16 colour textures, 1 palette, colour 0 transparent). 16 textures per
  character: the facing picks the texture: <= 4 textures `facing`, <= 8 `facing * 2`, <= 16 `0, 11, 2, 4`, more `0, 27, 2, 4` (facing
  0 up, 1 down, 2 left, 3 right, as DSPRE's event editor does). BTX0: `u16` block count at 0xE, block offsets from 0x10; `TEX0` block:
  texture data offset at +0x14, palette dictionary at +0x34, palette data at +0x38, texture dictionary at +0x3C. A dictionary is
  a 16 byte head (count at +1) and 4 bytes per entry, then entries: textures `u16 offset >> 3, u16 params, u32` (format bits
  10-12, width `8 << bits 4-6`, height `8 << bits 7-9`, bit 13 colour 0 transparent), palettes `u16 offset >> 3, u16`; names
  (16 bytes each) follow. Formats 1, 2, 3, 4, 6, 7 decode; 5 (block compressed) does not and gives no picture. Verified by eye.

## Map thumbnails (app)

- A thumbnail is one pixel per tile of the 32x32 permissions (tile type colour, blocked tiles darkened), a schematic of the walkable
  layout. The real scene is the NSBMD terrain, buildings and textures (3D), which is not read yet.

## Map scenes: NSBMD models and the area data chain

- The `model` of a map file (`a/0/6/5`) is a `BMD0` file with only an `MDL0` block (no textures). Textures come from the area of
  the map: map header `areaData` -> file of `a/0/4/2` (8 bytes: u16 building texture set, u16 map texture set, u16 dynamic
  texture type, u8 indoor/outdoor, u8 light) -> file `map texture set` of `a/0/4/4` (a `BTX0`). Buildings (`Building.modelId`) are
  files of `a/0/4/0`: `BMD0` with `MDL0` **and** their own `TEX0` (339 of 340), so the building texture set of the area data is not
  needed for the picture. A matrix cell that uses a map decides which header (and so which area) the map is drawn with.
- `BMD0`/`BTX0` header: `u16` block count at 0xE, `u32` block offsets from 0x10. A Nitro dictionary (used everywhere below): 8 byte
  head (count at +1), a tree of 4 bytes per entry plus one, `u16` entry size and `u16` total, the entries, then 16 byte names.
- `MDL0`: dictionary of models at +8 (entries `u32` offset from the block). Model: `u32` at +4 render commands (SBC), +8 material
  set, +0xC shape set (all relative to the model), `s32` position scale at +0x1C (fx32; 64 for maps, 4..64 for buildings), node
  dictionary at +0x40. World size: model units are 1/16 tile, so a map spans 32 * 16 = 512 units centred on the origin, and a
  building's position in the map file (tiles from the centre) is added after dividing by 16.
- Materials: dictionary, each entry an offset to a struct with polygon attributes at +0xC (alpha bits 16-20, 31 = opaque) and
  texture image parameters at +0x14 (bit 16/17 repeat S/T, 18/19 flip S/T). The material set starts with two `u16`: offsets of the
  texture-name and palette-name dictionaries; their entries are `u32` (bits 0-15 offset to a list of material indices, 16-23 the length,
  bit 24 no binding). Materials without a texture name are not drawn (animated water and so on).
- Nodes: record = `u16` flags (bit 0 no translation, 1 no rotation, 2 no scale, 3 compressed rotation: pivot in bits 4-7, sign
  flags in bits 8-10), `s16` rotation[0][0] (fx16), then translation 3 x fx32, rotation (8 x fx16, or two for compressed) and
  scale 3 x fx32 (+ 3 unused). A vertex is scaled by the node scale, rotated (row vector) and multiplied by the position scale, then
  offset by the translation; children compose with the parent (scale multiplies, translation is scaled and rotated by the parent).
- SBC bytecode (`op & 0x1F`): 1 end, 2 NODE (2 bytes), 3 MTX (1), 4 MAT (1), 5 SHP (1), 6 NODEDESC (node, parent, flags +1 store +1
  restore), 7/8 billboards, 9 NODEMIX (2 + 3n), 0xA CALLDL (8). MAT sets the material, SHP draws the shape with the current node and
  material.
- Shape struct: display list offset (from the struct) at +8, size at +0xC. A display list packs four geometry commands per word
  followed by their parameter words; used: 0x22 TEXCOORD (two fx16 with 4 fraction bits, in texels), 0x23 VTX_16, 0x24 VTX_10 (fx 1.3.6),
  0x25-0x27 VTX_XY/XZ/YZ, 0x28 VTX_DIFF (fx 1.3.12 deltas), 0x40 BEGIN_VTXS (0 triangles, 1 quads, 2 triangle strip, 3 quad strip), 0x41
  END; the others are skipped by their parameter counts. Verified: terrain and buildings of New Bark Town, Route 29, Cherrygrove and a
  gym look right; 676 maps render in about 5 s.
- Not drawn: translucent faces (shadows, alpha < 31) and the building models using the texture `wind` (a weather effect that is
  thin streaks in its static pose), lighting, animated water, building rotation and scale.

## Pokémon pictures

- Icons (`a/0/2/0`): file 0 palettes, file `naix` the 32x64 icon; `naix = species + 7` for 0..493. Egg 501 (Manaphy egg 502),
  Deoxys forms 503-505, Unown letters 507-533 (`form + 507 - 1`), Burmy 534-535, Wormadam 536-537, Shellos 538, Gastrodon 539,
  Giratina origin 540, Shaymin sky 541, Rotom 542-546, Castform 547-549, Cherrim 550-551 (from `GetMonIconNaixEx`). The palette is
  `sPokemonPalNoBySpeciesAndForm[slot]` with slot = species, 494/495 for eggs, 496 + form - 1 Deoxys, 499 Unown, 527 Burmy, 529
  Wormadam, 531 Shellos, 532 Gastrodon, 533 Giratina, 534 Shaymin, 535 Rotom. Personal data records above 493 are 494 egg,
  495 Manaphy egg, 496-498 Deoxys, 499-500 Wormadam, 501 Giratina origin, 502 Shaymin sky, 503-507 Rotom.
- Battle pictures (`a/0/0/4`, 6 files per species 0..493): back female, back male, front female, front male, palette, shiny
  palette. Species with one gender have an empty file for the other. Each picture is an NCGR (4 bit, flag 1 = rows not tiles,
  20 x 10 tiles = 160 x 80: two 80 x 80 frames side by side, the first is used) whose tile data is **scrambled**: a 16 bit
  stream cipher, `key = first u16`, for each u16 word `word ^= key & 0xFFFF; key = key * 1103515245 + 24691` (32 bit). The
  palette is 16 colours.
- Alternate forms and eggs (`a/1/1/4`, "otherpoke"): NCGR pairs (back, front) in the order of the decomp's `otherpoke.txt`
  (index 0 Deoxys normal back ... 152/153 Giratina origin, 136/137 Shaymin sky, 140-149 Rotom, 80-83 Wormadam sandy and trash,
  132/133 the two eggs, one picture each), then from index 158 palettes in normal / shiny pairs per form group (Deoxys 158 for all
  four forms, Wormadam 176/178/180, Shaymin 232/234, Rotom 236..246, Giratina 248/250, eggs 230/231). Same scrambling.

## 3D view (app)

- World units are tiles with the map's centre as origin, y up, z towards the south (a building's `x`, `y`, `z` in the map file are in
  these units). `Rasterizer3d` projects with a vertical field of view of 0.7 rad, clips at 0.2 tiles, interpolates `1/w`, `s/w` and `t/w`
  and shades flat with `0.6 + 0.4 * |n . L|`, `L` = (0.35, 0.85, 0.4); the models' own normals and light settings are not used yet.

## Project folder: history.bin

- `FHGH`, u32 version 1, u32 undo steps, u32 redo steps; then every undo step (oldest first) and every redo step (next redo last): u32
  changes, each u8 kind (0 file, 1 ARM9, 2 overlay), the path (u16 length + UTF-8) or the overlay id (u32), then the bytes before and
  after (u8 present, u32 length, bytes). Written when the project is saved explicitly or closed; deleted when the history is empty;
  ignored when damaged or when the newest undo step does not end in the current file contents.

## BDHC (walking surface, last section of a map file)

- `BDHC`, then u16 counts: points, slopes, heights, plates, stripes, plate indices (the six counts fill bytes 4..15); then points (8 bytes: s32 x,
  s32 z, 16.16 fixed point, tiles from the middle of the map), slopes (12 bytes: s32 x, up, z in 4096ths; flat = (0, 4096, 0), 45 degrees =
  (0, 2896, 2896)), heights (s32, 16.16, **negated**), plates (4 x u16: two corner points, slope, height), stripes (8 bytes: s32 z, u16
  count, u16 first index) and plate indices (u16, the plates of every stripe one after the other). Section size = 16 + 8p + 12s + 4h + 8pl +
  8st + 2i exactly, for all 676 maps; round trip exact.
- A flat plate's ground height is the negative of its stored height: New Bark Town's plates say -1.0 where the terrain model is at 1.0, and
  80 % or more of the flat plates match the model within 0.3 tiles (a test). Sloped plates (stairs, ramps, slopes of the 45 degree kind) are
  not drawn: their plane formula is not confirmed (a slope plate of the lab's roof area does not match the terrain at all).
- Stripes are rows of the lookup grid, 7 for New Bark Town, with the z of the row start; their plates are listed in the index list.

## In-game trades (`a/1/1/2`)

- 13 files of 0x54 bytes, one per NPC trade, 21 little-endian u32: give species, HP, Atk, Def, Speed, SpAtk, SpDef IVs, ability, OT id,
  cool, beauty, cute, smart, tough, PID, held item, OT gender (0 male, 1 female), sheen, language (2 English, 3 for Volty), ask species,
  one unused word (0 in all 13). Layout from the decomp's `NPCTrade`; round trip exact, and the vanilla trades match the decomp's
  `NpcTradeNum` order (0 Onix for Bellsprout, 12 Beldum for Forretress).
- Nickname of trade `n` is entry `n` of message bank 200, the original trainer's name entry `n + 13` (Rocky, Rudy). The editor does not
  edit those; the text editor does. PID and OT id are u32 in the file; the editor edits them up to 2^31 - 1 (all vanilla values are far below).

## Static Pokémon in scripts (HeartGold English)

- Script commands that make a fixed Pokémon: `WildBattle` (589: u16 species, u16 level, u8 shiny), `GiveMon` (137: u16 species, u16 level, u16 item, u16 form, u16 ability, var result) and `GiveEgg` (138: u16 species, u16 location). A value of 0x4000 or more is a script variable, not a number. `CreateRoamer` (464, u8 id) only picks which roamer starts: ids 0 Raikou, 1 Entei, 2 Latias, 3 Latios, their species and levels (40, 40, 35, 35) are in the game's code (`Save_CreateRoamerByID`), not in data. The vanilla ROM has 21 `WildBattle` (3 with the species in variable 0x400A), 13 `GiveMon` and 3 `GiveEgg` (species 179, 194, 218) with the species as a plain number: for example Mewtwo level 70, Suicune level 40 twice, the Eevee from Bill level 5, Tyrogue level 10, Dratini level 15.
- Species from a variable: the scripts set it with `SetVar var, species` (41) or `SetOrCopyVar var, species` (43) before the command, on the path that reaches it, so the editor walks the script backwards from the command (fall-through, `GoTo` and `GoToIf` and the other jumps, and from a function back to its callers) up to the first assignment of the variable on each path, and lists each assignment with a species number as an encounter of its own; editing it rewrites that `SetVar` value (a `PlayCry` on the same variable follows for free). Found in the real ROM: Ho-Oh (script file 21, variable 0x400A = 250) and Lugia (file 104, 249), Latias and Latios in file 750 (380, 381, 0x400A), the Game Corner prizes in file 804 (122, 133, 137, variable 0x4002), the Kanto starters in file 740 (1, 7, 4, variable 0x8004), the Hoenn starters in file 837 (252, 255, 258) and four gifts at level 15 in files 906 and 910 (63, 23, 147, 27). Not resolved: a `GiveMon` in file 755 on variable 0x407F (the species is stored earlier or in another script). The level of Ho-Oh and Lugia comes from variable 0x8004, which `GetGameVersion` branches set to 45 or 70 (`SetVar 32772, 45` at items 54 and 58 of file 21, items 67 and 71 of file 104; the two game versions pick opposite values in the two scripts). The level is followed the same way as the species: one row for each `SetVar` of a number from 1 to 100 that reaches the command, and a level edit rewrites only that `SetVar`. A level variable with no such assignment is shown but not edited. Which assignment a game uses: both scripts run `GetGameVersion`, `CompareVarToValue result, 7` and `GoToIf 5, function_5` (not equal); HeartGold is version 7 and falls through, which was checked in the emulator (`GameVersionBranchEmulatorTest`: the same three commands in the clerk script fall through, giving the Ditto of the fall-through branch). So HeartGold uses the `SetVar` before the `GoTo`: 45 for Ho-Oh (file 21, item 54) and 70 for Lugia (file 104, item 67); SoulSilver the other value of each. A call is not followed, so an assignment made inside a called function is not seen. Not checked in game.
- Overlays: the ARM9 overlay table (`y9.bin`) has 32 byte entries (id, RAM address, RAM size, BSS size, static initialiser start and end, file id, and the last word with the flags in the top byte and the packed size in the low 24 bits); foxhg keeps edited overlays decompressed and compresses them again (BLZ) at build time when the table says the overlay is packed.

## Safari Zone encounters (HeartGold English)

- `a/2/3/0` (`sz_enc_data.narc`) has 12 files, one per kind of area (Plains, Meadow, Savannah, Peak, Rocky Beach, Wetland, Forest, Swamp, Marshland, Wasteland, Mountain, Desert; names from DSPRE). Layout from DSPRE's `SafariZoneEncounterFile`, confirmed by an exact round trip over all 12 files: 5 bytes with the object arrangement count of each group (grass, surf, old rod, good rod, super rod), 3 bytes of padding, then the groups in that order. Each group: 3 x 10 encounters (morning, day, night; u16 species, u8 level, 1 byte of padding), then 3 x N encounters (the N arrangements, by time of day), then N pairs of requirements (u8 type, u8 quantity: required, then optional). Requirement types: 0 none, 1 Plains, 2 Forest, 3 Peak, 4 Waterside.
- The vanilla files have 10 arrangements for grass, 3 for surf and 2 for each rod, in all 12 areas.

## Roamers (HeartGold English)

- `Save_CreateRoamerByID` (decomp `field_roamer.c`) is in the ARM9, THUMB, at 0x6771C-0x67733 for the four `switch` cases (found by the bytes, not by the address): `movs r6, #species` then `movs r5, #level`, then `b` to the shared tail. Raikou: species imm8 at +0 (243), level at +2 (40). Entei: +6 (244), +8 (40). Latias: `movs r6, #95` at +12, `lsls r6, r6, #2` at +14 (95 << 2 = 380), level at +16 (35). Latios: `ldr r6, [pc, #188]` at +20 reads the u32 species word at 0x677F0 (381), level at +22 (35). So Raikou and Entei hold species 1 to 255, Latias a byte shifted left (shift in the `lsls` word, bits 6 to 10), Latios any. Confirmed by disassembling the real ARM9 and by `RoamersRealRomTest`. Not checked in game.
- The tail stores the species and level with `SetRoamerData` and builds the Pokémon with `CreateMon` (decomp), taking both from those registers.

## Fossils (HeartGold English)

- `ScrCmd_GetFossilPokemon` and `CountFossils` (decomp `scrcmd_fossils.c`) read `sFossilPokemonMap`: 7 pairs of u16 item and u16 species, 28 bytes. In the real ROM it is in **overlay 21 at offset 304** (not in the main ARM9, although the decomp lists the file in `main.lsf`; found by searching the ARM9 and every overlay for the bytes). Order: Old Amber (103) to 142 Aerodactyl, Helix (101) to 138 Omanyte, Dome (102) to 140 Kabuto, Root (99) to 345 Lileep, Claw (100) to 347 Anorith, Armor (104) to 410 Shieldon, Skull (105) to 408 Cranidos. foxhg finds it by the seven item ids in that order with a species 1 to 493 after each, so an edited table is still found.
- The museum scientist's script (file 755) calls `GetFossilPokemon` to put the species in variable 0x407F, then `GiveMon 0x407F, 20, ...`: the level 20 is in the script. The same script checks the fossil items with `HasItem` by item id, so only the species is edited. Not checked in game.

## Shiny rates (HeartGold English)

- The game has no shiny chance to edit: a Pokémon is shiny when `(tid ^ sid ^ pid high ^ pid low) < 8` (8 in 65536, `SHINY_CHECK`, inlined in several places; the stand-alone check is at ARM9 0x2070068, `cmp r0, #8` at 0x2070080), so no single number changes the rate of one kind of Pokémon. The "Shiny rates" patch (`ShinyRatesPatch`, source `docs/asm/shiny_rates.s`, built by `scripts/build-shiny-asm.sh`) hooks the places that make them instead and keeps four u32 rates (chance out of 65536) at synthetic overlay 0x11D00; the routines are at 0x11D20 (264 bytes). A rate of 0 exits before drawing a random number. It needs the ARM9 expansion.
- Wild: `generateWildNonShinyAndAddToParty` is at 0x02247A18 in overlay 2 (0x1E98 in the file), `generateWildShinyAndAddToParty` at 0x02247910, `GenerateShinyPersonality(otid)` at ARM9 0x02070094 (found by its 13 step loop and the `bl` to `gf_rand` 0x0201FD44). Both take `(species, level, battler, r3, encounterGen, leadMon, battleSetup)` with the last three on the stack; r3 is "force one perfect IV" for the non-shiny one and the OT id for the shiny one, and `encounterGen->playerId` is its first word. The non-shiny one is called from 0x02247D96, 0x02247EC8, 0x02247F20 (the encounter tables) and 0x02247F92 (a script `WildBattle` that does not ask for a shiny; the call to the shiny one is at 0x02247F78). The patch replaces each of these `bl`s (overlay 2 is within `bl` range of the synthetic overlay) with a `bl` to a routine that copies the arguments and, at the chance set, calls the shiny one with the player's OT id instead.
- Gifts and trainers: `Party_AddMon(party, mon)` is at ARM9 0x02074524; called from `GiveMon` (0x0205425C), `GiveEgg` (0x020542BC) and the script command `GiveEgg` (0x0204D2BE), and from the four trainer party formats (0x02073746, 0x02073850, 0x02073946, 0x02073A64). The ARM9 is too far from the synthetic overlay for a `bl`, so the seven `bl`s go to one 8 byte stub at ARM9 0x110350 (free space after the loader of the ARM9 expansion: `ldr r3, [pc]; bx r3; .word routine`), and the routine tells gifts from trainers by the return address (0x02073000 and up is the trainer code). A gift is made shiny with `SetMonPersonality(mon, GenerateShinyPersonality(otid))` (0x0207235C; `GetMonData(mon, 7)` is the OT id); a trainer's Pokémon keeps its personality and gets a random OT id whose halves xor with the personality halves to below 8, set with `SetMonData(mon, 7, &otid)`.
- Checked in the emulator (DeSmuME), `ShinyRatesInGameEmulatorTest`: gifts at 100 % are all shiny (Cyndaquil, Pikachu, Pidgey given by the init script) and none at 0; a scripted `WildBattle` is shiny at a scripted rate of 100 % and not when only the other rates are set; the Pokémon of trainer 1 (plain party) and trainer 21 (held items and own moves) are shiny at a trainer rate of 100 %. Not driven in the game: wild encounters from the grass, water and rods (the same routine as the scripted battle, entered from three other `bl`s whose original bytes the patch checks), and eggs of the script command `GiveEgg` and the Day-Care. Bred eggs are not hooked (the game has its own Masuda method).

## Ground items (item balls), HeartGold English

- Script numbers 7000 to 7254 (decomp `std_itemball_*`, `_std_item_ball` = 7000) are the entries `script_1` to `script_255` of script file 141 (`a/0/1/2`, DSPRE `RomInfo.itemScriptFileNumber` for HGSS; 370 for DP and 404 for Platinum): script number = 7000 + N - 1. Each is `SetVar 0x8008, item`, `SetVar 0x8009, quantity`, `GoTo script_256`, `End`. `script_256` (7255, `std_itemball_variadic`) is the shared function that gives the item and shows the message; it does not start with those two `SetVar`s and is not an entry. Checked on the real ROM: script 7000 gives Potion x1 (Route 29), 254 of the 255 entries are run by an overworld event of some map (the unused one is a leftover).
- An overworld event uses a ball by setting its script field to the number; its type is not needed to find the maps (DSPRE also accepts type 3). Editing an entry changes every event that runs the number.
- DSPRE's editor adds and removes entries and, for Platinum only, adjusts a u16 at overlay 9 offset 0x8E20 + 10. That fix-up does not apply to HGSS.

## Town Map (PokeGear map) tables, HeartGold English

- Both tables are in the decompressed overlay 101 (offsets from the wiki guide "Editing the Town Map", checked on the real ROM). Visited towns, `GearMapTownOverlay`: 27 entries of 14 bytes at 0x10274 (map header u16, gate map header u16, entry u8, visited entry u8, red X, red Y, gray X, gray Y, town size byte, gray block size byte, offset byte, padding). In the three size bytes the high nibble is Y and the low X. The red X and Y are stored one tile off (picture position X - 1, Y + 1). Entry 0 is Pallet Town (map header 0x31, red 0x20 0x0B, gray 0x00 0x14), entries 0 to 10 are Kanto towns (Indigo Plateau is 9), 11 to 22 Johto towns, then 23 Safari Zone gate, 24 Frontier access, 25 Pokéathlon gate and 26 Victory Road. Entries 9 and 21 to 26 (Indigo Plateau, Lake of Rage, Mt. Silver and the four gate entries) have visited entry 0xFF, so no gray block replaces them.
- Selection blocks, `GearMapTownSelectionOverlay`: 100 entries of 16 bytes at 0xFC32 (map header u16, PokeGear X, PokeGear Y, size byte with Y in the high nibble, flags, text entry in text bank 273, fly spot, u32 padding, picture X, picture Y, picture width, picture height). The table ends at 0x10272 (two zero bytes follow) and the town table starts at 0x10274. Entry 28 is the Pallet Town block (map header 0x31, PokeGear 0x20, 0x0D, size 1x1, text 0x0A, fly spot 1, picture Y 0x20, 3x3). What the flags do is not documented; all bytes read and write back unchanged (`TownMapRealRomTest`).
- Both lengths are fixed in the game code, so entries are edited, not added. SoulSilver and other languages may differ: not checked.

## HP bar speed, HeartGold English

- One Thumb instruction in the decompressed overlay 12 at 0x2E17A (wiki guide "Changing the HP bar speed", offset checked: the ROM has `00 02`, `LSLS R0, R0, #8`). The immediate is the shift of the bar's step: the speed is 2^(n - 8) times the game's (`80 01` = #6 = x0.25, `C0 01` = x0.5, `40 02` = x2, `80 02` = x4, `C0 02` = x8; the instruction word is `n << 6`).
- Verified in the emulator (`HpBarSpeedInGameEmulatorTest`, a Rattata battle, pixels of the two HP bars on the top screen counted per frame): the same drain of 16 to 18 pixels takes 119 frames at #6 (one pixel every 8 frames), 33 at #8 (one pixel every 2 frames) and 1 at #12 (the bar jumps within a frame).
- The guide's fine control (a routine in the synthetic overlay at 0x14FF0 and a `bl` at 0x2E17A) is not used. Later changes of the bar pixels every 8 frames, a few hundred frames after the drain, are not the drain (cause not identified; possibly the low HP blink of the player's bar).

## Photo locations, HeartGold English

- `a/2/5/4` has 93 files of 16 bytes, eight little-endian halfwords (wiki guide "Photo Locations", struct from the decomp's `photo_album.h`): map header, album icon (0 to 9: mountain landscape, sea, yellow plain, rough terrain, cave interior, building exterior, building interior, grassland, forest, lake), player global X, player global Y, a constant that is 0xFF01 in every file, the overworld entry of the NPC in the picture (0 = group photo of the party), parameter 1 and parameter 2 (unknown). The script command is `CameronPhoto n` (id 615, `FieldSystem_TakePhoto`), the album is `OpenPhotoAlbum`.
- Checked on the real ROM: file 9 is the guide's example (Route 34 Day Care, header 38, icon 5, x 363, y 410, entry 330), every file reads and writes back unchanged. The guide says a photo with no NPC must have both parameters 0 or the game fails to load, but nine vanilla files (1, 10, 14, 35, 52, 53, 55, 62, 66) have no NPC and a non-zero parameter 1 (file 53 also a parameter 2), so the rule is wrong as written and the editor does not enforce it.

## Battle rules (wiki guide "Battle Logic Edits"), HeartGold English

- Each rule is one byte, the immediate of a Thumb `MOVS`/`LSLS` instruction in the decompressed overlay 12 (a divisor, multiplier, chance denominator or number of turns), or a byte of a battle script: `a/0/0/1` is the sub-sequence NARC (file 26 burn: divisor at 0x30, Heatproof divisor at 0x50; file 18 sleep: random range at 0x264, base turns at 0x268; file 58 binding moves: range at 0x38, base at 0x3C, Grip Claw duration at 0x60, at most 7) and `a/0/3/0` the move effect NARC (Reckless: 0x20 of files 45, 48, 198, 253, 262 and 269). Overlay 12 offsets: freeze thaw divisor 0x13E12 (5), Iron Fist multiplier 0x1FF44 (12) and divisor 0x1FF48 (10), Pickup 0xC852 (10), Ice Body 0xA3F6 (16), Rain Dish 0xA46C (16), Quick Feet multiplier 0x185AE and 0x1875A (15) and divisor 0x185B2 and 0x1875E (10), Slow Start 0x185E2, 0x1878E, 0x1C2A8 and 0x1FA38 (5), Rivalry same gender 0x1FF00 (125) over 0x1FF04 (100), opposite gender 0x1FF26 (75) over 0x1FF2A (100), Leftovers 0x1D508 (16), Black Sludge heal 0x1D548 (16) and damage 0x1D570 (8), the six X items 0x372A, 0x3768, 0x37A6, 0x37E4, 0x3822 and 0x3860 (1 stage), Aqua Ring 0x1257A (16), binding damage 0x12852 (16), Encore 0x939A (5).
- Also in overlay 12: hail damage 0xA42C (16) and the critical hit table at 0x35288, one byte for each stage 0 to +4: 16, 8, 4, 3, 2 (a random number divided by the byte, a remainder of 0 crits). The Magnitude powers (rolls 4 to 10: 10, 30, 50, 70, 90, 110, 150) are at overlay 12 0xA89A, 0xA8AA, 0xA8BA, 0xA8CA, 0xA8DA, 0xA8EA and 0xA8F4, and the Trainer AI keeps a copy at overlay 10 0x351C, 0x3524, 0x352C, 0x3534, 0x353C, 0x3544 and 0x3548; Gyro Ball is 25 at overlay 12 0xBEAA and overlay 10 0x347C. Both places of each are written together. That is 42 rules.
- All 42 rules were read from the real ROM and every one holds the guide's vanilla value. A divisor must stay at least 1. The game takes at most 7 for the Grip Claw duration (guide). Several places of one rule are written together.
- Verified in the emulator: the critical hit table (stage 0 set to 1 makes a Tackle take 30 of 49 enemy HP bar pixels, the vanilla 16 took 11 to 14, `CriticalHitInGameEmulatorTest`) and Leftovers at 1/2 of the maximum HP heals a 40 of 110 HP lead to 91 in one turn (`LeftoversInGameEmulatorTest`). The others were not run in battle.

## Where a number is used (Research Helper)

- The command database (`ScrcmdData`) types parameters only as `u8`, `u16`, `u32`, `var`, `flag`... where `flag` is never used: flags are `u16` parameters named `flag` (`SetFlag`, `ClearFlag`, `CheckFlag`), items are `u16` named `item` or `item_id`, trainers are named `trainer`, `trainer_1`, `trainer_2`, `trainer_id`, `trainer_1_id` or `trainer_2_id` (not `trainer_class`), and variables are the `var` typed parameters plus the names `variable`, `var_N`, `result`, `retvar` and a few more. The Research Helper matches by these.
- Events: an overworld object has a flag (its appear or hide flag), a sprite entry and a script; a trigger has a variable. A trainer object has the script 3000 + trainer - 1 (`std_trainer`) or 5000 + trainer - 1 (`std_trainer_2`), from the decomp's `std_script.h` (first trainer index 1, 740 trainers). On the real ROM 493 of the 740 trainers are found on a map or in a `TrainerBattle`, 41 items are named by scripts, flag 579 (hide Whirl Islands Lugia) is used by scripts 104, 149, 750 and 825 and by six objects of event file 230.
