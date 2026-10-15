package io.github.romulofer.foxhg.core.nds

import io.github.romulofer.foxhg.core.FoxResult
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Reads only the header, so opening a 128 MB ROM to identify it is instant. */
fun readNdsHeader(path: Path): FoxResult<NdsHeader> =
    try {
        Files.newInputStream(path).use { input ->
            val buffer = input.readNBytes(NdsHeader.SIZE)
            NdsHeader.parse(buffer)
        }
    } catch (e: IOException) {
        FoxResult.Err("Cannot read $path: ${e.message}")
    }
