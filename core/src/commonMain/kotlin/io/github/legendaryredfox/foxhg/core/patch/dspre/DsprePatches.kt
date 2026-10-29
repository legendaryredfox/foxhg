/*
 * SPDX-License-Identifier: AGPL-3.0-only
 *
 * Ported from DSPRE (https://github.com/DS-Pokemon-Rom-Editor/DSPRE), copyright (c) the DSPRE contributors, licensed under the
 * GNU Affero General Public License v3.0 (see LICENSES/AGPL-3.0.txt and THIRD_PARTY_NOTICES.md).
 * Taken from DSPRE's PatchToolboxDialog and Resources/ROMToolboxDB: which bytes each toolbox patch writes and where.
 * Changes: rewritten in Kotlin for foxhg, only the HeartGold English values kept. The bytes each edit replaces were read from a clean
 * HeartGold English ROM and are checked before anything is written, which DSPRE does not do (see PatchToolboxRealRomTest).
 */
package io.github.legendaryredfox.foxhg.core.patch.dspre

import io.github.legendaryredfox.foxhg.core.game.HgssEnglishLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.patch.ByteEdit
import io.github.legendaryredfox.foxhg.core.patch.BattleBugFixPatches
import io.github.legendaryredfox.foxhg.core.patch.BattleTweakPatches
import io.github.legendaryredfox.foxhg.core.patch.BytePatch
import io.github.legendaryredfox.foxhg.core.patch.FastHpBarPatch
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.patch.RomPatch
import io.github.legendaryredfox.foxhg.core.patch.ReusableTmsPatch
import io.github.legendaryredfox.foxhg.core.patch.ShinyRatesPatch
import io.github.legendaryredfox.foxhg.core.patch.TextureAnimationsPatch
import io.github.legendaryredfox.foxhg.core.patch.edit
import io.github.legendaryredfox.foxhg.core.patch.fill
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u32

/** The ROM toolbox patches for HeartGold English. ARM9 offsets are file offsets of the decompressed ARM9 (runtime address 0x02000000 + offset). */
object DsprePatches {
    private val SYNTH = HgssEnglishLayout.syntheticOverlayAddress

    /**
     * Makes room for new code: a branch at ARM9 0xCD0 runs a small routine written into free space at the end of the ARM9 (0x110334), which
     * loads member 0 of `a/0/2/8` (the synthetic overlay) to 0x023C8000 at start-up. The member is replaced by 0x16000 empty bytes (the
     * vanilla 552 bytes are dropped, as DSPRE does) unless it is already that big. Most other code patches need this one.
     */
    val arm9Expansion: RomPatch = object : BytePatch(
        id = "arm9-expansion",
        title = "Expand ARM9 memory",
        description = "Adds a 88 KB synthetic overlay that the game loads at start-up, so new code and data can live outside the original ARM9. " +
            "Needed by the camera, building rotation and script command patches.",
        fixedEdits = listOf(
            edit(PatchTarget.Arm9, 0xCD0, "00 20 03 21", "0F F1 30 FB", "branch to the loader routine"),
            edit(
                PatchTarget.Arm9, 0x110334,
                "FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF FF",
                "FC B5 05 48 C0 46 1C 21 00 22 02 4D A8 47 00 20 03 21 FC BD 09 75 00 02 00 80 3C 02",
                "loader routine for the synthetic overlay (replaces free space)",
            ),
        ),
    ) {
        override val changes: List<String>
            get() = super.changes + "synthetic overlay: member 0 of ${HgssEnglishLayout.syntheticOverlay} becomes ${HgssEnglishLayout.syntheticOverlaySize} empty bytes (unless it already is that big)"

        override fun apply(project: RomProject): FoxResult<Unit> {
            val applied = super.apply(project)
            if (applied is FoxResult.Err) return applied
            val current = PatchTarget.SyntheticOverlay.read(project)
            if (current is FoxResult.Ok && current.value.size < project.layout.syntheticOverlaySize) {
                return PatchTarget.SyntheticOverlay.write(project, ByteArray(project.layout.syntheticOverlaySize))
            }
            return current.let { if (it is FoxResult.Err) it else FoxResult.Ok(Unit) }
        }
    }

    /**
     * DSPRE's matrix expansion: 13 constants in the ARM9 code that size and bound the overworld matrix (matrix 0), raised so it can grow
     * to twice its size. The original values are the vanilla ones read from the ROM; what each constant does exactly is not decoded here.
     */
    val matrixExpansion: RomPatch = BytePatch(
        id = "matrix-expansion",
        title = "Matrix expansion",
        description = "Lets matrix 0 (the overworld) grow to up to twice its size by raising the limits the game checks and the sizes of its buffers.",
        fixedEdits = listOf(
            edit(PatchTarget.Arm9, 0x3AEC6, "BF 01", "FF 01", "matrix 0 limit"),
            edit(PatchTarget.Arm9, 0x3AEC8, "36 01", "76 01", "matrix 0 limit"),
            edit(PatchTarget.Arm9, 0x3AF60, "89 01", "C9 01", "matrix 0 limit"),
            edit(PatchTarget.Arm9, 0x3AF7A, "09 01", "49 01", "matrix 0 limit"),
            edit(PatchTarget.Arm9, 0x3AF94, "1F 03 00 00", "3E 06 00 00", "buffer size"),
            edit(PatchTarget.Arm9, 0x3AF98, "9E 0F 00 00", "3C 1F 00 00", "buffer size"),
            edit(PatchTarget.Arm9, 0x3AFB0, "B2 0F 00 00", "50 1F 00 00", "buffer size"),
            edit(PatchTarget.Arm9, 0x3B000, "64 09 00 00", "C4 12 00 00", "buffer size"),
            edit(PatchTarget.Arm9, 0x3B110, "64 09 00 00", "C4 12 00 00", "buffer size"),
            edit(PatchTarget.Arm9, 0x3B1F8, "64 09 00 00", "C4 12 00 00", "buffer size"),
            edit(PatchTarget.Arm9, 0x3B264, "64 09 00 00", "C4 12 00 00", "buffer size"),
            edit(PatchTarget.Arm9, 0x3B090, "44 06 00 00", "84 0C 00 00", "buffer size"),
            edit(PatchTarget.Arm9, 0x3B0C4, "3E 06 00 00", "7C 0C 00 00", "buffer size"),
        ),
    )

    /** The trainer name buffer length: one byte of ARM9 code (an immediate), 8 in the vanilla game, 12 afterwards (11 usable characters). */
    val trainerNames: RomPatch = BytePatch(
        id = "trainer-names",
        title = "Trainer name expansion",
        description = "Raises the trainer name length from 7 to 11 usable characters.",
        fixedEdits = listOf(edit(PatchTarget.Arm9, 0x7342E, "08", "0C", "trainer name buffer length")),
    )

    /**
     * The dynamic camera: two hooks at the start of camera functions of overlay 1 jump into a routine written to the synthetic overlay at
     * 0x115B0 (entry points at +0x690 and +0x650 of it), plus a branch in the ARM9.
     */
    val dynamicCamera: RomPatch = BytePatch(
        id = "dynamic-camera",
        title = "Dynamic cameras",
        description = "DSPRE's dynamic camera (BDH camera positioning): adds the camera routine to the synthetic overlay and hooks the game's camera code into it.",
        fixedEdits = listOf(
            edit(PatchTarget.Arm9, 0x23174, "FF F7 06 FF", "B6 F3 2E FA", "branch to the camera routine"),
            edit(PatchTarget.Overlay(1), 0x1574C, "38 B5 82 B0 05 1C 00 20", "00 4B 18 47 41 9C 3D 02", "hook into the camera routine"),
            edit(PatchTarget.Overlay(1), 0x15864, "0B 1C 51 69 DA 68 01 4B", "00 4B 18 47 01 9C 3D 02", "hook into the camera routine"),
            fill(PatchTarget.SyntheticOverlay, 0x115B0, DspreBlobs.camera, "camera routine"),
        ),
        requires = arm9Expansion,
    )

    private const val ROTATION_PAYLOAD = 0x11CE8
    private const val ROTATION_HOOK_OVERLAY_OFFSET = 0xE1C6
    private const val ROTATION_HOOK_ADDRESS = 0x021F3AC6
    private const val ROTATION_FUNCTION = 0x02020D2C

    /**
     * Building rotation: a branch in overlay 1 (0xE1C6) runs a 20 byte routine in the synthetic overlay that calls the game's rotation matrix
     * function (0x02020D2C) and then does what the replaced two instructions did, so the rotation values of the buildings take effect.
     */
    val buildingRotation: RomPatch = BytePatch(
        id = "building-rotation",
        title = "Building rotation",
        description = "Makes the game use the rotation values stored with each building when it places them.",
        fixedEdits = run {
            val payloadAddress = SYNTH + ROTATION_PAYLOAD
            val payload = DspreBlobs.rotation.copyOf()
            thumbBl(payloadAddress + 8, ROTATION_FUNCTION).copyInto(payload, 8)
            listOf(
                edit(PatchTarget.Overlay(1), ROTATION_HOOK_OVERLAY_OFFSET, "0F A9 00 28", thumbBl(ROTATION_HOOK_ADDRESS, payloadAddress).hex(), "branch to the rotation routine"),
                fill(PatchTarget.SyntheticOverlay, ROTATION_PAYLOAD, payload, "rotation routine"),
            )
        },
        requires = arm9Expansion,
    )

    private const val SCRCMD_BLOCK = 0x200
    private const val SCRCMD_COUNT_POINTER = 0x400E0
    private const val SCRCMD_TABLE_POINTER = 0x400E4
    private const val SCRCMD_COUNT = 0xFAC90
    private const val SCRCMD_TABLE = 0xFAD00
    private const val SCRCMD_COMMANDS = 0x355
    private const val COUNT_MARKER = 0x4E554F43 // "COUN"
    private const val TABLE_MARKER = 0x4C424154 // "TABL"

    /**
     * Moves the script command table (853 entries, 4 bytes each) and its count out of the ARM9 into the synthetic overlay at 0x200, as a
     * block "COUN", count, "TABL", table, and points the two ARM9 pointers (0x400E0 count, 0x400E4 table) at it. Adds no commands, but the
     * table can then grow.
     */
    val scriptCommandRepoint: RomPatch = object : BytePatch(
        id = "script-command-repoint",
        title = "Script command table repoint",
        description = "Moves the script command table into the synthetic overlay so it can be extended with new commands.",
        requires = arm9Expansion,
    ) {
        override val changes: List<String>
            get() = listOf(
                "synthetic overlay 0x${SCRCMD_BLOCK.toString(16).uppercase()}: block with the command count and the $SCRCMD_COMMANDS entry command table copied from the ARM9",
                "ARM9 0x${SCRCMD_COUNT_POINTER.toString(16).uppercase()}: count pointer -> synthetic overlay",
                "ARM9 0x${SCRCMD_TABLE_POINTER.toString(16).uppercase()}: table pointer -> synthetic overlay",
            )

        override fun editsFor(project: RomProject): FoxResult<List<ByteEdit>> {
            val arm9 = when (val a = project.readArm9()) {
                is FoxResult.Ok -> a.value
                is FoxResult.Err -> return a
            }
            if (arm9.size < SCRCMD_TABLE + SCRCMD_COMMANDS * 4) return FoxResult.Err("ARM9 is too small for the script command table")
            if (arm9.u32(SCRCMD_COUNT) != SCRCMD_COMMANDS.toLong()) return FoxResult.Err("The script command count is not $SCRCMD_COMMANDS: this is not a vanilla HeartGold English ARM9")
            val block = ByteArray(12 + SCRCMD_COMMANDS * 4)
            block.putU32(0, COUNT_MARKER.toLong())
            block.putU32(4, SCRCMD_COMMANDS.toLong())
            block.putU32(8, TABLE_MARKER.toLong())
            arm9.copyInto(block, 12, SCRCMD_TABLE, SCRCMD_TABLE + SCRCMD_COMMANDS * 4)
            fun word(v: Int) = ByteArray(4).also { it.putU32(0, v.toLong()) }
            return FoxResult.Ok(
                listOf(
                    fill(PatchTarget.SyntheticOverlay, SCRCMD_BLOCK, block, "command table block"),
                    ByteEdit(PatchTarget.Arm9, SCRCMD_COUNT_POINTER, word(0x02000000 + SCRCMD_COUNT), word(SYNTH + SCRCMD_BLOCK + 4), "pointer to the command count"),
                    ByteEdit(PatchTarget.Arm9, SCRCMD_TABLE_POINTER, word(0x02000000 + SCRCMD_TABLE), word(SYNTH + SCRCMD_BLOCK + 12), "pointer to the command table"),
                ),
            )
        }
    }

    val all: List<RomPatch> = listOf(arm9Expansion, dynamicCamera, buildingRotation, scriptCommandRepoint, matrixExpansion, trainerNames, TextureAnimationsPatch, ShinyRatesPatch, ReusableTmsPatch, FastHpBarPatch) + BattleBugFixPatches.all + BattleTweakPatches.all

    /** A THUMB `bl` from the instruction at [source] to [target] (both runtime addresses), 4 bytes. */
    internal fun thumbBl(source: Int, target: Int): ByteArray {
        val offset = target - (source + 4)
        val first = 0xF000 or ((offset shr 12) and 0x07FF)
        val second = 0xF800 or ((offset shr 1) and 0x07FF)
        return byteArrayOf(first.toByte(), (first shr 8).toByte(), second.toByte(), (second shr 8).toByte())
    }

    private fun ByteArray.hex() = joinToString(" ") { "%02X".format(it) }
}
