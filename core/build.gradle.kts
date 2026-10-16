plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Emulator tests only (Apache-2.0, not shipped): runs a libretro core to play a ROM and read its RAM, see EmulatorDriver.
        jvmTest.dependencies {
            implementation(libs.libretro.kmp)
        }
    }
}

// libretro-kmp loads the core through JNA, which JDK 24 and later wants told about.
val emulatorPackage = "**/core/emulator/**"

tasks.named<Test>("jvmTest") {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    exclude(emulatorPackage)
}

// A libretro core keeps global state and cannot be loaded twice in one process: every emulator test class gets its own JVM.
// emulatorSetup makes the saved games the others start from (once: they are kept in build/emulator-saves), then emulatorTest runs the rest.
fun Test.emulatorTask(include: String, exclude: String? = null) {
    group = "verification"
    val jvmTest = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTest.testClassesDirs
    classpath = jvmTest.classpath
    include(include)
    exclude?.let { exclude(it) }
    forkEvery = 1
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // what they do depends on the ROM, the core and the environment, which Gradle does not track
    outputs.upToDateWhen { false }
}

val emulatorSetup = tasks.register<Test>("emulatorSetup") {
    description = "Makes the saved games of the emulator tests (plays the new game on a ROM that starts in a Poke Mart)."
    emulatorTask("**/core/emulator/Make*SaveEmulatorTest*")
}

val emulatorTest = tasks.register<Test>("emulatorTest") {
    description = "Runs the tests that boot a ROM on the DeSmuME libretro core (each class in its own JVM)."
    emulatorTask(emulatorPackage, exclude = "**/core/emulator/Make*SaveEmulatorTest*")
    dependsOn(emulatorSetup)
}

tasks.named("check") { dependsOn(emulatorTest) }
