package io.github.romulofer.foxhg.core.patch

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.narc.Narc
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.u16

/**
 * Turns the animated map textures off: the "dynamic texture" field (u16 at offset 4) of every area data file is set to 0xFFFF, the value for
 * "none". The behaviour of DSPRE's "Disable Texture Animations" patch, written from the area data layout in docs/formats.md.
 */
object TextureAnimationsPatch : RomPatch {
    private const val FIELD = 4
    private const val NONE = 0xFFFF

    override val id = "disable-texture-animations"
    override val title = "Disable texture animations"
    override val description = "Sets the dynamic texture field of every area data file to none, so no map plays animated textures (water, flowers)."
    override val changes = listOf("${HgssLayout.AREA_DATA}: u16 at offset $FIELD of every file -> 0xFFFF")

    private fun narc(project: RomProject): FoxResult<Narc> {
        val bytes = project.read(HgssLayout.AREA_DATA) ?: return FoxResult.Err("The ROM has no ${HgssLayout.AREA_DATA}")
        return Narc.parse(bytes)
    }

    override fun status(project: RomProject): PatchStatus {
        val n = when (val r = narc(project)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return PatchStatus.Unavailable(r.message)
        }
        if (n.files.any { it.size < FIELD + 2 }) return PatchStatus.Unavailable("An area data file is shorter than expected: this is not a HeartGold English ROM")
        return if (n.files.all { it.u16(FIELD) == NONE }) PatchStatus.Applied else PatchStatus.Available
    }

    override fun apply(project: RomProject): FoxResult<Unit> {
        when (val s = status(project)) {
            PatchStatus.Available -> Unit
            PatchStatus.Applied -> return FoxResult.Err("\"$title\" is already applied")
            is PatchStatus.Unavailable -> return FoxResult.Err(s.reason)
        }
        val n = (narc(project) as FoxResult.Ok).value
        val files = n.files.map { f -> f.copyOf().also { it.putU16(FIELD, NONE) } }
        return project.write(HgssLayout.AREA_DATA, Narc(files, n.nameTable).toBytes())
    }
}
