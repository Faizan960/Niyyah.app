package com.salahlock.app.data.model

enum class BlockProfile(
    val displayName: String,
    val description: String,
    val blockedCategories: Set<AppCategory>,
) {
    MINIMAL(
        displayName = "Minimal",
        description = "Social apps only",
        blockedCategories = setOf(AppCategory.SOCIAL),
    ),
    BALANCED(
        displayName = "Balanced",
        description = "Social + Entertainment + Games",
        blockedCategories = setOf(AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.GAMES),
    ),
    STRICT(
        displayName = "Strict",
        description = "Almost everything",
        blockedCategories = setOf(
            AppCategory.SOCIAL, AppCategory.ENTERTAINMENT, AppCategory.GAMES,
            AppCategory.SHOPPING, AppCategory.UTILITIES,
        ),
    ),
    CUSTOM(
        displayName = "Custom",
        description = "Your own selection",
        blockedCategories = emptySet(),
    );

    companion object {
        fun fromName(name: String): BlockProfile =
            entries.firstOrNull { it.name == name } ?: CUSTOM
    }
}
