import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
        mainClass = "io.github.romulofer.foxhg.app.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // extra JVM flags for `:app:run`, e.g. scripts/live-click-test.sh points user.home at a temporary folder
        System.getenv("FOXHG_JVM_ARGS")?.takeIf { it.isNotBlank() }?.let { jvmArgs += it.split(" ") }

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg)
            packageName = "foxhg"
            packageVersion = "0.5.0"
            description = "Nintendo DS Pokémon ROM editor."
            modules("java.prefs")
            linux {
                menuGroup = "Development"
                appCategory = "Development"
                shortcut = true
            }
        }
    }
}
