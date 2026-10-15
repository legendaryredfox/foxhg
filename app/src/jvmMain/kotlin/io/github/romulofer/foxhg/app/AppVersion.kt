package io.github.romulofer.foxhg.app

import java.util.Properties

/** The version of the app, written into `version.properties` by the build from the packaging version. */
object AppVersion {
    val current: String = runCatching {
        AppVersion::class.java.getResourceAsStream("/version.properties")?.use { Properties().apply { load(it) }.getProperty("version") }
    }.getOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("$") } ?: "dev"
}
