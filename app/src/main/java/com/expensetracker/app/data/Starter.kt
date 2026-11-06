package com.expensetracker.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate
import java.time.ZoneId

/** A category created on first launch. A group lists its [parts]. */
class StarterCategory(
    val name: String,
    val colorIndex: Int = 0,
    val isGroup: Boolean = false,
    val isFixed: Boolean = false,
    val isOptional: Boolean = false,
    val parts: List<StarterCategory> = emptyList(),
)

/**
 * Amounts for one category, found by name. If no such category exists yet it is
 * created inside [group] (or as its own card when [group] is null).
 */
class StarterEntries(
    val category: String,
    val group: String?,
    val rupees: List<Int>,
    val isOptional: Boolean = false,
)

/** A month of past spending. All entries go on the 1st, since the notes have no dates. */
class StarterMonth(val year: Int, val month: Int, val entries: List<StarterEntries>) {
    val key: String get() = "%04d-%02d".format(year, month)
}

/**
 * Puts the build flavor's [StarterData] into the database: categories on first launch,
 * and each month exactly once (also on phones that already have the app installed).
 */
object Starter {
    const val FIXED_NAME = "Fixed & one-time"
    const val FIXED_COLOR = 2

    fun createCategories(db: SupportSQLiteDatabase) {
        insertTree(db, StarterData.categories, isReceived = false)
        createReceivedCategories(db)
    }

    /** The Received tab's starting categories (none in versions without that tab). */
    fun createReceivedCategories(db: SupportSQLiteDatabase) {
        insertTree(db, StarterData.receivedCategories, isReceived = true)
    }

    private fun insertTree(db: SupportSQLiteDatabase, categories: List<StarterCategory>, isReceived: Boolean) {
        categories.forEachIndexed { index, top ->
            val topId = insertCategory(db, top, null, top.colorIndex, index, isReceived)
            top.parts.forEachIndexed { partIndex, part ->
                insertCategory(db, part, topId, top.colorIndex, partIndex, isReceived)
            }
        }
    }

    fun importMonths(db: SupportSQLiteDatabase) {
        if (StarterData.months.isEmpty()) return
        db.execSQL("CREATE TABLE IF NOT EXISTS starter_imports (monthKey TEXT NOT NULL PRIMARY KEY)")
        StarterData.months.forEach { month ->
            val done = db.query("SELECT 1 FROM starter_imports WHERE monthKey = ?", arrayOf(month.key))
                .use { it.moveToFirst() }
            if (done) return@forEach

            db.beginTransaction()
            try {
                // A month that already has expenses (like October from the earlier built-in data)
                // is only marked as done, so nothing is doubled.
                if (!hasExpenses(db, month)) insertMonth(db, month)
                db.insert("starter_imports", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
                    put("monthKey", month.key)
                })
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun insertMonth(db: SupportSQLiteDatabase, month: StarterMonth) {
        val date = LocalDate.of(month.year, month.month, 1).atTime(12, 0).toMillis()
        month.entries.forEach { entries ->
            val categoryId = findOrCreate(db, entries)
            entries.rupees.forEach { amount ->
                db.insert("expenses", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
                    put("categoryId", categoryId)
                    put("amountPaise", amount * 100L)
                    put("date", date)
                })
            }
        }
    }

    private fun hasExpenses(db: SupportSQLiteDatabase, month: StarterMonth): Boolean {
        val start = LocalDate.of(month.year, month.month, 1).atStartOfDay()
        return db.query(
            "SELECT 1 FROM expenses WHERE date >= ? AND date < ? LIMIT 1",
            arrayOf(start.toMillis(), start.plusMonths(1).toMillis()),
        ).use { it.moveToFirst() }
    }

    private fun findOrCreate(db: SupportSQLiteDatabase, entries: StarterEntries): Long {
        // Starter months are spending only, so Received categories are never matched.
        db.query(
            "SELECT id FROM categories WHERE isGroup = 0 AND isReceived = 0 AND lower(trim(name)) = ? ORDER BY id LIMIT 1",
            arrayOf(entries.category.trim().lowercase()),
        ).use { if (it.moveToFirst()) return it.getLong(0) }

        val group = entries.group?.let { findGroup(db, it) }
        val parentId = group?.first
        return insertCategory(
            db,
            StarterCategory(entries.category, isOptional = entries.isOptional),
            parentId = parentId,
            colorIndex = group?.second ?: nextTopLevelColor(db),
            sortOrder = nextSortOrder(db, parentId),
            isReceived = false,
        )
    }

    /** (id, colorIndex) of the group with this name; the fixed group is also found if renamed. */
    private fun findGroup(db: SupportSQLiteDatabase, name: String): Pair<Long, Int>? {
        db.query(
            "SELECT id, colorIndex FROM categories WHERE isGroup = 1 AND isReceived = 0 AND lower(trim(name)) = ? LIMIT 1",
            arrayOf(name.trim().lowercase()),
        ).use { if (it.moveToFirst()) return it.getLong(0) to it.getInt(1) }
        if (name != FIXED_NAME) return null
        return db.query("SELECT id, colorIndex FROM categories WHERE isGroup = 1 AND isFixed = 1 LIMIT 1")
            .use { if (it.moveToFirst()) it.getLong(0) to it.getInt(1) else null }
    }

    private fun nextTopLevelColor(db: SupportSQLiteDatabase): Int =
        db.query("SELECT COALESCE(MAX(colorIndex), -1) + 1 FROM categories WHERE parentId IS NULL")
            .use { it.moveToFirst(); it.getInt(0) }

    private fun nextSortOrder(db: SupportSQLiteDatabase, parentId: Long?): Int =
        db.query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories WHERE parentId IS ?", arrayOf(parentId))
            .use { it.moveToFirst(); it.getInt(0) }

    private fun insertCategory(
        db: SupportSQLiteDatabase,
        category: StarterCategory,
        parentId: Long?,
        colorIndex: Int,
        sortOrder: Int,
        isReceived: Boolean,
    ): Long = db.insert("categories", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
        put("name", category.name)
        put("parentId", parentId)
        put("isGroup", if (category.isGroup) 1 else 0)
        put("isOptional", if (category.isOptional) 1 else 0)
        put("isFixed", if (category.isFixed) 1 else 0)
        put("isReceived", if (isReceived) 1 else 0)
        put("colorIndex", colorIndex)
        put("sortOrder", sortOrder)
    })

    private fun java.time.LocalDateTime.toMillis(): Long =
        atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
