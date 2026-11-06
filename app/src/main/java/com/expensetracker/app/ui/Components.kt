package com.expensetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.app.data.Expense
import java.time.YearMonth

@Composable
fun ColorDot(color: Color, size: Dp = 12.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

fun totalLabel(side: Side): String = if (side == Side.RECEIVED) "total received" else "total spent"

/** "Fixed & one-time ₹12,500 · Daily ₹8,000", or null when there is no fixed group. */
fun breakdownText(summary: MonthSummary): String? = summary.fixedName?.let {
    "$it ${formatRupees(summary.fixedTotal)}  ·  Daily ${formatRupees(summary.total - summary.fixedTotal)}"
}

/** "◀  October 2026 / ₹20,500 / total spent  ▶", plus an optional breakdown line. */
@Composable
fun MonthHeader(
    month: YearMonth,
    total: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    breakdown: String? = null,
    totalLabel: String = "total spent",
    balance: Balance? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatMonth(month),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatRupees(total),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                totalLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (breakdown != null) {
                Text(
                    breakdown,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (balance != null) BalanceLine(balance)
        }
        IconButton(onClick = onNext, enabled = month < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

/** "Received ₹25,000 · Spent ₹9,300" over "Left this month ₹15,700" (green), or "Over by …" (red). */
@Composable
private fun BalanceLine(balance: Balance) {
    val inProfit = balance.left >= 0
    val color = when {
        !inProfit -> MaterialTheme.colorScheme.error
        isSystemInDarkTheme() -> Color(0xFF81C784)
        else -> Color(0xFF2E7D32)
    }
    Column(Modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Received ${formatRupees(balance.received)}  ·  Spent ${formatRupees(balance.spent)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            if (inProfit) "Left this month ${formatRupees(balance.left)}" else "Over by ${formatRupees(-balance.left)}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** The amounts of one category as tappable chips: 160 40 20 160 … */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AmountChips(expenses: List<Expense>, onEdit: (Expense) -> Unit) {
    if (expenses.isEmpty()) {
        Text(
            "Nothing yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        expenses.forEach { expense ->
            Surface(
                onClick = { onEdit(expense) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    formatAmount(expense.amountPaise),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
