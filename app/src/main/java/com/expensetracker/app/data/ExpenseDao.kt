package com.expensetracker.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExpenseDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    abstract fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM expenses WHERE date >= :start AND date < :end ORDER BY date, id")
    abstract fun expensesBetween(start: Long, end: Long): Flow<List<Expense>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    abstract suspend fun allCategories(): List<Category>

    @Query("SELECT * FROM expenses ORDER BY date, id")
    abstract suspend fun allExpenses(): List<Expense>

    /** REPLACE lets "Undo" put a deleted expense back with its original id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertExpense(expense: Expense): Long

    @Update
    abstract suspend fun updateExpense(expense: Expense)

    @Delete
    abstract suspend fun deleteExpense(expense: Expense)

    @Insert
    abstract suspend fun insertCategory(category: Category): Long

    @Update
    abstract suspend fun updateCategory(category: Category)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    protected abstract suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Saves a new order: the first id gets sortOrder 0, the next 1, and so on. */
    @Transaction
    open suspend fun reorder(categoryIds: List<Long>) {
        categoryIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("SELECT COUNT(*) FROM expenses WHERE categoryId IN (:categoryIds)")
    abstract suspend fun countExpenses(categoryIds: List<Long>): Int

    @Query("DELETE FROM expenses WHERE categoryId IN (:categoryIds)")
    protected abstract suspend fun deleteExpensesIn(categoryIds: List<Long>)

    @Query("DELETE FROM categories WHERE id IN (:categoryIds)")
    protected abstract suspend fun deleteCategoriesIn(categoryIds: List<Long>)

    /** Deletes the categories together with every expense saved under them. */
    @Transaction
    open suspend fun deleteCategories(categoryIds: List<Long>) {
        deleteExpensesIn(categoryIds)
        deleteCategoriesIn(categoryIds)
    }
}
