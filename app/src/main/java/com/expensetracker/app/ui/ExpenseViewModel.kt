package com.expensetracker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.BuildConfig
import com.expensetracker.app.data.AppDatabase
import com.expensetracker.app.data.Category
import com.expensetracker.app.data.Expense
import com.expensetracker.app.report.MonthReport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Expenses of one category within the selected month. */
data class CategoryLine(val category: Category, val expenses: List<Expense>) {
    val total: Long = expenses.sumOf { it.amountPaise }
}

/** A top-level category: a single line, or one line per sub-category for a group. */
data class Section(val category: Category, val lines: List<CategoryLine>) {
    val total: Long = lines.sumOf { it.total }
}

data class MonthSummary(val sections: List<Section>) {
    val total: Long = sections.sumOf { it.total }

    /** Name of the "Fixed & one-time" group, or null if it was deleted. */
    val fixedName: String? = sections.firstOrNull { it.category.isFixed }?.category?.name
    val fixedTotal: Long = sections.filter { it.category.isFixed }.sumOf { it.total }
}

/** Which tab is open: money spent, or money received (share version only). */
enum class Side { SPENT, RECEIVED }

/** Both sides of one month. */
data class Balance(val received: Long, val spent: Long) {
    val left: Long = received - spent
}

/** Shared by all screens so the selected month and tab stay the same across them. */
class ExpenseViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()

    /** The Received tab is switched on only in the "share" version. */
    val incomeEnabled: Boolean = BuildConfig.INCOME_ENABLED

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    private val _side = MutableStateFlow(Side.SPENT)
    val side: StateFlow<Side> = _side.asStateFlow()

    /** Every category, both sides. */
    val categories: StateFlow<List<Category>> = dao.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categories of the open tab. */
    val sideCategories: StateFlow<List<Category>> = combine(categories, _side) { all, side ->
        all.filter { it.isReceived == (side == Side.RECEIVED) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categories of the open tab that can hold amounts, in display order. */
    val leafCategories: StateFlow<List<Category>> = sideCategories
        .map { all ->
            all.filter { it.parentId == null }.flatMap { top ->
                if (top.isGroup) all.filter { it.parentId == top.id } else listOf(top)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthExpenses = _month
        .flatMapLatest { m -> dao.expensesBetween(m.startMillis(), m.plusMonths(1).startMillis()) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** The open tab's cards for the selected month. */
    val summary: StateFlow<MonthSummary> = combine(monthExpenses, sideCategories) { expenses, categories ->
        buildSummary(categories, expenses)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSummary(emptyList()))

    /** Received vs spent this month; null when the Received tab is off. */
    val balance: StateFlow<Balance?> = combine(monthExpenses, categories) { expenses, all ->
        if (!incomeEnabled) return@combine null
        val receivedIds = all.filter { it.isReceived }.mapTo(HashSet()) { it.id }
        val received = expenses.filter { it.categoryId in receivedIds }.sumOf { it.amountPaise }
        Balance(received = received, spent = expenses.sumOf { it.amountPaise } - received)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSide(side: Side) {
        _side.value = side
    }

    fun previousMonth() = _month.update { it.minusMonths(1) }

    fun nextMonth() = _month.update { if (it < YearMonth.now()) it.plusMonths(1) else it }

    fun addExpense(categoryId: Long, amountPaise: Long) {
        val month = _month.value
        // In the current month use "now"; when filling in a past month, file it under that month.
        val date = if (month == YearMonth.now()) {
            System.currentTimeMillis()
        } else {
            month.atDay(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        viewModelScope.launch {
            dao.insertExpense(Expense(categoryId = categoryId, amountPaise = amountPaise, date = date))
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { dao.updateExpense(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { dao.deleteExpense(expense) }
    }

    fun restoreExpense(expense: Expense) {
        viewModelScope.launch { dao.insertExpense(expense) }
    }

    /** Adds a category to the open tab (or to [parent]'s side when it goes inside a group). */
    fun addCategory(name: String, parent: Category?, isGroup: Boolean, isOptional: Boolean) {
        val all = categories.value
        val group = parent == null && isGroup
        val category = Category(
            name = name,
            parentId = parent?.id,
            isGroup = group,
            isOptional = !group && isOptional,
            isReceived = parent?.isReceived ?: (_side.value == Side.RECEIVED),
            colorIndex = parent?.colorIndex ?: nextTopLevelColor(all),
            sortOrder = nextSortOrder(all, parent?.id),
        )
        viewModelScope.launch { dao.insertCategory(category) }
    }

    fun renameCategory(category: Category, name: String) {
        viewModelScope.launch { dao.updateCategory(category.copy(name = name)) }
    }

    fun setOptional(category: Category, isOptional: Boolean) {
        viewModelScope.launch { dao.updateCategory(category.copy(isOptional = isOptional)) }
    }

    /** Moves a category into [target] group (at its end), or out to its own card when null. */
    fun moveCategory(category: Category, target: Category?) {
        val all = categories.value
        val moved = category.copy(
            parentId = target?.id,
            colorIndex = target?.colorIndex ?: nextTopLevelColor(all),
            sortOrder = nextSortOrder(all, target?.id),
        )
        viewModelScope.launch { dao.updateCategory(moved) }
    }

    /** Saves the order of one level (top-level cards, or the parts of one group). */
    fun reorder(ordered: List<Category>) {
        val ids = ordered.map { it.id }
        viewModelScope.launch { dao.reorder(ids) }
    }

    /** The selected month, or every month that has entries (oldest first), ready to print. */
    suspend fun reports(allMonths: Boolean): List<MonthReport> {
        val categories = dao.allCategories()
        val spentCategories = categories.filter { !it.isReceived }
        val receivedCategories = categories.filter { it.isReceived }
        val byMonth = dao.allExpenses().groupBy {
            YearMonth.from(Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()))
        }
        val months = if (allMonths) byMonth.keys.sorted() else listOf(_month.value)
        return months.map { month ->
            val expenses = byMonth[month].orEmpty()
            MonthReport(
                month,
                buildSummary(spentCategories, expenses),
                if (incomeEnabled) buildSummary(receivedCategories, expenses) else null,
            )
        }
    }

    /** Number of expenses (all months) that deleting [category] would remove. */
    suspend fun expenseCount(category: Category): Int = dao.countExpenses(familyIds(category))

    fun deleteCategory(category: Category) {
        val ids = familyIds(category)
        viewModelScope.launch { dao.deleteCategories(ids) }
    }

    private fun familyIds(category: Category): List<Long> =
        listOf(category.id) + categories.value.filter { it.parentId == category.id }.map { it.id }

    private fun nextTopLevelColor(all: List<Category>): Int =
        (all.filter { it.parentId == null }.maxOfOrNull { it.colorIndex } ?: -1) + 1

    private fun nextSortOrder(all: List<Category>, parentId: Long?): Int =
        (all.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: -1) + 1

    /** Optional categories with nothing spent this month are left out. */
    private fun buildSummary(categories: List<Category>, expenses: List<Expense>): MonthSummary {
        val byCategory = expenses.groupBy { it.categoryId }
        fun lineFor(category: Category): CategoryLine? =
            CategoryLine(category, byCategory[category.id].orEmpty())
                .takeIf { !category.isOptional || it.expenses.isNotEmpty() }

        val sections = categories.filter { it.parentId == null }.mapNotNull { top ->
            if (top.isGroup) {
                Section(top, categories.filter { it.parentId == top.id }.mapNotNull(::lineFor))
            } else {
                lineFor(top)?.let { Section(top, listOf(it)) }
            }
        }
        return MonthSummary(sections)
    }
}

private fun YearMonth.startMillis(): Long =
    atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
