package com.sawn.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sawn.app.R
import com.sawn.domain.security.PinResult
import kotlinx.coroutines.launch

private const val PIN_LEN = 4

@Composable
fun SetPinDialog(onSubmit: suspend (String) -> Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit(pin: String) {
        if (busy) return
        busy = true
        scope.launch {
            val saved = runCatching { onSubmit(pin) }.getOrDefault(false)
            busy = false
            if (saved) {
                onDismiss()
            } else {
                error = context.getString(R.string.pin_save_failed)
            }
        }
    }

    PinDialog(
        title = if (first == null) {
            stringResource(R.string.pin_set_title)
        } else {
            stringResource(R.string.pin_confirm_title)
        },
        subtitle = error,
        entry = entry,
        busy = busy,
        onDigit = { digit ->
            if (!busy && entry.length < PIN_LEN) {
                entry += digit
                if (entry.length == PIN_LEN) {
                    val candidate = entry
                    if (first == null) {
                        first = candidate
                        entry = ""
                        error = null
                    } else if (first == candidate) {
                        submit(candidate)
                    } else {
                        first = null
                        entry = ""
                        error = context.getString(R.string.pin_mismatch)
                    }
                }
            }
        },
        onBackspace = {
            if (!busy && entry.isNotEmpty()) entry = entry.dropLast(1)
        },
        onDismiss = {
            if (!busy) onDismiss()
        },
    )
}

@Composable
fun VerifyPinDialog(
    title: String,
    verify: suspend (String) -> PinResult,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PinDialog(
        title = title,
        subtitle = error,
        entry = entry,
        busy = busy,
        onDigit = { digit ->
            if (!busy && entry.length < PIN_LEN) {
                entry += digit
                if (entry.length == PIN_LEN) {
                    val attempt = entry
                    busy = true
                    scope.launch {
                        val result = runCatching { verify(attempt) }.getOrElse { PinResult.Error }
                        busy = false
                        when (result) {
                            PinResult.Ok -> onSuccess()
                            PinResult.Wrong -> {
                                entry = ""
                                error = context.getString(R.string.pin_wrong)
                            }
                            PinResult.Error -> {
                                entry = ""
                                error = context.getString(R.string.pin_verify_failed)
                            }
                            is PinResult.Locked -> {
                                entry = ""
                                error = context.getString(R.string.pin_locked, result.seconds)
                            }
                        }
                    }
                }
            }
        },
        onBackspace = {
            if (!busy && entry.isNotEmpty()) entry = entry.dropLast(1)
        },
        onDismiss = {
            if (!busy) onDismiss()
        },
    )
}

@Composable
private fun PinDialog(
    title: String,
    subtitle: String?,
    entry: String,
    busy: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit,
) {
    SecureScreen()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(title, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(PIN_LEN) { index ->
                        val filled = index < entry.length
                        val slotLabel = stringResource(R.string.pin_digit, index + 1)
                        Surface(
                            shape = CircleShape,
                            color = if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(if (filled) 14.dp else 11.dp)
                                .semantics { contentDescription = slotLabel },
                        ) {}
                    }
                }

                Spacer(Modifier.height(18.dp))
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(18.dp))
                }
                PinPad(onDigit = onDigit, onBackspace = onBackspace, enabled = !busy)
            }
        },
    )
}

@Composable
private fun PinPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "back"),
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        when (key) {
                            "" -> Spacer(Modifier.width(64.dp))
                            "back" -> FilledIconButton(
                                onClick = onBackspace,
                                enabled = enabled,
                                modifier = Modifier.size(58.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = stringResource(R.string.pin_backspace),
                                )
                            }
                            else -> FilledIconButton(
                                onClick = { onDigit(key) },
                                enabled = enabled,
                                modifier = Modifier.size(58.dp),
                            ) {
                                Text(key, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
