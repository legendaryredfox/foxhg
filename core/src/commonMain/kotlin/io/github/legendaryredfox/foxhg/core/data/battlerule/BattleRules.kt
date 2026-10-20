package io.github.legendaryredfox.foxhg.core.data.battlerule

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * Where one byte of a battle rule lives: in the decompressed overlay [id] or in file [file] of the NARC [narc]. The byte holds
 * `base + value * scale`; the usual rule stores the value as it is (scale 1, base 0), a rule that is a field of a Thumb instruction does not.
 */
sealed interface BattleSite {
    val offset: Int
    val scale: Int
    val base: Int

    fun encode(value: Int): Int = base + value * scale

    /** The rule value this byte holds, or null when the byte is not `base + n * scale`. */
    fun decode(byte: Int): Int? = (byte - base).takeIf { it >= 0 && it % scale == 0 }?.div(scale)

    data class Overlay(val id: Int, override val offset: Int, override val scale: Int = 1, override val base: Int = 0) : BattleSite
    data class NarcFile(val narc: String, val file: Int, override val offset: Int, override val scale: Int = 1, override val base: Int = 0) : BattleSite
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
 * code) or in a battle script (a NARC file), so a rule is read and written byte by byte; the critical hit table is five rules, one byte each. Tweaks that
 * need several bytes that change together in another way (Flail, Disable, no hail damage, the bug fixes) are not here. The paralysis Speed cut is a field of a Thumb shift instruction (the shift count sits in the two top bits of its low byte), so its sites encode the value.
 */
object BattleRules {
    const val SUB_SEQ = "a/0/0/1"
    const val BE_SEQ = "a/0/3/0"
    private const val BATTLE_OVERLAY = 12
    private const val TRAINER_AI_OVERLAY = 10

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
        BattleRule("paralysis-shift", "Statuses", "Paralysis Speed cut (shifts)", "Speed is shifted right by this many bits when paralyzed: 1 halves it, 2 (the game) leaves a quarter, 3 an eighth. Both battlers' copies of the code.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0x185CA, 64, 0x36), BattleSite.Overlay(BATTLE_OVERLAY, 0x18776, 64, 0x24)), 2, 1..3) { n -> "Speed x1/${1 shl n} (${100 - 100 / (1 shl n)} % cut)" },
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
        BattleRule("hail-damage", "Weather", "Hail damage", "HP lost each turn in hail by a Pokémon that is not an Ice type and has no Snow Cloak.", ov(0xA42C), 16, 1..255) { share(it) },
        BattleRule("crit-stage-0", "Critical hits", "Critical hit chance, stage 0", "A random number is divided by this, and a remainder of 0 is a critical hit (the table holds one number for each critical hit stage, 0 to +4).",
            ov(0x35288), 16, 1..255) { chance(it) },
        BattleRule("crit-stage-1", "Critical hits", "Critical hit chance, stage +1", "See stage 0 (the game has 8 here).", ov(0x35289), 8, 1..255) { chance(it) },
        BattleRule("crit-stage-2", "Critical hits", "Critical hit chance, stage +2", "See stage 0 (the game has 4 here).", ov(0x3528A), 4, 1..255) { chance(it) },
        BattleRule("crit-stage-3", "Critical hits", "Critical hit chance, stage +3", "See stage 0 (the game has 3 here).", ov(0x3528B), 3, 1..255) { chance(it) },
        BattleRule("crit-stage-4", "Critical hits", "Critical hit chance, stage +4", "See stage 0 (the game has 2 here).", ov(0x3528C), 2, 1..255) { chance(it) },
        BattleRule("magnitude-4", "Move effects", "Magnitude 4 power", "Power of Magnitude when the roll is 4; the Trainer AI keeps its own copy of the table in overlay 10, written together.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0xA89A), BattleSite.Overlay(TRAINER_AI_OVERLAY, 0x351C)), 10, 1..255) { "power $it" },
        BattleRule("magnitude-5", "Move effects", "Magnitude 5 power", "Power of Magnitude when the roll is 5; the Trainer AI keeps its own copy of the table in overlay 10, written together.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0xA8AA), BattleSite.Overlay(TRAINER_AI_OVERLAY, 0x3524)), 30, 1..255) { "power $it" },
        BattleRule("magnitude-6", "Move effects", "Magnitude 6 power", "Power of Magnitude when the roll is 6; the Trainer AI keeps its own copy of the table in overlay 10, written together.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0xA8BA), BattleSite.Overlay(TRAINER_AI_OVERLAY, 0x352C)), 50, 1..255) { "power $it" },
        BattleRule("magnitude-7", "Move effects", "Magnitude 7 power", "Power of Magnitude when the roll is 7; the Trainer AI keeps its own copy of the table in overlay 10, written together.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0xA8CA), BattleSite.Overlay(TRAINER_AI_OVERLAY, 0x3534)), 70, 1..255) { "power $it" },
        BattleRule("magnitude-8", "Move effects", "Magnitude 8 power", "Power of Magnitude when the roll is 8; the Trainer AI keeps its own copy of the table in overlay 10, written together.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0xA8DA), BattleSite.Overlay(TRAINER_AI_OVERLAY, 0x353C)), 90, 1..255) { "power $it" },
        BattleRule("magnitude-9", "Move effects", "Magnitude 9 power", "Power of Magnitude when the roll is 9; the Trainer AI keeps its own copy of the table in overlay 10, written together.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0xA8EA), BattleSite.Overlay(TRAINER_AI_OVERLAY, 0x3544)), 110, 1..255) { "power $it" },
        BattleRule("magnitude-10", "Move effects", "Magnitude 10 power", "Power of Magnitude when the roll is 10; the Trainer AI keeps its own copy of the table in overlay 10, written together.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0xA8F4), BattleSite.Overlay(TRAINER_AI_OVERLAY, 0x3548)), 150, 1..255) { "power $it" },
        BattleRule("gyro-ball", "Move effects", "Gyro Ball power multiplier", "The target's Speed divided by the user's is multiplied by this to get the power (at most 150); the Trainer AI has its own copy in overlay 10, written together.",
            listOf(BattleSite.Overlay(BATTLE_OVERLAY, 0xBEAA), BattleSite.Overlay(TRAINER_AI_OVERLAY, 0x347C)), 25, 1..255) { "x$it" },
        BattleRule("taunt-range", "Move effects", "Taunt: random extra turns", "Taunt lasts a random 0 to this many turns on top of the base (battle script 1_132). 0 here and a base of 4 make it always last 4.",
            listOf(BattleSite.NarcFile(SUB_SEQ, 132, 0x38)), 2, 0..255) { "0 to ${turns(it)} extra" },
        BattleRule("taunt-base", "Move effects", "Taunt: base turns", "The fixed part of the Taunt duration; the target can use a move one turn less when the user is slower.",
            listOf(BattleSite.NarcFile(SUB_SEQ, 132, 0x3C)), 3, 0..255) { "${turns(it)} at least" },
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
        val byte = bytes[site.offset].toInt() and 0xFF
        return site.decode(byte)?.let { FoxResult.Ok(it) } ?: FoxResult.Err("Offset 0x${site.offset.toString(16).uppercase()} holds 0x${byte.toString(16).uppercase()}, which is not a value of this rule")
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
            sites.forEach { overlay[it.offset] = it.encode(value).toByte() }
            project.writeOverlay(id, overlay).let { if (it is FoxResult.Err) return it }
        }
        for ((path, sites) in rule.sites.filterIsInstance<BattleSite.NarcFile>().groupBy { it.narc }) {
            val narc = (Narc.parse(project.read(path)!!) as FoxResult.Ok).value
            val files = narc.files.map { it.copyOf() }
            sites.forEach { files[it.file][it.offset] = it.encode(value).toByte() }
            project.write(path, Narc(files, narc.nameTable).toBytes()).let { if (it is FoxResult.Err) return it }
        }
        return FoxResult.Ok(Unit)
    }
}
