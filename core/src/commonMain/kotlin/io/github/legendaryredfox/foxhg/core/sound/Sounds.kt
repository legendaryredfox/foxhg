package io.github.legendaryredfox.foxhg.core.sound

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * What the editors play: sound effects and music by sequence id, species cries. Reads the archive of the ROM (it is not
 * editable here) and keeps the rendered clips of the sounds it was asked for.
 */
class Sounds(project: RomProject) {
    val sdat: Sdat? = project.read(project.layout.soundArchive)?.let { (Sdat.parse(it) as? FoxResult.Ok)?.value }
    private val synth = sdat?.let { Synth(it) }
    private val cache = HashMap<String, PcmClip?>()

    /** Sound ids (sequence numbers) that have a name and data, in order. */
    val sequences: List<SequenceInfo> get() = sdat?.sequences?.filter { it.file >= 0 && it.name.isNotEmpty() } ?: emptyList()

    fun name(id: Int): String? = sdat?.sequence(id)?.name

    /** Whether sequence [id] exists (music, sound effect, jingle, ...). */
    fun has(id: Int): Boolean = sdat?.sequence(id) != null

    /** The sound with sequence number [id]: sound effects, jingles and music. */
    fun sequence(id: Int): PcmClip? = cache.getOrPut("s$id") { synth?.render(id, maxSeconds = if (isMusic(id)) 45.0 else 12.0) }

    /** The cry of species [species] (1..493): the shared cry sequence played with that species' bank. */
    fun cry(species: Int): PcmClip? = cache.getOrPut("c$species") { if (species !in 1..MAX_CRY) null else synth?.render(CRY_SEQUENCE, bankOverride = species, maxSeconds = 5.0) }

    private fun isMusic(id: Int): Boolean = sdat?.sequence(id)?.name?.startsWith("SEQ_GS_") == true

    companion object {
        /** `SEQ_PV001`: the sequence every cry plays, with the bank `BANK_PV<species>`. */
        const val CRY_SEQUENCE = 1
        const val MAX_CRY = 493
    }
}
