package com.example.domain.model

enum class ThemeMode(val label: String) {
    DARK("Dark"),
    AMOLED("Full Black"),
    LIGHT("Light"),
    PURE_WHITE("Full White"),
    SYSTEM("System")
}

enum class ThemeAccent(
    val label: String,
    val primaryHex: Long,
    val secondaryHex: Long,
    val tertiaryHex: Long
) {
    CORAL_STUDIO("Electric Coral", 0xFFFF4D6D, 0xFFFFB703, 0xFF9D4EDD),
    CYAN_PULSE("Cyber Cyan", 0xFF00D2FF, 0xFF2EC4B6, 0xFF536DFE),
    AMBER_VINYL("Amber Vinyl", 0xFFFFB703, 0xFFFB8500, 0xFFFF5D8F),
    VIOLET_NEON("Velvet Violet", 0xFFA855F7, 0xFFFF4D9D, 0xFF2EC4B6),
    EMERALD_WAVE("Emerald Wave", 0xFF2EC4B6, 0xFF80ED99, 0xFF4EA8DE),
    CRIMSON_STAGE("Crimson Stage", 0xFFFF3355, 0xFFFF9EBB, 0xFFFFCA3A),
    MONOCHROME("Mono B&W", 0xFFF2F2F7, 0xFFB8B8C4, 0xFF7E7E8A)
}

enum class ThemeCornerStyle(
    val label: String,
    val smallDp: Int,
    val mediumDp: Int,
    val largeDp: Int
) {
    ROUNDED("Soft Studio", 10, 16, 22),
    PILL("Plush Pill", 16, 24, 28),
    SHARP("Crisp Studio", 4, 8, 12)
}

enum class PlayerBackdropStyle(val label: String) {
    GRADIENT("Accent Gradient"),
    ARTWORK_BLUR("Album Art Glow"),
    MINIMAL("Clean Surface")
}
