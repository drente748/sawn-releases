package com.sawn.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.foundation.Canvas

import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.R
import com.sawn.domain.model.BlockType
import com.sawn.core.data.BlockedItem
import com.sawn.domain.feature.ParentalModePolicy
import com.sawn.domain.feature.FeatureCatalog
import com.sawn.domain.feature.SocialPlatform
import com.sawn.domain.model.InterceptMode
import com.sawn.core.data.Schedule
import com.sawn.domain.security.PinResult
import com.sawn.app.ui.status.CoverageLevel
import com.sawn.app.ui.status.CoverageState
import com.sawn.app.ui.status.coverageLevel
import com.sawn.app.usage.AppsHelper
import com.sawn.app.usage.UsageStatsHelper
import com.sawn.app.util.PermissionsHelper
import com.sawn.app.vpn.SawnVpnService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun BackHeader(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BlocklistScreen(
    vm: SawnViewModel,
    onOpenAppPicker: () -> Unit,
    onBack: () -> Unit = {},
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val enabledFeatures by vm.enabledFeatureKeys.collectAsStateWithLifecycle()
    val socialGuardEnabled by vm.socialGuardEnabled.collectAsStateWithLifecycle()
    val enabledSocialPlatforms by vm.enabledSocialPlatforms.collectAsStateWithLifecycle()
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<BlockedItem?>(null) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackHeader(
            stringResource(R.string.blocklist_title),
            onBack,
            stringResource(R.string.blocklist_subtitle),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.blocklist_website_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { vm.addWebsite(input); input = "" },
                enabled = input.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(stringResource(R.string.add)) }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onOpenAppPicker, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_apps))
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "in_app_protection") {
                InAppProtectionSettings(
                    vm = vm,
                    socialGuardEnabled = socialGuardEnabled,
                    enabledSocialPlatforms = enabledSocialPlatforms,
                    enabledFeatures = enabledFeatures,
                    locked = lockdownActive,
                )
            }
            if (items.isEmpty()) {
                item(key = "empty_blocklist") {
                    Text(
                        stringResource(R.string.blocklist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth().clickable { editing = item }) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.type == BlockType.APP) {
                            AppIcon(item.value, modifier = Modifier.size(40.dp).padding(end = 12.dp))
                        } else {
                            Icon(
                                Icons.Filled.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp).padding(end = 12.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                itemSummary(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = item.enabled, onCheckedChange = { vm.toggleItem(item) })
                        IconButton(onClick = { vm.removeItem(item) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
            }
        }
    }

    editing?.let { current ->
        ItemEditorSheet(
            item = current,
            onSave = { vm.saveItem(it) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun InAppProtectionSettings(
    vm: SawnViewModel,
    socialGuardEnabled: Boolean,
    enabledSocialPlatforms: Set<String>,
    enabledFeatures: Set<String>,
    locked: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.feature_blocking_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.feature_blocking_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_blocking_social),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = socialGuardEnabled, enabled = !locked || socialGuardEnabled, onCheckedChange = vm::setSocialGuardEnabled)
            }
            if (socialGuardEnabled) {
                SocialPlatform.entries.forEach { platform ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(platform.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        val checked = platform.key in enabledSocialPlatforms
                        Switch(
                            checked = checked,
                            enabled = !locked || checked,
                            onCheckedChange = { vm.setSocialPlatformEnabled(platform.key, it) },
                        )
                    }
                }
            }
            Text(stringResource(R.string.feature_blocking_feeds), style = MaterialTheme.typography.titleSmall)
            FeatureCatalog.ALL.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.feature_blocking_feed, feature.label),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val checked = feature.key in enabledFeatures
                    Switch(
                        checked = checked,
                        enabled = !locked || checked,
                        onCheckedChange = { vm.setFeatureEnabled(feature.key, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun itemSummary(item: BlockedItem): String = when {
    item.type == BlockType.WEBSITE -> stringResource(R.string.summary_website_hard)
    item.interceptMode == InterceptMode.BLOCK -> stringResource(R.string.summary_app_hard)
    else -> buildString {
        append(stringResource(R.string.summary_app_pause, item.pauseSeconds))
        if (item.openLimitPerDay > 0) append(" ").append(stringResource(R.string.summary_app_pause_opens, item.openLimitPerDay))
        if (item.dailyLimitMinutes > 0) append(" ").append(stringResource(R.string.summary_app_pause_limit, item.dailyLimitMinutes))
    }
}

@Composable
fun SchedulesScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val schedules by vm.schedules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Schedule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        BackHeader(stringResource(R.string.schedules_title), onBack)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.schedules_hint), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.new_schedule))
        }
        Spacer(Modifier.height(16.dp))
        if (schedules.isEmpty()) {
            Text(
                stringResource(R.string.schedules_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(schedules, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth().clickable { editing = s }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${fmt(s.startMinute)} – ${fmt(s.endMinute)}  •  ${daysLabel(s.daysMask)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = s.enabled, onCheckedChange = { vm.addSchedule(s.copy(enabled = it)) })
                        IconButton(onClick = { vm.removeSchedule(s) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        ScheduleEditorDialog(initial = null, onSave = { vm.addSchedule(it) }, onDismiss = { creating = false })
    }
    editing?.let { current ->
        ScheduleEditorDialog(initial = current, onSave = { vm.addSchedule(it) }, onDismiss = { editing = null })
    }
}

private data class UsageRow(val pkg: String, val label: String, val millis: Long)

/** Re-reads permission-backed state every time the screen comes back to the foreground. */
@Composable
internal fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    return remember(owner) { mutableStateOf(0) }.also { tick ->
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick.value++
        }
        DisposableEffect(owner) {
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
    }.value
}

@Composable
fun StatsScreen(vm: SawnViewModel, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val resumeTick = rememberResumeTick()
    val hasPermission = remember(resumeTick) { UsageStatsHelper.hasPermission(context) }
    val streak by vm.currentStreak.collectAsStateWithLifecycle()
    val longest by vm.longestStreak.collectAsStateWithLifecycle()
    val sessions by vm.focusSessionsDone.collectAsStateWithLifecycle()
    val journal by vm.blockJournal.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader(stringResource(R.string.stats_title), onBack)

        // بطل الشاشة: السلسلة أولاً لأنها الشيء الذي يعود المستخدم لرؤيته.
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.stats_hero_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            SawnNumbers.format(streak.toLong()),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.stats_days_unit),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${stringResource(R.string.best_streak)} · $longest",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "${stringResource(R.string.focus_sessions)} · $sessions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.stats_week_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                ProtectionWeekChart(journal)
                Text(
                    stringResource(R.string.stats_week_total, journal.weekTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.stats_urge_week_total, journal.urgesLast7, journal.urgesTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(stringResource(R.string.screen_time_today), style = MaterialTheme.typography.titleMedium)
        if (!hasPermission) {
            Text(stringResource(R.string.usage_access_needed), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                Text(stringResource(R.string.grant_usage_access))
            }
        } else {
            val rows by produceState<List<UsageRow>?>(initialValue = null, resumeTick) {
                value = withContext(Dispatchers.Default) {
                    UsageStatsHelper.todayUsage(context).take(25)
                        .map { UsageRow(it.packageName, AppsHelper.labelFor(context, it.packageName), it.totalMillis) }
                }
            }
            val list = rows
            if (list != null && list.isEmpty()) {
                Text(stringResource(R.string.no_usage_today), style = MaterialTheme.typography.bodyMedium)
            } else if (list != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    list.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(u.pkg, modifier = Modifier.size(32.dp).padding(end = 12.dp))
                            Text(
                                u.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(fmtDuration(u.millis), style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * بوابة الرقم السري لإدارة التطبيق. حالة الفتح واحدة مشتركة يملكها MainActivity حتى لا
 * يطلب الرقم مراراً أثناء التنقل بين الأقسام، وتُغلق من جديد عند إعادة الدخول للتطبيق.
 *
 * في وضع ولي الأمر (مع رقم ولي الأمر مُعيَّن) يُطلب رقم الوالدين بدل رقم المستخدم —
 * هو الذي يحرس الإدارة.
 */
@Composable
fun ManagementPinGate(
    vm: SawnViewModel,
    title: String,
    unlocked: Boolean,
    onUnlocked: () -> Unit,
    content: @Composable () -> Unit,
) {
    val settingsReady by vm.settingsReady.collectAsStateWithLifecycle()
    val pinConfigured by vm.pinConfigured.collectAsStateWithLifecycle()
    val parentMode by vm.parentModeEnabled.collectAsStateWithLifecycle()
    val parentPinSet by vm.parentPinPresent.collectAsStateWithLifecycle()
    var showUnlock by remember { mutableStateOf(false) }

    val parentLocked = ParentalModePolicy.isManagementLocked(parentPinSet, parentMode)

    // حتى يصل أول لقطة من DataStore لا نعرف إن كان رقم سري موجوداً، فلا نعرض الواجهة.
    if (!settingsReady || pinConfigured == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if ((pinConfigured == true || parentLocked) && !unlocked) {
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.screen_is_locked, title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = { showUnlock = true }) { Text(stringResource(R.string.unlock_with_pin)) }
        }
        if (showUnlock) {
            VerifyPinDialog(
                title = stringResource(
                    if (parentLocked) R.string.parent_unlock_title else R.string.enter_pin,
                ),
                verify = { pin ->
                    if (parentLocked) vm.verifyParentPin(pin) else vm.verifyPin(pin)
                },
                onSuccess = {
                    showUnlock = false
                    onUnlocked()
                },
                onDismiss = { showUnlock = false },
            )
        }
        return
    }
    content()
}

/** هيكل شاشة قسم داخل الإعدادات: عنوان مع رجوع + عمود قابل للتمرير. */
@Composable
fun SettingsSectionScreen(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BackHeader(title, onBack)
        content()
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// --- مساعِدة تنسيق ---
private fun fmt(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

@Composable
private fun daysLabel(mask: Int): String {
    val names = listOf(
        stringResource(R.string.day_mon), stringResource(R.string.day_tue), stringResource(R.string.day_wed),
        stringResource(R.string.day_thu), stringResource(R.string.day_fri), stringResource(R.string.day_sat),
        stringResource(R.string.day_sun),
    )
    val picked = names.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }
    return if (picked.size == 7) stringResource(R.string.every_day) else picked.joinToString("، ")
}

@Composable
private fun fmtDuration(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return if (h > 0) stringResource(R.string.hours_minutes, h, m) else stringResource(R.string.minutes_short, m)
}
