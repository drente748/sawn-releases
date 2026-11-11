package com.sawn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sawn.app.R
import com.sawn.core.data.Schedule

/** ترتيب الأيام يطابق قناع [Schedule.daysMask]: البت 0 = الاثنين … البت 6 = الأحد. */
private val DAY_LETTERS = listOf(
    R.string.day_letter_mon, R.string.day_letter_tue, R.string.day_letter_wed,
    R.string.day_letter_thu, R.string.day_letter_fri, R.string.day_letter_sat, R.string.day_letter_sun,
)

private val DAY_NAMES = listOf(
    R.string.day_mon, R.string.day_tue, R.string.day_wed, R.string.day_thu,
    R.string.day_fri, R.string.day_sat, R.string.day_sun,
)

/** Create/edit dialog for a [Schedule]: name, days, and start/end times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorDialog(initial: Schedule?, onSave: (Schedule) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var daysMask by remember { mutableIntStateOf(initial?.daysMask ?: 0b0011111) }
    var start by remember { mutableIntStateOf(initial?.startMinute ?: 9 * 60) }
    var end by remember { mutableIntStateOf(initial?.endMinute ?: 17 * 60) }
    var picking by remember { mutableStateOf<String?>(null) } // "start" | "end" | null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && daysMask != 0,
                onClick = {
                    onSave(
                        (initial ?: Schedule(name = "", daysMask = 0, startMinute = 0, endMinute = 0)).copy(
                            name = name.trim(), daysMask = daysMask, startMinute = start, endMinute = end,
                        ),
                    )
                    onDismiss()
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        title = {
            Text(stringResource(if (initial == null) R.string.new_schedule else R.string.schedule_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.schedule_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.schedule_days), style = MaterialTheme.typography.labelLarge)
                DayToggleRow(mask = daysMask, onToggle = { bit -> daysMask = daysMask xor (1 shl bit) })
                Text(
                    stringResource(R.string.schedule_days_preview, daysLabelOf(daysMask)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { picking = "start" }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.schedule_start, fmtTime(start)))
                    }
                    OutlinedButton(onClick = { picking = "end" }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.schedule_end, fmtTime(end)))
                    }
                }
                if (end <= start) {
                    Text(
                        stringResource(R.string.schedule_overnight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
    )

    picking?.let { which ->
        val minutes = if (which == "start") start else end
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    val v = state.hour * 60 + state.minute
                    if (which == "start") start = v else end = v
                    picking = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun daysLabelOf(mask: Int): String {
    if (mask == 0) return stringResource(R.string.schedule_days_none)
    val names = DAY_NAMES.mapIndexed { i, res ->
        stringResource(res).takeIf { (mask shr i) and 1 == 1 }
    }.filterNotNull()
    return if (names.size == 7) stringResource(R.string.every_day) else names.joinToString("، ")
}

/** سبعة مربعات متساوية بعرض السطر — تتسع لأي شاشة وتعمل بالاتجاهين، بخلاف رقائق الفلتار. */
@Composable
private fun DayToggleRow(mask: Int, onToggle: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        DAY_LETTERS.forEachIndexed { i, letterRes ->
            val on = (mask shr i) and 1 == 1
            Surface(
                onClick = { onToggle(i) },
                shape = MaterialTheme.shapes.small,
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (on) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(letterRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun fmtTime(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
