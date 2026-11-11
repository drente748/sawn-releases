package com.sawn.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.BuildConfig
import com.sawn.app.R
import com.sawn.domain.block.web.BrowserConfig
import com.sawn.app.ui.SettingsSectionScreen
import com.sawn.app.ui.SawnViewModel
import com.sawn.app.ui.rememberResumeTick
import com.sawn.app.util.PermissionsHelper
import com.sawn.app.sawnContainer
import com.sawn.app.update.InstallUpdateResult
import com.sawn.app.update.UpdateInstaller
import com.sawn.app.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * أقسام الإعدادات: الحماية / الخصوصية والدعم.
 * بوابة الرقم السري واحدة يملكها MainActivity عبر ManagementPinGate، فلا تكرر هذه
 * الشاشات طلب الرقم.
 *
 * لا مفاتيح حماية هنا: مفاتيح الفلتر والوضع الصارم والقفل الزمني على الصفحة
 * الرئيسية، والرقم السري وحماية الإلغاء على شاشة الإعدادات نفسها.
 */
private const val HELPLINE_URL = "https://findahelpline.com/ar"

/**
 * قسم «الحماية» — ما لا يوجد مكان غيره: قوائم الحجب الجماعية، المتصفحات المدعومة،
 * وتلميحات التلافي.
 *
 * مفاتيح الحماية نفسها (فلتر الإباحية، SafeSearch، الإعلانات، DoH، الوضع الدائم،
 * الوضع الصارم، القفل الزمني) لا تُكرَّر هنا — الصفحة الرئيسية هي مكانها الوحيد.
 */
@Composable
fun ProtectionSettingsScreen(vm: SawnViewModel, onBack: () -> Unit) {
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()

    SettingsSectionScreen(stringResource(R.string.settings_protection), onBack) {
        if (lockdownActive) {
            InfoCard(
                title = stringResource(R.string.lockdown_active_title),
                body = stringResource(R.string.lockdown_active_body),
                tinted = true,
            )
        }

        InfoCard(
            title = stringResource(R.string.settings_protection_home_title),
            body = stringResource(R.string.settings_protection_home_body),
        )

        AdultListsCard(vm)

        InfoCard(
            title = stringResource(R.string.browsers_title),
            body = stringResource(R.string.browsers_count, BrowserConfig.ALL.size),
        )
        InfoCard(
            title = stringResource(R.string.doh_hint_title),
            body = stringResource(R.string.doh_hint_body),
        )
    }
}

@Composable
private fun AdultListsCard(vm: SawnViewModel) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.lists_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.lists_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    // ردّ التحديث يصل خارج التراكيب، فيُقرأ النص من السياق لا من stringResource.
                    vm.refreshAdultLists { result ->
                        busy = false
                        failed = result.error != null
                        message = when {
                            result.error != null -> context.getString(R.string.lists_update_failed)
                            result.skippedSameContent -> context.getString(R.string.lists_up_to_date)
                            else -> context.getString(R.string.lists_updated, result.added)
                        }
                    }
                },
            ) {
                Text(stringResource(if (busy) R.string.lists_updating else R.string.lists_update_action))
            }
            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun PrivacySupportSettingsScreen(vm: SawnViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val reduceMotion by vm.reduceMotion.collectAsStateWithLifecycle()
    val blockNotifications by vm.blockNotifications.collectAsStateWithLifecycle()
    val resumeTick = rememberResumeTick()
    val notificationsGranted = remember(resumeTick) { PermissionsHelper.hasNotifications(context) }
    val scope = rememberCoroutineScope()
    var updateStatus by remember { mutableStateOf<UpdateStatus?>(null) }
    var installUri by remember { mutableStateOf<Uri?>(null) }
    val autoUpdateEnabled by vm.autoUpdateEnabled.collectAsStateWithLifecycle()
    val autoDownloadEnabled by vm.autoDownloadEnabled.collectAsStateWithLifecycle()
    val autoNotifyEnabled by vm.autoNotifyEnabled.collectAsStateWithLifecycle()
    val pendingUpdate by vm.pendingUpdate.collectAsStateWithLifecycle()
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val uri = installUri ?: return@rememberLauncherForActivityResult
        if (android.os.Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) {
            runCatching { UpdateInstaller.install(context, uri) }
                .onFailure { updateStatus = UpdateStatus.Error(it.message ?: "unknown") }
        } else {
            updateStatus = UpdateStatus.Error(context.getString(R.string.update_install_permission_missing))
        }
    }

    SettingsSectionScreen(stringResource(R.string.settings_privacy), onBack) {
        // حالة فقط — مفتاح «كتم الإشعارات» على الصفحة الرئيسية، لا هنا.
        InfoCard(
            title = stringResource(R.string.notifications_title),
            body = stringResource(
                if (blockNotifications) R.string.notifications_status_on else R.string.notifications_status_off,
            ),
        )
        if (blockNotifications && !notificationsGranted) {
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.notifications_grant)) }
        }

        InfoCard(title = stringResource(R.string.privacy_title), body = stringResource(R.string.privacy_body))

        UpdateSettingsCard(
            status = updateStatus,
            autoUpdate = autoUpdateEnabled,
            autoDownload = autoDownloadEnabled,
            autoNotify = autoNotifyEnabled,
            pending = pendingUpdate,
            onAutoUpdateChange = { enabled ->
                scope.launch { vm.setAutoUpdateEnabled(enabled) }
            },
            onAutoDownloadChange = { enabled ->
                scope.launch { vm.setAutoDownloadEnabled(enabled) }
            },
            onAutoNotifyChange = { enabled ->
                scope.launch { vm.setAutoNotifyEnabled(enabled) }
            },
            onCheck = {
                updateStatus = UpdateStatus.Checking
                scope.launch {
                    updateStatus = runCatching {
                        withContext(Dispatchers.IO) {
                            context.sawnContainer.updateChecker.check(BuildConfig.VERSION_CODE)
                        }
                    }.getOrElse { UpdateStatus.Error(it.message ?: "unknown") }
                    if (updateStatus == UpdateStatus.Disabled) {
                        updateStatus = UpdateStatus.Error(context.getString(R.string.update_host_not_configured))
                    }
                }
            },
            onInstallPending = { pending ->
                val uri = UpdateInstaller.uriFor(context, pending.apkPath)
                if (uri == null) {
                    updateStatus = UpdateStatus.Error(context.getString(R.string.update_failed_pending))
                    return@UpdateSettingsCard
                }
                if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                    runCatching {
                        installPermissionLauncher.launch(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                        )
                    }.onFailure { updateStatus = UpdateStatus.Error(it.message ?: "unknown") }
                } else {
                    UpdateInstaller.install(context, uri)
                    updateStatus = UpdateStatus.Installing
                }
            },
            onInstall = { info ->
                updateStatus = UpdateStatus.Downloading
                scope.launch {
                    when (val result = UpdateInstaller.downloadAndVerify(context, info)) {
                        is InstallUpdateResult.Failed -> updateStatus = UpdateStatus.Error(result.reason)
                        is InstallUpdateResult.Ready -> {
                            installUri = result.packageUri
                            // نفس ما يفعله العامل التلقائي: يُسجَّل التحديث المعلّق
                            // فيبقى في بطاقة «جاهز للتثبيت» وفي الإشعار.
                            val apk = UpdateInstaller.apkFileFor(context, info.versionCode)
                            if (apk.exists()) vm.savePendingUpdate(info, apk.absolutePath)
                            if (android.os.Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                                runCatching {
                                    installPermissionLauncher.launch(
                                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                                    )
                                }.onFailure { updateStatus = UpdateStatus.Error(it.message ?: "unknown") }
                            } else {
                                UpdateInstaller.install(context, result.packageUri)
                                // The APK is verified and Android's installer is open — the
                                // update is NOT installed yet. Saying "up to date" here told
                                // the user they had the newest build while it was still pending.
                                updateStatus = UpdateStatus.Installing
                            }
                        }
                    }
                }
            },
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.help_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(HELPLINE_URL)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.help_action)) }
            }
        }

        SwitchRow(
            title = stringResource(R.string.reduce_motion_title),
            subtitle = stringResource(R.string.reduce_motion_sub),
            checked = reduceMotion,
            onChange = vm::setReduceMotion,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Verified,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateSettingsCard(
    status: UpdateStatus?,
    autoUpdate: Boolean,
    autoDownload: Boolean,
    autoNotify: Boolean,
    pending: com.sawn.domain.update.PendingUpdate?,
    onAutoUpdateChange: (Boolean) -> Unit,
    onAutoDownloadChange: (Boolean) -> Unit,
    onAutoNotifyChange: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onInstallPending: (com.sawn.domain.update.PendingUpdate) -> Unit,
    onInstall: (com.sawn.app.update.UpdateInfo) -> Unit,
) {    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.update_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // --- التحديث التلقائي (مفعّل افتراضياً) ---
            Spacer(Modifier.height(12.dp))
            SwitchRow(
                title = stringResource(R.string.update_auto_title),
                subtitle = stringResource(R.string.update_auto_sub),
                checked = autoUpdate,
                onChange = onAutoUpdateChange,
            )
            if (autoUpdate) {
                SwitchRow(
                    title = stringResource(R.string.update_auto_download_title),
                    subtitle = stringResource(R.string.update_auto_download_sub),
                    checked = autoDownload,
                    onChange = onAutoDownloadChange,
                )
                SwitchRow(
                    title = stringResource(R.string.update_auto_install_title),
                    subtitle = stringResource(R.string.update_auto_install_sub),
                    checked = autoNotify,
                    onChange = onAutoNotifyChange,
                )
            }

            // --- تحديث جاهز ومُتحقَّق منه ---
            if (pending != null) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            stringResource(R.string.update_pending_title, pending.versionName),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.update_pending_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onInstallPending(pending) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.update_install_now)) }
                    }
                }
            }

            if (status != null) {
                Spacer(Modifier.height(8.dp))
                when (status) {
                    UpdateStatus.Checking -> Text(stringResource(R.string.update_checking))
                    UpdateStatus.Downloading -> Text(stringResource(R.string.update_downloading))
                    UpdateStatus.Installing -> Text(stringResource(R.string.update_installing))
                    UpdateStatus.UpToDate -> Text(stringResource(R.string.update_up_to_date))
                    UpdateStatus.Disabled -> Text(stringResource(R.string.update_host_not_configured))
                    is UpdateStatus.Error -> Text(
                        stringResource(R.string.update_failed, status.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    is UpdateStatus.Available -> {
                        Text(stringResource(R.string.update_available, status.info.versionName))
                        if (status.info.changelog.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(status.info.changelog, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onInstall(status.info) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.update_install))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_check))
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, tinted: Boolean = false) {
    Card(
        Modifier.fillMaxWidth(),
        colors = if (tinted) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (tinted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = if (tinted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** صفّ مفتاح — للإعدادات التي لا يوجد لها مكان على الصفحة الرئيسية. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
    }
}
