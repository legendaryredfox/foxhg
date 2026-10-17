import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val appVersion = "0.7.0"

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":core"))
            implementation(compose.desktop.currentOs)
            implementation(compose.material3)
            implementation(libs.kotlinx.coroutines.swing)
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.github.legendaryredfox.foxhg.app.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // extra JVM flags for `:app:run`, e.g. scripts/live-click-test.sh points user.home at a temporary folder
        System.getenv("FOXHG_JVM_ARGS")?.takeIf { it.isNotBlank() }?.let { jvmArgs += it.split(" ") }

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "foxhg"
            packageVersion = appVersion
            description = "Nintendo DS Pokémon ROM editor."
            modules("java.prefs")
            linux {
                menuGroup = "Development"
                appCategory = "Development"
                shortcut = true
                iconFile.set(project.file("icons/foxhg-512.png"))
            }
            windows {
                iconFile.set(project.file("icons/foxhg.ico"))
            }
            macOS {
                iconFile.set(project.file("icons/foxhg.icns"))
            }
        }
    }
}

// the window shows the version, read from this resource
tasks.named<ProcessResources>("jvmProcessResources") {
    filesMatching("version.properties") { expand("version" to appVersion) }
}
