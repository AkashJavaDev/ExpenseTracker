package com.expensetracker.app.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.expensetracker.app.ui.formatMonth
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class ReportFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    PNG("image/png", "png"),
}

/**
 * Writes the report into the app's private cache, from where it is printed, saved or shared.
 * Only the latest report is kept there.
 */
fun writeReportFile(context: Context, reports: List<MonthReport>, allMonths: Boolean, format: ReportFormat): File {
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }

    val name = if (allMonths) "Expenses-All-months" else "Expenses-" + formatMonth(reports.single().month).replace(' ', '-')
    val file = File(dir, "$name.${format.extension}")
    file.outputStream().use { out ->
        val renderer = ReportRenderer()
        when {
            format == ReportFormat.PNG -> renderer.monthPng(reports.single(), out)
            allMonths -> renderer.allMonthsPdf(reports, out)
            else -> renderer.monthPdf(reports.single(), out)
        }
    }
    return file
}

/** Opens Android's print screen (a printer, or "Save as PDF"). */
fun printReport(context: Context, pdf: File) {
    context.getSystemService(PrintManager::class.java).print(pdf.nameWithoutExtension, PdfFileAdapter(pdf), null)
}

/** Copies the report to the place the user picked in the "Save" file picker. */
fun saveReport(context: Context, file: File, target: Uri) {
    val out = context.contentResolver.openOutputStream(target) ?: throw IOException("Couldn't open the chosen file")
    out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
}

/** Opens the share menu; the receiving app gets read access to this one file only. */
fun shareReport(context: Context, file: File, format: ReportFormat) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.reports", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = format.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share ${file.name}"))
}

/** Hands an already-made PDF to the print framework as-is. */
private class PdfFileAdapter(private val pdf: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(pdf.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        try {
            pdf.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: IOException) {
            callback.onWriteFailed(e.message)
        }
    }
}
