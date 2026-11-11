package com.sawn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sawn.app.R
import kotlinx.coroutines.delay

/**
 * البطاقة النفسية الداعمة: تظهر قبل تأكيد القفل (ومحاولات الإيقاف حيث يعني ذلك).
 * أسلوب داعم لا لائم — أرقام حقيقية فقط (سلسلة الأيام + جلسات التركيز)، ثم تأمّل
 * 10 ثوانٍ قبل تفعيل زر التأكيد.
 */
@Composable
fun SupportiveConfirmDialog(
    streak: Int = 0,
    sessions: Int = 0,
    endsAtMs: Long = 0L, // القفل: الأزرار تحسبها قبل الاستدعاء
    onDismiss: () -> Unit = {},
    onConfirmed: (Long) -> Unit,
) {
    // سبب القفل: الأزرار تمرر endsAtMs أما الإيقاف فلا يلزم قيمة.
    var reflectSeconds by remember { mutableLongStateOf(10L) }
    LaunchedEffect(Unit) {
        while (reflectSeconds > 0) {
            delay(1000)
            reflectSeconds--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.supportive_title), fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.supportive_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (streak > 0 || sessions > 0) {
                    val streakText =
                        if (streak > 0) stringResource(R.string.supportive_streak, streak) else null
                    val sessionsText =
                        if (sessions > 0) stringResource(R.string.supportive_sessions, sessions) else null
                    Text(
                        stringResource(
                            R.string.supportive_reality,
                            listOfNotNull(streakText, sessionsText)
                                .joinToString(stringResource(R.string.supportive_connective)),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.supportive_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (reflectSeconds > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.supportive_reflect, reflectSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reflectSeconds <= 0,
                onClick = { onConfirmed(endsAtMs) },
            ) { Text(stringResource(R.string.supportive_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.supportive_stay_protected)) }
        },
    )
}
