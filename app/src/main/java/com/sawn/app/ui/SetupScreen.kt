package com.sawn.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.R
import com.sawn.domain.feature.CheckupState
import com.sawn.domain.feature.ProtectionCheckup
import com.sawn.app.util.PermissionsHelper
import com.sawn.app.vpn.VpnController
import com.sawn.app.vpn.SawnVpnService

/** لقطة أذونات الجهاز — تُعاد قراءتها عند كل ON_RESUME فتتحدث فور العودة من النظام. */
private data class SetupState(
    val accessibility: Boolean,
    val filter: Boolean,
    val vpnConsent: Boolean,
    val notificationListener: Boolean,
    val usage: Boolean,
    val notifications: Boolean,
    val alwaysOnVpn: Boolean,
    val batteryFree: Boolean,
    val privateDnsStrict: Boolean,
    val restrictedNeeded: Boolean,
)

private fun readSetupState(context: Context): SetupState {
    val accessibility = PermissionsHelper.isAccessibilityEnabled(context)
    return SetupState(
        accessibility = accessibility,
        filter = SawnVpnService.running,
        vpnConsent = PermissionsHelper.hasVpnConsent(context),
        notificationListener = PermissionsHelper.isNotificationListenerEnabled(context),
        usage = PermissionsHelper.hasUsageAccess(context),
        notifications = PermissionsHelper.hasNotifications(context),
        alwaysOnVpn = PermissionsHelper.isAlwaysOnVpnConfigured(context),
        batteryFree = PermissionsHelper.isBatteryOptimizationIgnored(context),
        privateDnsStrict = PermissionsHelper.isPrivateDnsStrict(context),
        // أندرويد 13+ يمنع الأذونات الخاصة عن النسخ المثبّتة خارج المتاجر حتى يُتاح
        // المصدر من «معلومات التطبيق» — وهو شرطٌ يسبق إمكانية الوصول.
        restrictedNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !accessibility && PermissionsHelper.isSideloaded(context),
    )
}

/**
 * فحص الحماية: لوحة واحدة تقول كم طبقة تعمل، وكل صفٍّ يفتح إعداد النظام الصحيح
 * مباشرة. هذا يستبدل قائمة أذوناتٍ عمياء: المستخدم يرى الأثر (درجة) ويعرف موضع
 * الخلل، ويعود فتتحدث الحالة وحدها.
 */
@Composable
fun SetupScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val vpnRunning by SawnVpnService.runningFlow.collectAsStateWithLifecycle()

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refresh++ }

    val state = remember(refresh, vpnRunning) {
        readSetupState(context).copy(filter = vpnRunning)
    }

    val checkup = ProtectionCheckup.evaluate(
        CheckupState(
            accessibility = state.accessibility,
            filter = state.filter,
            usage = state.usage,
            notifications = state.notifications,
        ),
    )

    val openSettings = stringResource(R.string.open_settings)
    val essential = listOf(state.accessibility, state.vpnConsent, state.filter)
    val extras = buildList {
        add(state.notifications)
        add(state.notificationListener)
        add(state.usage)
        add(state.alwaysOnVpn)
        add(state.batteryFree)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(!state.privateDnsStrict)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
        BackHeader(
            stringResource(R.string.checkup_title),
            onBack,
            stringResource(R.string.checkup_intro),
        )
        Spacer(Modifier.height(14.dp))

        ScoreCard(checkup.score, checkup.essentialMissing, checkup.complete)
        Spacer(Modifier.height(16.dp))

        SectionLabel(
            stringResource(R.string.checkup_layers),
            passed = essential.count { it },
            total = essential.size,
        )

        SetupRow(
            title = stringResource(R.string.setup_accessibility_title),
            subtitle = stringResource(R.string.setup_accessibility_sub),
            granted = state.accessibility,
            actionLabel = openSettings,
        ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        if (state.restrictedNeeded) {
            RestrictedSourceCard(
                onOpenAppInfo = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )
        }

        // إذن الاتصال: كان غائباً من شاشة الإعداد تماماً، فلا يعرف المستخدم لماذا
        // لا يبدأ الفلتر. الآن يُطلب من هنا مباشرة، ويُختبر بعد العودة.
        SetupRow(
            title = stringResource(R.string.checkup_consent_title),
            subtitle = stringResource(R.string.checkup_consent_sub),
            granted = state.vpnConsent,
            actionLabel = stringResource(R.string.checkup_consent_action),
        ) {
            val consent = VpnController.consentIntent(context)
            if (consent != null) consentLauncher.launch(consent)
            else VpnController.start(context)
        }

        SetupRow(
            title = stringResource(R.string.checkup_filter_title),
            subtitle = stringResource(R.string.checkup_filter_sub),
            granted = state.filter,
            actionLabel = stringResource(R.string.checkup_filter_action),
        ) {
            val consent = VpnController.consentIntent(context)
            if (consent != null) consentLauncher.launch(consent) else VpnController.start(context)
        }

        SectionLabel(
            stringResource(R.string.checkup_extras),
            passed = extras.count { it },
            total = extras.size,
        )

        SetupRow(
            title = stringResource(R.string.setup_notifications_title),
            subtitle = stringResource(R.string.setup_notifications_sub),
            granted = state.notifications,
            actionLabel = openSettings,
        ) {
            val runtimePrompt = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (runtimePrompt) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }

        SetupRow(
            title = stringResource(R.string.checkup_listener_title),
            subtitle = stringResource(R.string.checkup_listener_sub),
            granted = state.notificationListener,
            actionLabel = openSettings,
        ) {
            runCatching {
                context.startActivity(
                    Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }

        SetupRow(
            title = stringResource(R.string.setup_usage_title),
            subtitle = stringResource(R.string.setup_usage_sub),
            granted = state.usage,
            actionLabel = openSettings,
        ) { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        SetupRow(
            title = stringResource(R.string.setup_vpn_title),
            subtitle = stringResource(R.string.setup_vpn_sub),
            granted = state.alwaysOnVpn,
            actionLabel = openSettings,
        ) { runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) } }

        SetupRow(
            title = stringResource(R.string.setup_battery_title),
            subtitle = stringResource(R.string.setup_battery_sub),
            granted = state.batteryFree,
            actionLabel = stringResource(R.string.setup_battery_direct),
        ) { PermissionsHelper.requestBatteryExemption(context) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            SetupRow(
                title = stringResource(R.string.setup_dns_title),
                subtitle = stringResource(
                    if (state.privateDnsStrict) R.string.setup_dns_strict else R.string.setup_dns_safe_hint,
                ),
                granted = !state.privateDnsStrict,
                actionLabel = openSettings,
            ) {
                runCatching {
                    context.startActivity(Intent("android.settings.PRIVATE_DNS_SETTINGS"))
                }.onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }

        Spacer(Modifier.height(18.dp))
        if (checkup.complete) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.checkup_complete),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }

    // مراقبة الأذونات التي تُمنح من إعدادات النظام: عند العودة نقرأها من جديد.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, refresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun ScoreCard(score: Int, essentialMissing: Boolean, complete: Boolean) {
    val ringColor = when {
        complete -> MaterialTheme.colorScheme.primary
        essentialMissing -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.15f),
                        radius = size.minDimension / 2f,
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.checkup_score, score),
                        style = MaterialTheme.typography.headlineSmall,
                        color = ringColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.checkup_score_caption),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = when {
                        complete -> stringResource(R.string.checkup_complete)
                        essentialMissing -> stringResource(R.string.checkup_essential_missing)
                        else -> stringResource(R.string.checkup_partial)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(R.string.checkup_summary, score, 100),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { /* إعادة القراءة تحدث عند كل عودة؛ الزر يوضّح الحالة فقط */ },
                    enabled = false,
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        stringResource(if (complete) R.string.checkup_ready else R.string.checkup_pending),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestrictedSourceCard(onOpenAppInfo: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.setup_restricted_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.setup_restricted_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(stringResource(R.string.setup_restricted_steps), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onOpenAppInfo) {
                Text(stringResource(R.string.setup_open_app_info))
            }
        }
    }
}

/** عنوان قسم مع عدّاد صغير (منجز/الإجمالي) حتى يعرف المستخدم حجم ما تبقّى بنظرة. */
@Composable
private fun SectionLabel(text: String, passed: Int? = null, total: Int? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (passed != null && total != null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                Text(
                    SawnNumbers.format(passed) + "/" + SawnNumbers.format(total),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * بطاقة إذن واحدة: أيقونة في مربع ملون، عنوان وسطر شرح بخطين منظّمين، شارة حالة،
 * وزر الإجراء وحده أسفل البطاقة عند النقص — فيبقى الصف مقروءاً ولا يتزاحم النص مع الزر.
 */
@Composable
private fun SetupRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val accent = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Card(
        Modifier.fillMaxWidth().padding(bottom = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (granted) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = stringResource(if (granted) R.string.granted else R.string.not_granted),
                        tint = accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(granted)
            }
            if (!granted) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onAction, modifier = Modifier.height(40.dp)) {
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    val container = if (granted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (granted) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = CircleShape, color = container) {
        Text(
            stringResource(if (granted) R.string.checkup_ready else R.string.checkup_pending),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
