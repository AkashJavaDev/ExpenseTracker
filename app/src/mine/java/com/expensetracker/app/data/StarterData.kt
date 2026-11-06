package com.expensetracker.app.data

import com.expensetracker.app.data.Starter.FIXED_COLOR
import com.expensetracker.app.data.Starter.FIXED_NAME

/** "mine" version: personal categories plus the private spending notes, if present. */
object StarterData {

    val categories = listOf(
        StarterCategory(
            FIXED_NAME, colorIndex = FIXED_COLOR, isGroup = true, isFixed = true,
            parts = listOf(
                StarterCategory("EMI"),
                StarterCategory("Rent"),
                StarterCategory("Claude", isOptional = true),
                StarterCategory("Recharge", isOptional = true),
                StarterCategory("Dress", isOptional = true),
                StarterCategory("Earbuds", isOptional = true),
            ),
        ),
        StarterCategory(
            "Food", colorIndex = 5, isGroup = true,
            parts = listOf(
                StarterCategory("Morning"),
                StarterCategory("Afternoon"),
                StarterCategory("Night"),
                StarterCategory("Snacks"),
            ),
        ),
        StarterCategory("Travel", colorIndex = 6),
        StarterCategory("Others", colorIndex = 7),
        StarterCategory("Sended", colorIndex = 8),
    )

    /** No Received tab in this version. */
    val receivedCategories = emptyList<StarterCategory>()

    /** Personal spending notes: src/minePrivate (not in git), or none from src/minePublic. */
    val months = MineMonths.months
}
