package com.expensetracker.app.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import com.expensetracker.app.ui.CategoryLine
import com.expensetracker.app.ui.MonthSummary
import com.expensetracker.app.ui.Section
import com.expensetracker.app.ui.breakdownText
import com.expensetracker.app.ui.formatAmount
import com.expensetracker.app.ui.formatMonth
import com.expensetracker.app.ui.formatRupees
import com.expensetracker.app.ui.theme.categoryColor
import java.io.OutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * One month: what was spent and, in versions with the Received tab, what came in
 * ([received] is null otherwise).
 */
class MonthReport(val month: YearMonth, val summary: MonthSummary, val received: MonthSummary? = null)

/**
 * Draws reports laid out like the notes they replace: each category with its amounts and
 * total. Categories with nothing spent are left out. Always black on white, whatever the theme.
 */
class ReportRenderer {
    private val pageWidth = 595 // A4, in PDF points
    private val pageHeight = 842
    private val margin = 40f
    private val footerSpace = 18f
    private val dotSpace = 14f
    private val contentWidth = pageWidth - 2 * margin

    // Right edges of the number columns on the all-months summary page.
    private val spentColumns = listOf(0.45f, 0.75f, 1f) // Total, Fixed, Daily
    private val balanceColumns = listOf(0.5f, 0.75f, 1f) // Spent, Received, Left

    private val titlePaint = textPaint(20f, Color.BLACK, bold = true)
    private val headingPaint = textPaint(15f, Color.BLACK, bold = true)
    private val bodyPaint = textPaint(12f, Color.BLACK)
    private val bodyBoldPaint = textPaint(12f, Color.BLACK, bold = true)
    private val goodPaint = textPaint(12f, 0xFF2E7D32.toInt(), bold = true)
    private val badPaint = textPaint(12f, 0xFFC62828.toInt(), bold = true)
    private val amountsPaint = textPaint(10.5f, 0xFF555555.toInt())
    private val smallPaint = textPaint(10f, 0xFF777777.toInt())
    private val linePaint = Paint().apply {
        color = 0xFFDDDDDD.toInt()
        strokeWidth = 1f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val printedOn = "Printed on " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy"))

    fun monthPdf(report: MonthReport, out: OutputStream) = writePdf(monthBlocks(report), out)

    /** A summary page with one row per month, then each month starting on its own page. */
    fun allMonthsPdf(reports: List<MonthReport>, out: OutputStream) =
        writePdf(summaryBlocks(reports) + reports.flatMap { listOf(PageBreak) + monthBlocks(it) }, out)

    /** The whole month as one tall picture, drawn at 2x for sharp text. */
    fun monthPng(report: MonthReport, out: OutputStream) {
        val blocks = monthBlocks(report)
        val contentEnd = margin + blocks.sumOf { it.height.toDouble() }.toFloat()
        val height = contentEnd + footerSpace + margin / 2
        val scale = 2f
        val bitmap = Bitmap.createBitmap((pageWidth * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale, scale)
            var y = margin
            blocks.forEach { y += draw(canvas, it, y) }
            canvas.drawText(printedOn, margin, height - margin / 2, smallPaint)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } finally {
            bitmap.recycle()
        }
    }

    private sealed interface Block {
        val height: Float
    }

    private class TextBlock(val layout: StaticLayout, val indent: Float, val spaceBefore: Float) : Block {
        override val height = spaceBefore + layout.height
    }

    /** Name on the left (optionally after a colour dot), amount on the right. */
    private class RowBlock(
        val left: String,
        val right: String,
        val paint: TextPaint,
        val dotColor: Int? = null,
        val indent: Float = 0f,
        val spaceBefore: Float = 0f,
        val rightPaint: TextPaint = paint,
    ) : Block {
        override val height = spaceBefore + paint.fontSpacing
    }

    /** First cell on the left, the rest right-aligned to [ends] (fractions of the content width). */
    private class TableBlock(
        val cells: List<String>,
        val ends: List<Float>,
        val paint: TextPaint,
        val spaceBefore: Float,
    ) : Block {
        override val height = spaceBefore + paint.fontSpacing
    }

    private class DividerBlock(val spaceBefore: Float) : Block {
        override val height = spaceBefore + 9f
    }

    private data object PageBreak : Block {
        override val height = 0f
    }

    private fun monthBlocks(report: MonthReport): List<Block> = buildList {
        val summary = report.summary
        val received = report.received
        add(text("Expense Tracker — ${formatMonth(report.month)}", titlePaint))
        add(RowBlock("Total spent", formatRupees(summary.total), headingPaint, spaceBefore = 10f))
        breakdownText(summary)?.let { add(text(it, smallPaint, spaceBefore = 2f)) }
        if (received != null) {
            add(RowBlock("Total received", formatRupees(received.total), headingPaint, spaceBefore = 6f))
            add(balanceRow(received.total - summary.total))
        }
        add(DividerBlock(8f))

        val sections = summary.sections.filter { it.total > 0 }
        if (sections.isEmpty()) {
            add(text("Nothing spent in this month.", bodyPaint, spaceBefore = 8f))
        } else {
            addAll(sectionBlocks(sections))
            add(DividerBlock(8f))
            val shares = sections.sortedByDescending { it.total }
                .joinToString("  ·  ") { "${it.category.name} ${percent(it.total, summary.total)}%" }
            add(text("Biggest spending: $shares", smallPaint, spaceBefore = 2f))
        }

        if (received != null) {
            add(RowBlock("RECEIVED", formatRupees(received.total), headingPaint, spaceBefore = 18f))
            add(DividerBlock(2f))
            val receivedSections = received.sections.filter { it.total > 0 }
            if (receivedSections.isEmpty()) {
                add(text("Nothing received in this month.", bodyPaint, spaceBefore = 4f))
            } else {
                addAll(sectionBlocks(receivedSections))
            }
        }
    }

    /** Each category in its colour, with its amounts; a group lists its parts. */
    private fun sectionBlocks(sections: List<Section>): List<Block> = buildList {
        sections.forEach { section ->
            val color = categoryColor(section.category.colorIndex).toArgb()
            add(RowBlock(section.category.name.uppercase(), formatRupees(section.total), bodyBoldPaint, color, spaceBefore = 10f))
            if (section.category.isGroup) {
                section.lines.filter { it.total > 0 }.forEach { line ->
                    add(RowBlock(line.category.name, formatRupees(line.total), bodyPaint, indent = dotSpace, spaceBefore = 4f))
                    add(amounts(line))
                }
            } else {
                add(amounts(section.lines.single()))
            }
        }
    }

    /** "Left this month ₹15,700" in green, or "Over by ₹2,000" in red. */
    private fun balanceRow(left: Long): Block =
        if (left >= 0) {
            RowBlock("Left this month", formatRupees(left), bodyBoldPaint, spaceBefore = 2f, rightPaint = goodPaint)
        } else {
            RowBlock("Over by", formatRupees(-left), bodyBoldPaint, spaceBefore = 2f, rightPaint = badPaint)
        }

    private fun summaryBlocks(reports: List<MonthReport>): List<Block> = buildList {
        add(text("Expense Tracker — All months", titlePaint))
        if (reports.isEmpty()) {
            add(text("No expenses saved yet.", bodyPaint, spaceBefore = 8f))
            return@buildList
        }
        val range = "${formatMonth(reports.first().month)} to ${formatMonth(reports.last().month)}"
        val count = if (reports.size == 1) "1 month" else "${reports.size} months"
        add(text("$range  ·  $count", smallPaint, spaceBefore = 4f))
        add(DividerBlock(8f))

        val spent = reports.sumOf { it.summary.total }
        if (reports.any { it.received != null }) {
            // Spent / Received / Left columns.
            add(TableBlock(listOf("Month", "Spent", "Received", "Left"), balanceColumns, bodyBoldPaint, 4f))
            reports.forEach { report ->
                val got = report.received?.total ?: 0
                add(TableBlock(
                    listOf(formatMonth(report.month), formatRupees(report.summary.total), formatRupees(got), formatRupees(got - report.summary.total)),
                    balanceColumns,
                    bodyPaint,
                    6f,
                ))
            }
            add(DividerBlock(6f))
            val got = reports.sumOf { it.received?.total ?: 0 }
            add(TableBlock(
                listOf("All months", formatRupees(spent), formatRupees(got), formatRupees(got - spent)),
                balanceColumns,
                bodyBoldPaint,
                2f,
            ))
        } else {
            // Total / Fixed / Daily columns.
            val fixedLabel = reports.firstNotNullOfOrNull { it.summary.fixedName } ?: "Fixed"
            add(TableBlock(listOf("Month", "Total", fixedLabel, "Daily"), spentColumns, bodyBoldPaint, 4f))
            reports.forEach { report ->
                val s = report.summary
                add(TableBlock(
                    listOf(formatMonth(report.month), formatRupees(s.total), formatRupees(s.fixedTotal), formatRupees(s.total - s.fixedTotal)),
                    spentColumns,
                    bodyPaint,
                    6f,
                ))
            }
            add(DividerBlock(6f))
            val fixed = reports.sumOf { it.summary.fixedTotal }
            add(TableBlock(
                listOf("All months", formatRupees(spent), formatRupees(fixed), formatRupees(spent - fixed)),
                spentColumns,
                bodyBoldPaint,
                2f,
            ))
        }
    }

    private fun amounts(line: CategoryLine): Block =
        text(line.expenses.joinToString(", ") { formatAmount(it.amountPaise) }, amountsPaint, dotSpace, 1f)

    private fun text(value: String, paint: TextPaint, indent: Float = 0f, spaceBefore: Float = 0f): Block {
        val width = (contentWidth - indent).toInt()
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        return TextBlock(layout, indent, spaceBefore)
    }

    private fun writePdf(blocks: List<Block>, out: OutputStream) {
        val pages = paginate(blocks)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val pdfPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
                var y = margin
                page.forEach { y += draw(pdfPage.canvas, it, y) }
                pdfPage.canvas.drawText(
                    "$printedOn  ·  Page ${index + 1} of ${pages.size}",
                    margin,
                    pageHeight - margin / 2,
                    smallPaint,
                )
                document.finishPage(pdfPage)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun paginate(blocks: List<Block>): List<List<Block>> {
        val bottom = pageHeight - margin / 2 - footerSpace
        val pages = mutableListOf(mutableListOf<Block>())
        var y = margin
        for (block in blocks) {
            val startNewPage = block is PageBreak || y + block.height > bottom
            if (startNewPage && pages.last().isNotEmpty()) {
                pages.add(mutableListOf())
                y = margin
            }
            if (block !is PageBreak) {
                pages.last().add(block)
                y += block.height
            }
        }
        return pages
    }

    /** Draws [block] with its top at [top] and returns its height. */
    private fun draw(canvas: Canvas, block: Block, top: Float): Float {
        when (block) {
            is TextBlock -> {
                canvas.save()
                canvas.translate(margin + block.indent, top + block.spaceBefore)
                block.layout.draw(canvas)
                canvas.restore()
            }
            is RowBlock -> {
                val baseline = top + block.spaceBefore - block.paint.ascent()
                var x = margin + block.indent
                block.dotColor?.let { color ->
                    dotPaint.color = color
                    val centerY = baseline + (block.paint.ascent() + block.paint.descent()) / 2
                    canvas.drawCircle(x + 4f, centerY, 4f, dotPaint)
                    x += dotSpace
                }
                canvas.drawText(block.left, x, baseline, block.paint)
                val rightX = pageWidth - margin - block.rightPaint.measureText(block.right)
                canvas.drawText(block.right, rightX, baseline, block.rightPaint)
            }
            is TableBlock -> {
                val baseline = top + block.spaceBefore - block.paint.ascent()
                canvas.drawText(block.cells[0], margin, baseline, block.paint)
                block.ends.forEachIndexed { i, end ->
                    val cell = block.cells[i + 1]
                    canvas.drawText(cell, margin + end * contentWidth - block.paint.measureText(cell), baseline, block.paint)
                }
            }
            is DividerBlock -> {
                val y = top + block.spaceBefore + 4f
                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            }
            PageBreak -> Unit
        }
        return block.height
    }

    private fun percent(part: Long, total: Long): Int = (part * 100.0 / total).roundToInt()

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}
