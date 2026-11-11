package com.sawn.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.R
import com.sawn.domain.block.LockdownPolicy
import com.sawn.app.system.AdminManager
import com.sawn.app.vpn.VpnController
import kotlinx.coroutines.launch

/**
 * أدوات الرقم السري وحماية الإلغاء والقفل الزمني. تستدعيها أقسام الإعدادات، ولا تملك
 * أي بوابة رقم سري بنفسها — البوابة الوحيدة في ManagementPinGate.
 */
object SettingsControls {

    /**
     * ضرب رقم سري. يُستدعى من بطاقة الرقم ومن تفعيل الوضع الصارم معاً، ويبلغ المستخدم
     * بالفشل بدل أن ينطفئ بصمت.
     */
    @Composable
    fun PinSetup(vm: SawnViewModel, enableStrictAfter: Boolean, onDismiss: () -> Unit) {
        SetPinDialog(
            onSubmit = { pin -> vm.setPin(pin, enableStrict = enableStrictAfter) },
            onDismiss = onDismiss,
        )
    }

    @Composable
    fun PinCard(vm: SawnViewModel, strict: Boolean, hasPin: Boolean) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
        var showSetPin by remember { mutableStateOf(false) }
        var showRemovePin by remember { mutableStateOf(false) }
        var removeBlockedMessage by remember { mutableStateOf<String?>(null) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.pin_card_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (hasPin) R.string.pin_card_set else R.string.pin_card_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showSetPin = true }) {
                        Text(stringResource(if (hasPin) R.string.pin_change else R.string.pin_set_new))
                    }
                    if (hasPin) {
                        OutlinedButton(
                            onClick = { showRemovePin = true },
                            enabled = !strict && !lockdownActive,
                        ) {
                            Text(
                                stringResource(
                                    when {
                                        lockdownActive -> R.string.pin_blocked_lockdown
                                        strict -> R.string.pin_blocked_strict
                                        else -> R.string.pin_remove
                                    },
                                ),
                            )
                        }
                    }
                }
                removeBlockedMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (showSetPin) {
            PinSetup(vm = vm, enableStrictAfter = false, onDismiss = { showSetPin = false })
        }
        if (showRemovePin) {
            VerifyPinDialog(
                title = stringResource(R.string.pin_remove_title),
                verify = vm::verifyPin,
                onSuccess = {
                    showRemovePin = false
                    // المتجر يرفض الإزالة ما دام الوضع الصارم أو القفل الزمني نشطاً، فنبلغ السبب.
                    scope.launch {
                        if (!vm.clearPin()) removeBlockedMessage = context.getString(R.string.pin_remove_failed)
                    }
                },
                onDismiss = { showRemovePin = false },
            )
        }
    }

    @Composable
    fun AdminProtectionCard(strict: Boolean, vm: SawnViewModel) {
        val context = LocalContext.current
        val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
        var adminActive by remember { mutableStateOf(AdminManager.isActive(context)) }
        val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            adminActive = AdminManager.isActive(context)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.admin_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (adminActive) R.string.admin_on else R.string.admin_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (adminActive) {
                    OutlinedButton(
                        onClick = {
                            if (!strict) {
                                AdminManager.disable(context)
                                adminActive = AdminManager.isActive(context)
                            }
                        },
                        enabled = !strict && !lockdownActive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                when {
                                    lockdownActive -> R.string.pin_blocked_lockdown
                                    strict -> R.string.pin_blocked_strict
                                    else -> R.string.admin_disable
                                },
                            ),
                        )
                    }
                } else {
                    Button(
                        onClick = { adminLauncher.launch(AdminManager.enableIntent(context)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.admin_enable)) }
                }
            }
        }
    }

    /**
     * بطاقة القفل الزمني. القفل مطلق: لا رفع قبل الانتهاء. التفعيل بخطوات مرتّبة —
     * بطاقة داعمة → إذن الفلتر → شاشة Device Admin → الكتابة بعد التأكد أن الحجب يعمل
     * فعلاً، فلا تُكتب أي قيمة قبل نجاح السلسلة كلها.
     */
    @Composable
    fun LockdownCard(vm: SawnViewModel) {
        val context = LocalContext.current
        val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
        val remaining by vm.lockdownRemaining.collectAsStateWithLifecycle()
        val streak by vm.currentStreak.collectAsStateWithLifecycle()
        val sessions by vm.focusSessionsDone.collectAsStateWithLifecycle()

        var pendingDurationMs by remember { mutableStateOf<Long?>(null) }
        var showNewLockOptions by remember { mutableStateOf(false) }
        var manualOpen by remember { mutableStateOf(false) }
        var manualAmount by remember { mutableStateOf("") }
        var manualUnit by remember { mutableStateOf(LockdownPolicy.DurationUnit.MINUTES) }
        var manualError by remember { mutableStateOf(false) }
        var activationError by remember { mutableStateOf<ActivationError?>(null) }

        fun commitStaged() {
            vm.commitLockdown { success ->
                activationError = if (success) null else ActivationError.NOT_APPLIED
            }
        }

        val adminLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (AdminManager.isActive(context) && vm.hasStagedLockdown()) {
                commitStaged()
            } else {
                vm.discardStagedLockdown()
                activationError = ActivationError.ADMIN
            }
        }

        val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (AdminManager.isActive(context)) commitStaged()
                else adminLauncher.launch(AdminManager.enableIntent(context))
            } else {
                vm.discardStagedLockdown()
                activationError = ActivationError.FILTER_CONSENT
            }
        }

        fun continueActivation() {
            val consent = VpnController.consentIntent(context)
            when {
                consent != null -> vpnLauncher.launch(consent)
                AdminManager.isActive(context) -> commitStaged()
                else -> adminLauncher.launch(AdminManager.enableIntent(context))
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.lockdown_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (lockdownActive) {
                    Text(
                        stringResource(R.string.remaining_time, formatRemaining(remaining)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.lockdown_active_body_card),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.lockdown_ends_automatically),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!showNewLockOptions) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                activationError = null
                                showNewLockOptions = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.lockdown_new)) }
                    }
                } else {
                    Text(
                        stringResource(R.string.lockdown_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!lockdownActive || showNewLockOptions) {
                    if (lockdownActive) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.lockdown_replaces),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    QuickDurationGrid { durationMs ->
                        activationError = null
                        pendingDurationMs = durationMs
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.lockdown_custom_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    if (manualOpen) {
                        OutlinedTextField(
                            value = manualAmount,
                            onValueChange = {
                                // نقبل الأرقام العربية-الفارسية ونحوّلها فوراً إلى ASCII،
                                // لأن toLongOrNull لا يقرأ ٠١٢.
                                val normalized = LockdownPolicy.normalizeDigits(it)
                                if (normalized.length <= 5 && normalized.all { c -> c in '0'..'9' }) {
                                    manualAmount = normalized
                                    manualError = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = manualError,
                            label = { Text(stringResource(R.string.lockdown_duration_field)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LockdownPolicy.DurationUnit.entries.forEach { unit ->
                                FilterChip(
                                    selected = manualUnit == unit,
                                    onClick = { manualUnit = unit },
                                    label = { Text(unitLabel(unit)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                val millis = LockdownPolicy.validateManual(
                                    LockdownPolicy.parseManualAmount(manualAmount) ?: 0L,
                                    manualUnit,
                                )
                                if (millis == null) {
                                    manualError = true
                                } else {
                                    manualError = false
                                    activationError = null
                                    pendingDurationMs = millis
                                }
                            },
                            enabled = manualAmount.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.lockdown_apply)) }
                        if (manualError) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.lockdown_invalid_range),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { manualOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.lockdown_custom_open)) }
                    }
                }

                activationError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            when (error) {
                                ActivationError.FILTER -> R.string.lockdown_no_filter
                                ActivationError.ADMIN -> R.string.lockdown_no_admin
                                ActivationError.FILTER_CONSENT -> R.string.lockdown_no_vpn_permission
                                ActivationError.NOT_APPLIED -> R.string.lockdown_not_applied
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        pendingDurationMs?.let { durationMs ->
            SupportiveConfirmDialog(
                streak = streak,
                sessions = sessions,
                onDismiss = { pendingDurationMs = null },
                onConfirmed = {
                    pendingDurationMs = null
                    activationError = null
                    vm.stageLockdownForDuration(durationMs)
                    continueActivation()
                },
            )
        }
    }

    private enum class ActivationError { FILTER, ADMIN, FILTER_CONSENT, NOT_APPLIED }

    private fun unitMillis(unit: LockdownPolicy.DurationUnit): Long = when (unit) {
        LockdownPolicy.DurationUnit.MINUTES -> 60_000L
        LockdownPolicy.DurationUnit.HOURS -> 3_600_000L
        LockdownPolicy.DurationUnit.DAYS -> 86_400_000L
        LockdownPolicy.DurationUnit.WEEKS -> 7 * 86_400_000L
        LockdownPolicy.DurationUnit.MONTHS -> 30L * 86_400_000L
    }

    @Composable
    private fun unitLabel(unit: LockdownPolicy.DurationUnit): String = stringResource(
        when (unit) {
            LockdownPolicy.DurationUnit.MINUTES -> R.string.unit_minutes
            LockdownPolicy.DurationUnit.HOURS -> R.string.unit_hours
            LockdownPolicy.DurationUnit.DAYS -> R.string.unit_days
            LockdownPolicy.DurationUnit.WEEKS -> R.string.unit_weeks
            LockdownPolicy.DurationUnit.MONTHS -> R.string.unit_months
        },
    )

    private data class QuickDuration(
        val labelRes: Int,
        val amount: Long,
        val unit: LockdownPolicy.DurationUnit,
    )

    private val quickDurations = listOf(
        QuickDuration(R.string.lockdown_30_min, 30L, LockdownPolicy.DurationUnit.MINUTES),
        QuickDuration(R.string.lockdown_1_hour, 1L, LockdownPolicy.DurationUnit.HOURS),
        QuickDuration(R.string.lockdown_6_hours, 6L, LockdownPolicy.DurationUnit.HOURS),
        QuickDuration(R.string.lockdown_1_day, 1L, LockdownPolicy.DurationUnit.DAYS),
        QuickDuration(R.string.lockdown_3_days, 3L, LockdownPolicy.DurationUnit.DAYS),
        QuickDuration(R.string.lockdown_1_week, 1L, LockdownPolicy.DurationUnit.WEEKS),
        QuickDuration(R.string.lockdown_2_weeks, 2L, LockdownPolicy.DurationUnit.WEEKS),
        QuickDuration(R.string.lockdown_1_month, 1L, LockdownPolicy.DurationUnit.MONTHS),
        QuickDuration(R.string.lockdown_3_months, 3L, LockdownPolicy.DurationUnit.MONTHS),
        QuickDuration(R.string.lockdown_6_months, 6L, LockdownPolicy.DurationUnit.MONTHS),
    )

    /** أزرار المدة في شبكة عمودين. */
    @Composable
    private fun QuickDurationGrid(onPick: (Long) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quickDurations.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        OutlinedButton(
                            onClick = { onPick(item.amount * unitMillis(item.unit)) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(item.labelRes)) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    /** «3ي 5س» أو «12س 40د 5ث» — اختصارات زمنية بلا سياق لغوي. */
    fun formatRemaining(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val days = totalSec / 86_400
        val hours = totalSec % 86_400 / 3_600
        val minutes = totalSec % 3_600 / 60
        val seconds = totalSec % 60
        return when {
            days > 0 -> "${days}ي ${hours}س"
            hours > 0 -> "${hours}س ${minutes}د"
            else -> "${minutes}د ${seconds}ث"
        }
    }
}
