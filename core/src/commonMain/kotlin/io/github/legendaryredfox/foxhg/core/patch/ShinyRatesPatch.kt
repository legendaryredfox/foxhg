package io.github.legendaryredfox.foxhg.core.patch

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u32
import kotlin.math.roundToInt

/** How likely a Pokémon of each kind is to be shiny, as a chance out of 65536 (see [ShinyRates.percent]); 0 leaves the game as it is. */
data class ShinyRates(val wild: Int, val scripted: Int, val gift: Int, val trainer: Int) {
    init { require(listOf(wild, scripted, gift, trainer).all { it in 0..MAX }) { "A shiny rate is 0 to $MAX out of $MAX" } }

    fun toBytes(): ByteArray = ByteArray(16).also { listOf(wild, scripted, gift, trainer).forEachIndexed { i, v -> it.putU32(4 * i, v.toLong()) } }

    companion object {
        const val MAX = 65536
        val NONE = ShinyRates(0, 0, 0, 0)

        /** [percent] (0 to 100) as the number the game code compares with, rounded to the nearest 1/65536. */
        fun fromPercent(percent: Double): Int = (percent / 100.0 * MAX).roundToInt().coerceIn(0, MAX)

        fun percent(rate: Int): Double = rate * 100.0 / MAX

        /** A rate in hundredths of a percent (10000 = 100 %), the unit of the editor's fields. */
        fun toBasisPoints(rate: Int): Int = (rate * 10000.0 / MAX).roundToInt()

        fun fromBasisPoints(points: Int): Int = fromPercent(points / 100.0)

        fun parse(table: ByteArray, at: Int): ShinyRates = ShinyRates(
            table.u32(at).toInt().coerceIn(0, MAX), table.u32(at + 4).toInt().coerceIn(0, MAX),
            table.u32(at + 8).toInt().coerceIn(0, MAX), table.u32(at + 12).toInt().coerceIn(0, MAX),
        )
    }
}

/** Where the shiny rate code goes and what it hooks (addresses are runtime addresses of HeartGold English). */
private object ShinyHooks {
    private const val SYNTH = HgssLayout.SYNTHETIC_OVERLAY_ADDRESS
    const val TABLE_OFFSET = 0x11D00
    const val CODE_OFFSET = 0x11D20
    private const val CODE_ADDRESS = SYNTH + CODE_OFFSET
    private const val WILD_ENTRY = CODE_ADDRESS
    private const val STATIC_ENTRY = CODE_ADDRESS + 6
    private const val PARTY_ENTRY = CODE_ADDRESS + 0x46
    private const val ARM9_STUB = 0x110350
    private const val PARTY_ADD = 0x02074524
    private const val NONSHINY_GENERATOR = 0x02247A18
    private const val OVERLAY_2_ADDRESS = 0x02245B80

    /** Calls of `generateWildNonShinyAndAddToParty` made for the wild encounter tables (grass, surf, rods, rock smash, headbutt, swarms). */
    private val wildCalls = listOf(0x02247D96, 0x02247EC8, 0x02247F20)

    /** The call made for a script's `WildBattle`, when the script does not ask for a shiny. */
    private const val SCRIPTED_CALL = 0x02247F92

    /** Calls of `Party_AddMon` in `GiveMon`, `GiveEgg` and the `GiveEgg` script command. */
    private val giftCalls = listOf(0x0205425C, 0x020542BC, 0x0204D2BE)

    /** Calls of `Party_AddMon` in the four trainer party formats. */
    private val trainerCalls = listOf(0x02073746, 0x02073850, 0x02073946, 0x02073A64)

    val code: ByteArray = hexBytes(
        "1F B5 34 4C 01 E0 1F B5 33 4C 24 68 33 49 03 9B 00 2C 0B D0 32 48 80 47 " +
        "00 04 00 0C A0 42 03 D2 30 49 06 9B 1B 68 01 E0 2C 49 03 9B 0C 1C 06 98 " +
        "07 99 08 9A 07 B4 03 98 04 99 05 9A A0 47 03 B0 04 B0 10 BC 00 BD 13 B5 " +
        "03 9A 27 4B 27 4C 9A 42 00 D3 27 4C 24 68 00 2C 34 D0 21 48 80 47 00 04 " +
        "00 0C A0 42 2E D2 03 9A 1F 4B 9A 42 0B D2 01 98 07 21 00 22 1F 4C A0 47 " +
        "1F 4C A0 47 01 1C 01 98 1E 4C A0 47 1E E0 01 98 00 21 00 22 19 4C A0 47 " +
        "01 0C 00 04 00 0C 48 40 04 1C 11 48 80 47 00 04 00 0C 01 B4 0E 48 80 47 " +
        "07 21 08 40 60 40 00 99 48 40 09 04 01 43 00 91 02 98 07 21 00 AA 10 4C " +
        "A0 47 01 B0 00 98 01 99 0E 4C A0 47 02 B0 10 BC 00 BD 00 00 00 9D 3D 02 " +
        "04 9D 3D 02 19 7A 24 02 45 FD 01 02 11 79 24 02 00 30 07 02 08 9D 3D 02 " +
        "0C 9D 3D 02 41 E5 06 02 95 00 07 02 5D 23 07 02 41 EC 06 02 25 45 07 02"
    )

    fun bl(from: Int, to: Int) = DsprePatches.thumbBl(from, to)

    fun edits(rates: ShinyRates): List<ByteEdit> = buildList {
        add(ByteEdit(PatchTarget.SyntheticOverlay, TABLE_OFFSET, ByteArray(16), rates.toBytes(), "the four rates (wild, scripted, gift, trainer)"))
        add(fill(PatchTarget.SyntheticOverlay, CODE_OFFSET, code, "shiny routines"))
        val stub = byteArrayOf(0x00, 0x4B, 0x18, 0x47) + ByteArray(4).also { it.putU32(0, (PARTY_ENTRY or 1).toLong()) }
        add(ByteEdit(PatchTarget.Arm9, ARM9_STUB, ByteArray(8) { 0xFF.toByte() }, stub, "stub that jumps to the routine for gifts and trainers"))
        for (call in wildCalls) add(ByteEdit(PatchTarget.Overlay(2), call - OVERLAY_2_ADDRESS, bl(call, NONSHINY_GENERATOR), bl(call, WILD_ENTRY), "wild encounter: roll for a shiny"))
        add(ByteEdit(PatchTarget.Overlay(2), SCRIPTED_CALL - OVERLAY_2_ADDRESS, bl(SCRIPTED_CALL, NONSHINY_GENERATOR), bl(SCRIPTED_CALL, STATIC_ENTRY), "scripted wild battle: roll for a shiny"))
        for (call in giftCalls) add(ByteEdit(PatchTarget.Arm9, call - 0x02000000, bl(call, PARTY_ADD), bl(call, 0x02000000 + ARM9_STUB), "gift Pokémon: roll for a shiny"))
        for (call in trainerCalls) add(ByteEdit(PatchTarget.Arm9, call - 0x02000000, bl(call, PARTY_ADD), bl(call, 0x02000000 + ARM9_STUB), "trainer Pokémon: roll for a shiny"))
    }


}

/**
 * The "Shiny rates" patch: lets the chance that a Pokémon is shiny be set separately for wild encounters, scripted wild battles
 * (Lugia, the Snorlax and so on), gift Pokémon and eggs, and trainers' Pokémon. The game decides shininess from the OT id and the
 * personality (`(tid ^ sid ^ pid high ^ pid low) < 8`, 8 in 65536), so the chance can not be changed with one number; instead the
 * places that make these Pokémon are hooked (`docs/asm/shiny_rates.s`, docs/formats.md "Shiny rates"):
 *
 * - wild and scripted wild battles: the four calls of `generateWildNonShinyAndAddToParty` in overlay 2 go to a routine that, at the
 *   chance set, calls `generateWildShinyAndAddToParty` instead (the game's own shiny generator, which keeps Cute Charm and Synchronize);
 * - gifts and trainers: the seven calls of `Party_AddMon` in the ARM9 go through one 8 byte stub to a routine that makes the Pokémon
 *   shiny first. A gift gets a shiny personality for the player's OT id; a trainer's Pokémon keeps its personality (so its nature and
 *   gender stay as the trainer data says) and gets an OT id that makes it shiny.
 *
 * The four rates are a table of u32 in the synthetic overlay, so they stay editable after the patch is installed ([load], [save]).
 * A rate of 0 makes the routines leave at once without drawing a random number.
 */
object ShinyRatesPatch : BytePatch(
    id = "shiny-rates",
    title = "Shiny rates",
    description = "Adds an editable chance of a shiny Pokémon for wild encounters, static battles (scripted wild battles), gifts and eggs, and trainers' Pokémon. " +
        "Installs routines in the synthetic overlay and hooks the places that make these Pokémon. All rates start at 0, which changes nothing.",
    fixedEdits = ShinyHooks.edits(ShinyRates.NONE),
    requires = DsprePatches.arm9Expansion,
) {
    /** The edits with the rates the ROM has now, so a patch whose rates were edited still counts as applied. */
    override fun editsFor(project: RomProject): FoxResult<List<ByteEdit>> = FoxResult.Ok(ShinyHooks.edits(load(project).let { (it as? FoxResult.Ok)?.value ?: ShinyRates.NONE }))

    /** The rates in the ROM, or an error when the code is not installed. */
    fun load(project: RomProject): FoxResult<ShinyRates> {
        val synth = when (val s = PatchTarget.SyntheticOverlay.read(project)) {
            is FoxResult.Ok -> s.value
            is FoxResult.Err -> return s
        }
        if (synth.size < ShinyHooks.CODE_OFFSET + ShinyHooks.code.size || !ShinyHooks.code.indices.all { synth[ShinyHooks.CODE_OFFSET + it] == ShinyHooks.code[it] }) return FoxResult.Err("The shiny rate code is not installed")
        return FoxResult.Ok(ShinyRates.parse(synth, ShinyHooks.TABLE_OFFSET))
    }

    /** Writes [rates] into the table; the code must be installed. */
    fun save(project: RomProject, rates: ShinyRates): FoxResult<Unit> {
        when (val l = load(project)) {
            is FoxResult.Err -> return l
            is FoxResult.Ok -> if (l.value == rates) return FoxResult.Ok(Unit)
        }
        val synth = (PatchTarget.SyntheticOverlay.read(project) as FoxResult.Ok).value.copyOf()
        rates.toBytes().copyInto(synth, ShinyHooks.TABLE_OFFSET)
        return PatchTarget.SyntheticOverlay.write(project, synth)
    }
}
