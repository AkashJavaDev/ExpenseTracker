package com.expensetracker.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A spending category. Top-level categories have no [parentId].
 * A group (like Food) holds sub-categories instead of expenses.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val isGroup: Boolean = false,
    val colorIndex: Int = 0,
    val sortOrder: Int = 0,
    /** Hidden on Home in months where nothing was spent on it (like Dress). */
    @ColumnInfo(defaultValue = "0") val isOptional: Boolean = false,
    /** The "Fixed & one-time" group; Home shows its total apart from daily spending. */
    @ColumnInfo(defaultValue = "0") val isFixed: Boolean = false,
    /** Money coming in (Received tab) rather than money spent. */
    @ColumnInfo(defaultValue = "0") val isReceived: Boolean = false,
)

/** One amount spent. Stored in paise so totals never pick up rounding errors. */
@Entity(tableName = "expenses", indices = [Index("categoryId"), Index("date")])
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val amountPaise: Long,
    /** Epoch millis; only used to place the expense in the right month. */
    val date: Long,
)
