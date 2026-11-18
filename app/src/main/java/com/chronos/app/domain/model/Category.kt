package com.chronos.app.domain.model

/** A user category with a display name and accent color (ARGB). */
data class Category(
    val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val isDefault: Boolean = false,
) {
    companion object {
        /** Seed categories inserted on first run, with violet-family accents. */
        val SEED: List<Category> = listOf(
            Category(name = "Pessoal", colorArgb = 0xFF8B5CF6.toInt(), isDefault = true),
            Category(name = "Trabalho", colorArgb = 0xFF6366F1.toInt()),
            Category(name = "Saúde", colorArgb = 0xFF10B981.toInt()),
            Category(name = "Financeiro", colorArgb = 0xFFF59E0B.toInt()),
            Category(name = "Estudos", colorArgb = 0xFF3B82F6.toInt()),
        )
    }
}
