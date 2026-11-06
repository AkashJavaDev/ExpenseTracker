package com.expensetracker.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v3 adds the Received side; everything that exists stays on the Spent side. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN isReceived INTEGER NOT NULL DEFAULT 0")
        Starter.createReceivedCategories(db)
    }
}

/**
 * v2 adds optional/fixed categories and moves the existing big payments
 * (matched by name) into a new "Fixed & one-time" group at the top.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    // Lowercase names in the order they should appear inside the group.
    private val fixedNames = listOf("emi", "rent", "claude", "recharge", "dress", "earbuds")
    private val optionalNames = setOf("claude", "recharge", "dress", "earbuds")

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN isOptional INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE categories ADD COLUMN isFixed INTEGER NOT NULL DEFAULT 0")

        val found = mutableMapOf<String, Long>()
        db.query("SELECT id, name FROM categories WHERE parentId IS NULL AND isGroup = 0").use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(1).trim().lowercase()
                if (key in fixedNames && key !in found) found[key] = cursor.getLong(0)
            }
        }

        // Make room at the top for the new group.
        db.execSQL("UPDATE categories SET sortOrder = sortOrder + 1 WHERE parentId IS NULL")
        val groupId = db.insert("categories", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
            put("name", Starter.FIXED_NAME)
            putNull("parentId")
            put("isGroup", 1)
            put("isOptional", 0)
            put("isFixed", 1)
            put("colorIndex", Starter.FIXED_COLOR)
            put("sortOrder", 0)
        })

        fixedNames.filter { it in found }.forEachIndexed { index, key ->
            db.update(
                "categories",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("parentId", groupId)
                    put("colorIndex", Starter.FIXED_COLOR)
                    put("sortOrder", index)
                    put("isOptional", if (key in optionalNames) 1 else 0)
                },
                "id = ?",
                arrayOf(found.getValue(key)),
            )
        }
    }
}
