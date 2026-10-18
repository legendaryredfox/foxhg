package io.github.legendaryredfox.foxhg.core.data.battlerule

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** Where one byte of a battle rule lives: in the decompressed overlay [id] or in file [file] of the NARC [narc]. */
sealed interface BattleSite {
    val offset: Int

    data class Overlay(val id: Int, override val offset: Int) : BattleSite
    data class NarcFile(val narc: String, val file: Int, override val offset: Int) : BattleSite
}

/**
 * One number of the battle logic that a single byte (or the same byte in several places) sets, from the wiki guide "Battle Logic Edits": a
 * divisor of the maximum HP, a chance, a multiplier or a number of turns. [vanilla] is what HeartGold English has at every one of [sites]; [explain]
 * says in words what a value does.
 */
class BattleRule(
    val id: String,
    val group: String,
    val name: String,
    val description: String,
    val sites: List<BattleSite>,
    val vanilla: Int,
    val range: IntRange,
    val explain: (Int) -> String,
)

/**
 * The simple battle tweaks of the guide for HeartGold English: each is one byte in an overlay (an immediate of a Thumb instruction of the battle
 * code) or in a battle script (a NARC file), so a rule is read and written byte by byte. Tweaks that need a table or several bytes that change together
 * in another way (paralysis shifts, Magnitude, Flail, Disable, Taunt, hail, critical hits, the bug fixes) are not here.
 */
object BattleRules {
    const val SUB_SEQ = "a/0/0/1"
    const val BE_SEQ = "a/0/3/0"
    private const val BATTLE_OVERLAY = 12

    private fun ov(vararg offsets: Int) = offsets.map { BattleSite.Overlay(BATTLE_OVERLAY, it) }
    private fun chance(n: Int) = if (n <= 0) "" else "1 in $n (about ${"%.1f".format(100.0 / n).removeSuffix(".0")} %)"
    private fun share(n: Int) = "1/$n of the maximum HP"
    private fun turns(n: Int) = if (n == 1) "1 turn" else "$n turns"

    val all: List<BattleRule> = listOf(
        // statuses
        BattleRule("burn-damage", "Statuses", "Burn damage", "A burned Pokémon loses the maximum HP divided by this each turn (battle script 1_26).",
            listOf(BattleSite.NarcFile(SUB_SEQ, 26, 0x30)), 8, 1..255) { share(it) },
        BattleRule("sleep-range", "Statuses", "Sleep: random extra turns", "Sleep lasts a random 0 to this many turns on top of the base (battle script 1_18).",
            listOf(BattleSite.NarcFile(SUB_SEQ, 18, 0x264)), 3, 0..255) { "0 to ${turns(it)} extra" },
        BattleRule("sleep-base", "Statuses", "Sleep: base turns", "The fixed part of the sleep duration; the Pokémon can not move for one turn less than the total.",
            listOf(BattleSite.NarcFile(SUB_SEQ, 18, 0x268)), 2, 0..255) { "${turns(it)} at least" },
        BattleRule("freeze-thaw", "Statuses", "Freeze thaw chance", "Each turn a random number is divided by this, and a remainder of 0 thaws the Pokémon.",
            ov(0x13E12), 5, 1..255) { chance(it) },
        // abilities
        BattleRule("iron-fist-multiplier", "Abilities", "Iron Fist: multiplier", "Power of punching moves is multiplied by this and divided by the divisor (12 and 10 make +20 %).",
            ov(0x1FF44), 12, 1..255) { "x$it" },
        BattleRule("iron-fist-divisor", "Abilities", "Iron Fist: divisor", "See the multiplier.", ov(0x1FF48), 10, 1..255) { "/$it" },
        BattleRule("pickup", "Abilities", "Pickup activation chance", "Each turn a random number is divided by this, and a remainder of 0 lets Pickup act.",
            ov(0xC852), 10, 1..255) { chance(it) },
        BattleRule("ice-body", "Abilities", "Ice Body healing", "HP restored each turn in hail.", ov(0xA3F6), 16, 1..255) { share(it) },
        BattleRule("rain-dish", "Abilities", "Rain Dish healing", "HP restored each turn in rain.", ov(0xA46C), 16, 1..255) { share(it) },
        BattleRule("heatproof", "Abilities", "Heatproof burn divisor", "Burn damage is divided by this for a Heatproof Pokémon (1 means no protection).",
            listOf(BattleSite.NarcFile(SUB_SEQ, 26, 0x50)), 2, 1..255) { "burn damage /$it" },
        BattleRule("quick-feet-multiplier", "Abilities", "Quick Feet: speed multiplier", "Speed is multiplied by this and divided by the divisor (15 and 10 make +50 %). Both battlers' copies of the code.",
            ov(0x185AE, 0x1875A), 15, 1..255) { "x$it" },
        BattleRule("quick-feet-divisor", "Abilities", "Quick Feet: speed divisor", "See the multiplier.", ov(0x185B2, 0x1875E), 10, 1..255) { "/$it" },
        BattleRule("slow-start", "Abilities", "Slow Start duration", "Turns the stats stay halved after switching in (four places in the code).",
            ov(0x185E2, 0x1878E, 0x1C2A8, 0x1FA38), 5, 1..255) { turns(it) },
        BattleRule("reckless", "Abilities", "Reckless: multiplier", "Power of recoil and crash moves is multiplied by this and divided by 10 (12 makes +20 %). Six move effect scripts.",
            listOf(45, 48, 198, 253, 262, 269).map { BattleSite.NarcFile(BE_SEQ, it, 0x20) }, 12, 1..255) { "x$it/10" },
        BattleRule("rivalry-same-multiplier", "Abilities", "Rivalry, same gender: multiplier", "Power is multiplied by this and divided by the divisor (125 and 100 make +25 %).",
            ov(0x1FF00), 125, 1..255) { "x$it" },
        BattleRule("rivalry-same-divisor", "Abilities", "Rivalry, same gender: divisor", "See the multiplier.", ov(0x1FF04), 100, 1..255) { "/$it" },
        BattleRule("rivalry-opposite-multiplier", "Abilities", "Rivalry, opposite gender: multiplier", "Power is multiplied by this and divided by the divisor (75 and 100 make -25 %).",
            ov(0x1FF26), 75, 1..255) { "x$it" },
        BattleRule("rivalry-opposite-divisor", "Abilities", "Rivalry, opposite gender: divisor", "See the multiplier.", ov(0x1FF2A), 100, 1..255) { "/$it" },
        // items
        BattleRule("leftovers", "Items", "Leftovers healing", "HP restored each turn (set in the battle code, not in the item data).", ov(0x1D508), 16, 1..255) { share(it) },
        BattleRule("black-sludge-heal", "Items", "Black Sludge healing (Poison types)", "HP restored each turn to a Poison type.", ov(0x1D548), 16, 1..255) { share(it) },
        BattleRule("black-sludge-damage", "Items", "Black Sludge damage (other types)", "HP lost each turn by a Pokémon that is not a Poison type.", ov(0x1D570), 8, 1..255) { share(it) },
        BattleRule("x-item-stages", "Items", "X items: stages raised", "Stages the six X items (Attack, Defense, Special, Sp. Def, Speed, Accuracy) raise a stat by in battle.",
            ov(0x372A, 0x3768, 0x37A6, 0x37E4, 0x3822, 0x3860), 1, 1..6) { if (it == 1) "1 stage" else "$it stages" },
        // move effects
        BattleRule("aqua-ring", "Move effects", "Aqua Ring healing", "HP restored each turn.", ov(0x1257A), 16, 1..255) { share(it) },
        BattleRule("binding-damage", "Move effects", "Binding moves damage", "HP lost each turn when trapped by Bind, Wrap, Fire Spin, Whirlpool and the others.", ov(0x12852), 16, 1..255) { share(it) },
        BattleRule("binding-range", "Move effects", "Binding moves: random extra turns", "Trapping lasts a random 0 to this many turns on top of the base (battle script 1_58).",
            listOf(BattleSite.NarcFile(SUB_SEQ, 58, 0x38)), 3, 0..255) { "0 to ${turns(it)} extra" },
        BattleRule("binding-base", "Move effects", "Binding moves: base turns", "The fixed part of the trapping duration.",
            listOf(BattleSite.NarcFile(SUB_SEQ, 58, 0x3C)), 3, 0..255) { "${turns(it)} at least" },
        BattleRule("grip-claw", "Move effects", "Grip Claw duration", "Turns a binding move lasts when the user holds a Grip Claw (the game does not take more than 7).",
            listOf(BattleSite.NarcFile(SUB_SEQ, 58, 0x60)), 6, 1..7) { turns(it) },
        BattleRule("encore", "Move effects", "Encore duration (base)", "Turns Encore lasts, before the extra random part the code adds.", ov(0x939A), 5, 1..255) { turns(it) },
    )

    fun byId(id: String): BattleRule? = all.firstOrNull { it.id == id }

    /** The byte at [site], or an error naming it. */
    private fun readSite(project: RomProject, site: BattleSite): FoxResult<Int> {
        val bytes = when (site) {
            is BattleSite.Overlay -> when (val o = project.readOverlay(site.id)) {
                is FoxResult.Ok -> o.value
                is FoxResult.Err -> return o
            }
            is BattleSite.NarcFile -> {
                val narc = when (val n = Narc.parse(project.read(site.narc) ?: return FoxResult.Err("The ROM has no ${site.narc}"))) {
                    is FoxResult.Ok -> n.value
                    is FoxResult.Err -> return n
                }
                narc.files.getOrNull(site.file) ?: return FoxResult.Err("${site.narc} has no file ${site.file}")
            }
        }
        if (site.offset !in bytes.indices) return FoxResult.Err("Offset 0x${site.offset.toString(16).uppercase()} is outside the file")
        return FoxResult.Ok(bytes[site.offset].toInt() and 0xFF)
    }

    /** The value of [rule]: what every site holds. An error says when the sites disagree (another tool changed one of them). */
    fun read(project: RomProject, rule: BattleRule): FoxResult<Int> {
        val values = rule.sites.map { when (val v = readSite(project, it)) { is FoxResult.Ok -> v.value; is FoxResult.Err -> return v } }
        if (values.distinct().size != 1) return FoxResult.Err("${rule.name}: the places hold different values $values; set one value to make them agree")
        return FoxResult.Ok(values[0])
    }

    /** The value at the first site, even when the others differ. */
    fun readFirst(project: RomProject, rule: BattleRule): FoxResult<Int> = readSite(project, rule.sites[0])

    /** Writes [value] into every site of [rule]. */
    fun write(project: RomProject, rule: BattleRule, value: Int): FoxResult<Unit> {
        if (value !in rule.range) return FoxResult.Err("${rule.name} is ${rule.range.first} to ${rule.range.last}")
        for (site in rule.sites) readSite(project, site).let { if (it is FoxResult.Err) return it }
        // overlays one by one, each NARC once with all its files
        for ((id, sites) in rule.sites.filterIsInstance<BattleSite.Overlay>().groupBy { it.id }) {
            val overlay = when (val o = project.readOverlay(id)) { is FoxResult.Ok -> o.value; is FoxResult.Err -> return o }
            sites.forEach { overlay[it.offset] = value.toByte() }
            project.writeOverlay(id, overlay).let { if (it is FoxResult.Err) return it }
        }
        for ((path, sites) in rule.sites.filterIsInstance<BattleSite.NarcFile>().groupBy { it.narc }) {
            val narc = (Narc.parse(project.read(path)!!) as FoxResult.Ok).value
            val files = narc.files.map { it.copyOf() }
            sites.forEach { files[it.file][it.offset] = value.toByte() }
            project.write(path, Narc(files, narc.nameTable).toBytes()).let { if (it is FoxResult.Err) return it }
        }
        return FoxResult.Ok(Unit)
    }
}
