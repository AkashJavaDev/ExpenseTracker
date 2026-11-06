package com.expensetracker.app.data

import com.expensetracker.app.data.Starter.FIXED_COLOR
import com.expensetracker.app.data.Starter.FIXED_NAME

/** "share" version: general-purpose categories and no data. */
object StarterData {

    val categories = listOf(
        StarterCategory(
            FIXED_NAME, colorIndex = FIXED_COLOR, isGroup = true, isFixed = true,
            parts = listOf(
                StarterCategory("Rent"),
                StarterCategory("EMI"),
                StarterCategory("Recharge", isOptional = true),
                StarterCategory("Subscriptions", isOptional = true),
                StarterCategory("Shopping", isOptional = true),
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
    )

    /** Received tab; people add, rename or delete these as they like. */
    val receivedCategories = listOf(
        StarterCategory("Salary", colorIndex = 6),
        StarterCategory("Pocket money", colorIndex = 4),
        StarterCategory("From family", colorIndex = 3),
        StarterCategory("From friends", colorIndex = 1),
    )

    val months = emptyList<StarterMonth>()
}
