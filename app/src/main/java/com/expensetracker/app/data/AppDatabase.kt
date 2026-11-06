package com.expensetracker.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Category::class, Expense::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ExpenseDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "expenses.db")
                .addCallback(object : Callback() {
                    // Runs once, on the very first launch after install.
                    override fun onCreate(db: SupportSQLiteDatabase) = Starter.createCategories(db)

                    // Every launch; adds any starter month this phone hasn't received yet.
                    override fun onOpen(db: SupportSQLiteDatabase) = Starter.importMonths(db)
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
