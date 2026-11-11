package com.sawn.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.R
import com.sawn.app.ui.theme.SawnPalette
import com.sawn.app.system.AdminManager
import com.sawn.domain.feature.ChallengePolicy
import com.sawn.domain.feature.TriggerTag
import kotlinx.coroutines.launch

/*
 * شاشات Plan B — كلها تشارك الـ ViewModel نفسه (:main) فلا تنشئ نسخة خاصة بها،
 * وكلها بالتصميم نفسه (DawnCard + SawnPalette) فتقرأ كأنها جزء من التطبيق.
 */

// ===== التحدّي ===========================================================

@Composable
fun ChallengeScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val days by vm.challengeDays.collectAsStateWithLifecycle()
    val stage = ChallengePolicy.stageFor(days)
    val complete = ChallengePolicy.isComplete(days)
    val remaining = ChallengePolicy.daysRemaining(days)
    var confirmRestart by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    SettingsSectionScreen(stringResource(R.string.feature_challenge_title), onBack) {
        DawnCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SawnPalette.GoldContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = SawnPalette.GoldDeep)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.feature_challenge_progress, days, ChallengePolicy.TOTAL_DAYS),
                        style = MaterialTheme.typography.headlineSmall,
                        color = SawnPalette.Ink,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (complete) {
                            stringResource(R.string.feature_challenge_victory)
                        } else {
                            stringResource(R.string.feature_challenge_remaining, remaining)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SawnPalette.InkSoft,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ChallengeProgressBar(days, ChallengePolicy.TOTAL_DAYS)
            stage?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.feature_challenge_stage, it.milestone),
                    style = MaterialTheme.typography.labelMedium,
                    color = SawnPalette.PrimaryDeep,
                )
            }
        }

        DawnCard {
            Text(stringResource(R.string.feature_challenge_how), style = MaterialTheme.typography.titleMedium, color = SawnPalette.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.feature_challenge_how_body),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.InkSoft,
            )
        }

        Button(
            onClick = {
                // «لا يتقدّم إلا بيوم محمي» — إن لم تكن الحماية فعّالة الآن نشرح السبب.
                vm.recordChallengeDay(
                    onRejected = {
                        message = context.getString(R.string.feature_challenge_needs_protection)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (days == 0) R.string.feature_challenge_start else R.string.feature_challenge_record,
                ),
            )
        }
        if (days > 0) {
            TextButton(
                onClick = { confirmRestart = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_challenge_restart), color = SawnPalette.InkSoft)
            }
        }
        message?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.feature_challenge_restart)) },
            text = { Text(stringResource(R.string.feature_challenge_restart_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    vm.restartChallenge()
                }) {
                    Text(stringResource(R.string.feature_challenge_restart), color = SawnPalette.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ChallengeProgressBar(days: Int, total: Int) {
    val ratio = if (total <= 0) 0f else (days.toFloat() / total).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SawnPalette.Track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio)
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(SawnPalette.Gold),
        )
    }
}

// ===== الترميز العميق =====================================================

@Composable
fun DeepFocusScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val enabled by vm.deepFocusEnabled.collectAsStateWithLifecycle()
    val whitelist by vm.deepFocusWhitelist.collectAsStateWithLifecycle()
    val unlockUntil by vm.deepFocusUnlockUntil.collectAsStateWithLifecycle()
    var askPin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SettingsSectionScreen(stringResource(R.string.feature_deep_focus_title), onBack) {
        DawnCard {
            SwitchRow(
                title = stringResource(R.string.feature_deep_focus_enable),
                subtitle = stringResource(R.string.feature_deep_focus_sub),
                checked = enabled,
                onChange = { vm.setDeepFocusEnabled(it) },
            )
            if (enabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.feature_deep_focus_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SawnPalette.PrimaryDeep,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        DawnCard {
            Text(
                stringResource(R.string.feature_deep_focus_whitelist_label),
                style = MaterialTheme.typography.titleMedium,
                color = SawnPalette.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.feature_deep_focus_whitelist, whitelist.size),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
            if (whitelist.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                whitelist.sorted().forEach { pkg ->
                    Text(
                        pkg,
                        style = MaterialTheme.typography.labelSmall,
                        color = SawnPalette.InkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.feature_deep_focus_note),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
        }

        if (enabled) {
            val escapeOpen = unlockUntil > System.currentTimeMillis()
            if (escapeOpen) {
                Text(
                    stringResource(R.string.feature_deep_focus_escape_open),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SawnPalette.Success,
                )
            }
            OutlinedButton(
                onClick = { askPin = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_deep_focus_unlock))
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SawnPalette.Ink)
        }
    }

    if (askPin) {
        VerifyPinDialog(
            title = stringResource(R.string.feature_deep_focus_unlock),
            verify = { pin -> vm.requestDeepFocusUnlock(pin) },
            onSuccess = {
                askPin = false
                message = context.getString(R.string.feature_deep_focus_unlock_success)
            },
            onDismiss = { askPin = false },
        )
    }
}

// ===== حارس الليل =========================================================

@Composable
fun NightGuardScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val enabled by vm.nightGuardEnabled.collectAsStateWithLifecycle()
    val startMin by vm.nightGuardStartMin.collectAsStateWithLifecycle()
    val endMin by vm.nightGuardEndMin.collectAsStateWithLifecycle()
    val activeNow by vm.nightGuardActiveNow.collectAsStateWithLifecycle()
    val nightGuardUnlockUntil by vm.nightGuardUnlockUntil.collectAsStateWithLifecycle()
    var askPin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun apply(newStart: Int, newEnd: Int, on: Boolean = enabled) {
        scope.launch {
            val applied = vm.setNightGuard(on, newStart, newEnd)
            if (!applied) message = context.getString(R.string.feature_night_guard_locked)
        }
    }

    SettingsSectionScreen(stringResource(R.string.feature_night_guard_title), onBack) {
        DawnCard {
            SwitchRow(
                title = stringResource(R.string.feature_night_guard_enable),
                subtitle = stringResource(R.string.feature_night_guard_sub),
                checked = enabled,
                onChange = { apply(startMin, endMin, it) },
            )
            if (enabled) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(
                        if (activeNow) R.string.feature_night_guard_active else R.string.feature_night_guard_inactive,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (activeNow) SawnPalette.PrimaryDeep else SawnPalette.InkSoft,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        DawnCard {
            Text(
                stringResource(R.string.feature_night_guard_window),
                style = MaterialTheme.typography.titleMedium,
                color = SawnPalette.Ink,
            )
            Spacer(Modifier.height(10.dp))
            MinuteStepper(
                label = stringResource(R.string.feature_night_guard_start),
                minutes = startMin,
                onChange = { apply(it, endMin) },
            )
            Spacer(Modifier.height(10.dp))
            MinuteStepper(
                label = stringResource(R.string.feature_night_guard_end),
                minutes = endMin,
                onChange = { apply(startMin, it) },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.feature_night_guard_note),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
        }

        if (enabled) {
            val unlockRemaining = nightGuardUnlockUntil - System.currentTimeMillis()
            if (unlockRemaining > 0L) {
                Text(
                    stringResource(
                        R.string.feature_night_guard_unlocked,
                        (unlockRemaining / 60_000L + 1).toInt(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SawnPalette.Success,
                )
            }
            OutlinedButton(
                onClick = { askPin = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_night_guard_unlock))
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SawnPalette.Ink)
        }
    }

    if (askPin) {
        VerifyPinDialog(
            title = stringResource(R.string.feature_night_guard_unlock),
            verify = { pin -> vm.requestNightGuardUnlock(pin) },
            onSuccess = {
                askPin = false
                message = context.getString(R.string.feature_night_guard_unlock_success)
            },
            onDismiss = { askPin = false },
        )
    }
}

/** ساعة/دقيقة بزرّي + و− — بلا اعتماد على حوار الوقت. */
@Composable
private fun MinuteStepper(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val normalized = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60)
    val hour = normalized / 60
    val minute = normalized % 60
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = SawnPalette.Ink,
            modifier = Modifier.weight(1f),
        )
        StepButton("−") {
            onChange((normalized - 60 + 24 * 60) % (24 * 60))
        }
        Text(
            String.format(java.util.Locale.US, "%02d:%02d", hour, minute),
            style = MaterialTheme.typography.titleMedium,
            color = SawnPalette.PrimaryDeep,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        StepButton("+") {
            onChange((normalized + 60) % (24 * 60))
        }
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text(
            symbol,
            style = MaterialTheme.typography.titleMedium,
            color = SawnPalette.PrimaryDeep,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SawnPalette.PrimarySoft),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ===== المحفّزات ==========================================================

@Composable
fun TriggersScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val insights by vm.triggerInsights.collectAsStateWithLifecycle()
    val histogram by vm.triggerHourHistogram.collectAsStateWithLifecycle()

    SettingsSectionScreen(stringResource(R.string.feature_triggers_title), onBack) {
        Text(
            stringResource(R.string.feature_triggers_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = SawnPalette.InkSoft,
        )
        DawnCard {
            Text(
                stringResource(R.string.feature_triggers_log_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.InkSoft,
            )
            Spacer(Modifier.height(12.dp))
            TriggerTag.entries.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tag ->
                        OutlinedButton(
                            onClick = { vm.logTrigger(tag) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(tag.labelRes()))
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        DawnCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Insights, null, tint = SawnPalette.Primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.feature_triggers_insights),
                    style = MaterialTheme.typography.titleMedium,
                    color = SawnPalette.Ink,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.feature_triggers_total, insights.totalLogs),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.Ink,
            )
            Text(
                stringResource(
                    R.string.feature_triggers_top,
                    insights.topTag?.let { stringResource(it.labelRes()) } ?: "—",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.Ink,
            )
            Text(
                stringResource(R.string.feature_triggers_peak, insights.peakHour?.toString() ?: "—"),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.Ink,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.feature_triggers_by_hour),
                style = MaterialTheme.typography.labelMedium,
                color = SawnPalette.InkSoft,
            )
            Spacer(Modifier.height(6.dp))
            TriggerHourChart(histogram)
        }
    }
}

@Composable
private fun TriggerHourChart(hours: List<Int>) {
    val peak = maxOf(1, hours.maxOrNull() ?: 0)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        hours.forEach { count ->
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SawnPalette.Track),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (count > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight((count.toFloat() / peak).coerceIn(0.1f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(SawnPalette.Primary),
                    )
                }
            }
        }
    }
}

/** تسمية عربية لكل سبب. */
private fun TriggerTag.labelRes(): Int = when (this) {
    TriggerTag.BOREDOM -> R.string.feature_trigger_boredom
    TriggerTag.STRESS -> R.string.feature_trigger_stress
    TriggerTag.LONELINESS -> R.string.feature_trigger_loneliness
    TriggerTag.LATE_NIGHT -> R.string.feature_trigger_late_night
    TriggerTag.ANGER -> R.string.feature_trigger_anger
    TriggerTag.OTHER -> R.string.feature_trigger_other
}

// ===== المساءلة ===========================================================

@Composable
fun AccountabilityScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val savedName by vm.accountabilityName.collectAsStateWithLifecycle()
    val savedContact by vm.accountabilityContact.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var nameDirty by remember { mutableStateOf(false) }
    var contactDirty by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(savedName, savedContact) {
        if (!nameDirty) name = savedName.orEmpty()
        if (!contactDirty) contact = savedContact.orEmpty()
    }

    SettingsSectionScreen(stringResource(R.string.feature_accountability_title), onBack) {
        DawnCard {
            Text(
                stringResource(R.string.feature_accountability_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.InkSoft,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameDirty = true },
                label = { Text(stringResource(R.string.feature_accountability_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it; contactDirty = true },
                label = { Text(stringResource(R.string.feature_accountability_contact)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        vm.saveAccountability(name.ifBlank { null }, contact.ifBlank { null })
                        nameDirty = false
                        contactDirty = false
                        message = context.getString(R.string.feature_accountability_saved)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.save))
            }
        }

        DawnCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Groups, null, tint = SawnPalette.Primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.feature_accountability_report),
                    style = MaterialTheme.typography.titleMedium,
                    color = SawnPalette.Ink,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.feature_accountability_report_body),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { vm.shareWeeklyReport(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_accountability_share))
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SawnPalette.Success)
        }
    }
}

// ===== النسخة الاحتياطية ==================================================

@Composable
fun BackupScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    var passphrase by remember { mutableStateOf("") }
    var showPassphrase by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = when (val result = vm.exportBackup(context, uri, passphrase)) {
                is BackupResult.Success -> context.getString(R.string.feature_backup_exported)
                is BackupResult.PassphraseTooShort -> context.getString(R.string.feature_backup_short)
                is BackupResult.CannotDecrypt -> context.getString(R.string.feature_backup_wrong_pass)
                is BackupResult.Failed -> context.getString(R.string.feature_backup_failed)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = when (val result = vm.importBackup(context, uri, passphrase)) {
                is BackupResult.Success -> context.getString(R.string.feature_backup_imported)
                is BackupResult.PassphraseTooShort -> context.getString(R.string.feature_backup_short)
                is BackupResult.CannotDecrypt -> context.getString(R.string.feature_backup_wrong_pass)
                is BackupResult.Failed -> context.getString(R.string.feature_backup_failed)
            }
        }
    }

    SettingsSectionScreen(stringResource(R.string.feature_backup_title), onBack) {
        DawnCard {
            Text(
                stringResource(R.string.feature_backup_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = SawnPalette.InkSoft,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.feature_backup_passphrase)) },
                singleLine = true,
                // The passphrase decrypts the protected-day log, profile and blocklist:
                // show it masked by default with an explicit reveal affordance, so
                // recording or an onlooker cannot capture it while it is being typed.
                visualTransformation = if (showPassphrase) androidx.compose.ui.text.input.VisualTransformation.None
                else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (showPassphrase) androidx.compose.ui.text.input.KeyboardType.Text
                    else androidx.compose.ui.text.input.KeyboardType.Password,
                ),
                trailingIcon = {
                    IconButton(onClick = { showPassphrase = !showPassphrase }) {
                        Icon(
                            if (showPassphrase) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                            tint = SawnPalette.InkSoft,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.feature_backup_note),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
        }

        Button(
            onClick = { exportLauncher.launch("sawn-backup.sawnbak") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feature_backup_export))
        }
        OutlinedButton(
            onClick = { confirmImport = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.feature_backup_import))
        }

        status?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SawnPalette.Ink)
        }
    }

    // الاستيراد يستبدل القوائم والجداول — نؤكّد قبلها.
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text(stringResource(R.string.feature_backup_import)) },
            text = { Text(stringResource(R.string.feature_backup_import_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = false
                    importLauncher.launch(arrayOf("application/octet-stream", "application/zip"))
                }) {
                    Text(stringResource(R.string.feature_backup_import), color = SawnPalette.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

// ===== وضع الوالدين =======================================================

@Composable
fun ParentModeScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val enabled by vm.parentModeEnabled.collectAsStateWithLifecycle()
    val pinSet by vm.parentPinPresent.collectAsStateWithLifecycle()
    var askPin by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    SettingsSectionScreen(stringResource(R.string.feature_parent_title), onBack) {
        DawnCard {
            SwitchRow(
                title = stringResource(R.string.feature_parent_enable),
                subtitle = stringResource(R.string.feature_parent_sub),
                checked = enabled,
                onChange = { requested ->
                    scope.launch {
                        val applied = vm.setParentModeEnabled(requested)
                        if (applied && requested && !AdminManager.isActive(context)) {
                            context.startActivity(AdminManager.enableIntent(context))
                        }
                        if (!applied) {
                            message = context.getString(R.string.feature_parent_pin_missing)
                        }
                    }
                },
            )
        }

        DawnCard {
            Text(
                stringResource(R.string.feature_parent_pin_section),
                style = MaterialTheme.typography.titleMedium,
                color = SawnPalette.Ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    if (pinSet) R.string.feature_parent_pin_set_done else R.string.feature_parent_pin_missing,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { askPin = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feature_parent_pin_set))
            }
            if (pinSet && !enabled) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val removed = vm.clearParentPin()
                            message = context.getString(
                                if (removed) R.string.feature_parent_pin_removed
                                else R.string.feature_parent_pin_remove_blocked,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.feature_parent_pin_remove))
                }
            }
        }

        if (enabled) {
            DawnCard {
                Text(
                    stringResource(R.string.feature_parent_report),
                    style = MaterialTheme.typography.titleMedium,
                    color = SawnPalette.Ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.feature_parent_report_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = SawnPalette.InkSoft,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { vm.shareWeeklyReport(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.feature_accountability_share))
                }
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SawnPalette.Ink)
        }
    }

    if (askPin) {
        SetPinDialog(
            onSubmit = { pin ->
                val saved = vm.setParentPin(pin)
                if (saved && enabled) vm.setParentModeEnabled(true)
                saved
            },
            onDismiss = {
                askPin = false
                if (pinSet) message = context.getString(R.string.feature_parent_pin_set_done)
            },
        )
    }
}

// ===== عناصر مشتركة =======================================================

/** صفّ مفتاح بإطّراق صَوْن (Switch داخل بطاقة). */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = SawnPalette.Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SawnPalette.InkSoft)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
