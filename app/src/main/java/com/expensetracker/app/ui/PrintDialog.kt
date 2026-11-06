package com.expensetracker.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.app.report.ReportFormat
import com.expensetracker.app.report.printReport
import com.expensetracker.app.report.saveReport
import com.expensetracker.app.report.shareReport
import com.expensetracker.app.report.writeReportFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Top-bar button that opens the print / export dialog. */
@Composable
fun PrintButton(vm: ExpenseViewModel) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.Print, contentDescription = "Print or export")
    }
    if (open) PrintDialog(vm, onDismiss = { open = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrintDialog(vm: ExpenseViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val month by vm.month.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var allMonths by remember { mutableStateOf(false) }
    var chosenFormat by remember { mutableStateOf(ReportFormat.PDF) }
    var busy by remember { mutableStateOf(false) }
    var fileToSave by remember { mutableStateOf<File?>(null) }
    // A picture of every month would be impractically long, so all months is PDF only.
    val format = if (allMonths) ReportFormat.PDF else chosenFormat

    // The dialog stays open until the file picker returns, so the result isn't lost.
    val onSaveTarget: (Uri?) -> Unit = { target ->
        val file = fileToSave
        if (target != null && file != null) {
            try {
                saveReport(context, file, target)
                Toast.makeText(context, "Saved ${file.name}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't save: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        fileToSave = null
        onDismiss()
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ReportFormat.PDF.mimeType), onSaveTarget)
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ReportFormat.PNG.mimeType), onSaveTarget)

    fun export(format: ReportFormat, then: (File) -> Unit) {
        busy = true
        scope.launch {
            try {
                val reports = vm.reports(allMonths)
                val file = withContext(Dispatchers.Default) { writeReportFile(context, reports, allMonths, format) }
                then(file)
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't make the report: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Print / export") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !allMonths, onClick = { allMonths = false }, label = { Text(formatMonth(month)) })
                    FilterChip(selected = allMonths, onClick = { allMonths = true }, label = { Text("All months") })
                }
                Text("Format", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = format == ReportFormat.PDF,
                        onClick = { chosenFormat = ReportFormat.PDF },
                        label = { Text("PDF") },
                    )
                    FilterChip(
                        selected = format == ReportFormat.PNG,
                        onClick = { chosenFormat = ReportFormat.PNG },
                        enabled = !allMonths,
                        label = { Text("Picture") },
                    )
                }
                Text(
                    if (allMonths) "All months is PDF only. Print always uses PDF." else "Print always uses PDF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            FlowRow {
                TextButton(enabled = !busy, onClick = {
                    export(ReportFormat.PDF) { file ->
                        printReport(context, file)
                        onDismiss()
                    }
                }) { Text("Print") }
                TextButton(enabled = !busy, onClick = {
                    export(format) { file ->
                        fileToSave = file
                        (if (format == ReportFormat.PDF) savePdf else savePng).launch(file.name)
                    }
                }) { Text("Save") }
                TextButton(enabled = !busy, onClick = {
                    export(format) { file ->
                        shareReport(context, file, format)
                        onDismiss()
                    }
                }) { Text("Share") }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
        },
    )
}
