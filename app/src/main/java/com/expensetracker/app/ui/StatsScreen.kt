package com.expensetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.app.ui.theme.categoryColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(vm: ExpenseViewModel, onBack: () -> Unit) {
    val month by vm.month.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val side by vm.side.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val ranked = summary.sections.filter { it.total > 0 }.sortedByDescending { it.total }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (side == Side.RECEIVED) "Received summary" else "Month summary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { PrintButton(vm) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        ) {
            item {
                MonthHeader(
                    month,
                    summary.total,
                    vm::previousMonth,
                    vm::nextMonth,
                    breakdownText(summary),
                    totalLabel = totalLabel(side),
                    balance = balance,
                )
            }
            if (ranked.isEmpty()) {
                item {
                    Text(
                        if (side == Side.RECEIVED) "Nothing received in this month yet." else "Nothing spent in this month yet.",
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        DonutChart(
                            ranked.map { it.total to categoryColor(it.category.colorIndex) },
                            Modifier.size(200.dp),
                        )
                    }
                }
                items(ranked, key = { it.category.id }) { section -> SectionStats(section, summary.total) }
            }
        }
    }
}

@Composable
private fun SectionStats(section: Section, monthTotal: Long) {
    val color = categoryColor(section.category.colorIndex)
    Column(Modifier.padding(vertical = 8.dp)) {
        StatLine(section.category.name, section.total, monthTotal, color, MaterialTheme.typography.titleSmall)
        if (section.category.isGroup) {
            section.lines.filter { it.total > 0 }.sortedByDescending { it.total }.forEach { line ->
                StatLine(
                    line.category.name,
                    line.total,
                    monthTotal,
                    color.copy(alpha = 0.55f),
                    MaterialTheme.typography.bodyMedium,
                    Modifier.padding(start = 20.dp, top = 8.dp),
                )
            }
        }
    }
}

/** Name, share of the month, amount, and a bar showing that share. */
@Composable
private fun StatLine(
    name: String,
    amount: Long,
    monthTotal: Long,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val fraction = amount.toFloat() / monthTotal
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color, 10.dp)
            Spacer(Modifier.width(8.dp))
            Text(name, style = style, modifier = Modifier.weight(1f))
            Text("${(fraction * 100).roundToInt()}%", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(formatRupees(amount), style = style, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
        }
    }
}

@Composable
private fun DonutChart(slices: List<Pair<Long, Color>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.first }.toFloat()
    Canvas(modifier) {
        val stroke = size.minDimension * 0.18f
        val diameter = size.minDimension - stroke
        val gap = if (slices.size > 1) 1.5f else 0f
        var start = -90f
        slices.forEach { (value, color) ->
            val sweep = value / total * 360f
            drawArc(
                color = color,
                startAngle = start + gap / 2,
                sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(diameter, diameter),
                style = Stroke(stroke),
            )
            start += sweep
        }
    }
}
