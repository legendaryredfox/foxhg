package io.github.romulofer.foxhg.app

/** Planned editors, grouped as in docs/spec.md section 4. Those with [implemented] open a real editor; the rest are placeholders (see docs/plan.md). */
enum class EditorKind(val group: String, val title: String, val implemented: Boolean = false) {
    PERSONAL("Pokémon", "Personal data", implemented = true),
    LEARNSET("Pokémon", "Learnsets", implemented = true),
    EVOLUTIONS("Pokémon", "Evolutions", implemented = true),
    EGG_MOVES("Pokémon", "Egg moves", implemented = true),
    TRADES("Pokémon", "In-game trades", implemented = true),
    TMHM("Moves and items", "TMs and HMs", implemented = true),
    MOVES("Moves and items", "Moves", implemented = true),
    ITEMS("Moves and items", "Items", implemented = true),
    TRAINERS("Trainers", "Trainers", implemented = true),
    TRAINER_CLASSES("Trainers", "Trainer classes", implemented = true),
    ENCOUNTERS("World", "Wild encounters", implemented = true),
    HEADERS("World", "Map headers", implemented = true),
    MATRICES("World", "Matrices", implemented = true),
    MAPS("World", "Maps", implemented = true),
    EVENTS("World", "Events", implemented = true),
    SCRIPTS("Scripts and text", "Scripts", implemented = true),
    TEXT("Scripts and text", "Text archives", implemented = true),
    SOUNDS("Audio", "Sounds", implemented = true),
    FILES("Tools", "Files", implemented = true),
}
