package com.sawn.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sawn.app.BuildConfig
import com.sawn.app.R
import com.sawn.app.ui.DawnCard
import com.sawn.app.ui.SawnViewModel
import com.sawn.app.ui.SettingsControls
import com.sawn.app.ui.VerifyPinDialog
import com.sawn.app.ui.dawnBackground
import com.sawn.app.ui.theme.SawnPalette
import com.sawn.app.util.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * «الإعدادات» — ما لا يخص المفاتيح: ملفي · اللغة · الأمان · الأقسام · الخصوصية · عن.
 *
 * مفاتيح الحماية (فلتر الإباحية، SafeSearch، الإعلانات، DoH، الإشعارات، الوضع
 * الصارم والدائم، القفل الزمني) على الصفحة الرئيسية وحدها — لا تُكرَّر هنا.
 */
@Composable
fun SettingsScreen(
    vm: SawnViewModel,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenProtection: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val strictMode by vm.strictMode.collectAsStateWithLifecycle()
    val profileName by vm.profileName.collectAsStateWithLifecycle()
    val profileAge by vm.profileAge.collectAsStateWithLifecycle()
    val sawnLocale by vm.sawnLocale.collectAsStateWithLifecycle()
    val lockdownActive by vm.lockdownActive.collectAsStateWithLifecycle()

    var showWipeConfirm by remember { mutableStateOf(false) }
    var showWipeBlocked by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize().dawnBackground(),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsHeader(
                onBack = onBack,
            )

            // ===== الملف الشخصي =====
            DawnCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(SawnPalette.PrimarySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (profileName?.trim()?.take(1) ?: "؟").uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = SawnPalette.Primary,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            profileName?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.settings_profile),
                            style = MaterialTheme.typography.titleMedium,
                            color = SawnPalette.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            profileAge?.let { stringResource(R.string.settings_profile_age_label) + ": " + it }
                                ?: stringResource(R.string.settings_profile_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = SawnPalette.InkSoft,
                        )
                    }
                    TextButton(onClick = { showEditProfile = true }) {
                        Text(stringResource(R.string.settings_profile_edit), color = SawnPalette.Primary)
                    }
                }
            }

            // ===== اللغة =====
            SectionLabel(stringResource(R.string.settings_locale))
            DawnCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Language,
                        contentDescription = null,
                        tint = SawnPalette.Primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_locale),
                            style = MaterialTheme.typography.titleSmall,
                            color = SawnPalette.Ink,
                        )
                        Text(
                            stringResource(R.string.settings_locale_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = SawnPalette.InkSoft,
                        )
                    }
                    LocaleSegmented(
                        selected = sawnLocale,
                        onSelect = { tag ->
                            vm.setLocale(tag)
                            LocaleUtils.apply(context, tag)
                        },
                    )
                }
            }

            // ===== الأمان =====
            SectionLabel(stringResource(R.string.settings_security_title))
            SettingsControls.PinCard(vm = vm, strict = strictMode, hasPin = hasPin)
            SettingsControls.AdminProtectionCard(strict = strictMode, vm = vm)

            // ===== الأقسام (الحماية والخصوصية) =====
            SectionLink(
                icon = Icons.Filled.Shield,
                title = stringResource(R.string.settings_protection),
                subtitle = stringResource(R.string.settings_protection_subtitle),
                onClick = onOpenProtection,
            )
            SectionLink(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_privacy),
                subtitle = stringResource(R.string.settings_privacy_subtitle),
                onClick = onOpenPrivacy,
            )
            SetupLink(onOpenSetup)

            // ===== الخصوصية الراديكالية =====
            SectionLabel(stringResource(R.string.settings_privacy))
            DestructiveRow(
                icon = Icons.Filled.Warning,
                title = stringResource(R.string.settings_privacy_wipe),
                subtitle = stringResource(
                    if (lockdownActive) R.string.settings_wipe_locked else R.string.settings_privacy_wipe_sub,
                ),
                onClick = {
                    if (lockdownActive) showWipeBlocked = true else showWipeConfirm = true
                },
            )

            // ===== إعادة عرض المقدمة =====
            SettingsLinkRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_reset_onboarding),
                subtitle = stringResource(R.string.settings_reset_onboarding_sub),
                onClick = { vm.resetOnboarding() },
            )

            // ===== عن صَوْن =====
            SectionLabel(stringResource(R.string.settings_about_title))
            AboutCard()

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showWipeBlocked) {
        AlertDialog(
            onDismissRequest = { showWipeBlocked = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            title = { Text(stringResource(R.string.lockdown_active_title)) },
            text = { Text(stringResource(R.string.settings_wipe_locked)) },
            confirmButton = {
                TextButton(onClick = { showWipeBlocked = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showWipeConfirm) {
        if (hasPin) {
            VerifyPinDialog(
                title = stringResource(R.string.settings_privacy_wipe_confirm_title),
                verify = vm::verifyPin,
                onSuccess = {
                    showWipeConfirm = false
                    vm.wipeAllData()
                    (context as? android.app.Activity)?.finishAffinity()
                },
                onDismiss = { showWipeConfirm = false },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showWipeConfirm = false },
                icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = SawnPalette.Error) },
                title = { Text(stringResource(R.string.settings_privacy_wipe_confirm_title)) },
                text = { Text(stringResource(R.string.settings_privacy_wipe_confirm_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showWipeConfirm = false
                            vm.wipeAllData()
                            (context as? android.app.Activity)?.finishAffinity()
                        },
                    ) {
                        Text(stringResource(R.string.settings_privacy_wipe_confirm_action), color = SawnPalette.Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWipeConfirm = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                },
            )
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            initialName = profileName.orEmpty(),
            initialAge = profileAge?.toString().orEmpty(),
            onSave = { name, age -> vm.setProfile(name, age) },
            onDismiss = { showEditProfile = false },
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = SawnPalette.Ink,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = SawnPalette.Ink,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.ExtraBold,
        color = SawnPalette.InkSoft,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun LocaleSegmented(selected: String, onSelect: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SawnPalette.PrimarySoft,
        modifier = Modifier.border(1.dp, SawnPalette.Line, RoundedCornerShape(12.dp)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LocaleOption(stringResource(R.string.settings_locale_system), LocaleUtils.SYSTEM, selected, onSelect)
            LocaleOption(stringResource(R.string.settings_locale_ar), LocaleUtils.ARABIC, selected, onSelect)
            LocaleOption(stringResource(R.string.settings_locale_en), LocaleUtils.ENGLISH, selected, onSelect)
        }
    }
}

@Composable
private fun LocaleOption(label: String, tag: String, selected: String, onSelect: (String) -> Unit) {
    val active = selected == tag
    Surface(
        onClick = { onSelect(tag) },
        shape = RoundedCornerShape(10.dp),
        color = if (active) SawnPalette.Primary else Color.Transparent,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else SawnPalette.InkSoft,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SettingsLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = SawnPalette.Card,
        modifier = Modifier.fillMaxWidth().border(1.dp, SawnPalette.Line, MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = SawnPalette.Primary, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = SawnPalette.Ink)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SawnPalette.InkSoft)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = SawnPalette.InkSoft)
        }
    }
}

@Composable
private fun SectionLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) = SettingsLinkRow(icon = icon, title = title, subtitle = subtitle, onClick = onClick)

@Composable
private fun SetupLink(onOpenSetup: () -> Unit) {
    Surface(
        onClick = onOpenSetup,
        shape = MaterialTheme.shapes.medium,
        color = SawnPalette.PrimarySoft,
        modifier = Modifier.fillMaxWidth().border(1.dp, SawnPalette.Line, MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = SawnPalette.Primary, modifier = Modifier.size(22.dp))
            Text(
                stringResource(R.string.settings_setup),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SawnPalette.PrimaryDeep,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = SawnPalette.PrimaryDeep)
        }
    }
}

@Composable
private fun DestructiveRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = SawnPalette.ErrorSoft,
        modifier = Modifier.fillMaxWidth().border(1.dp, SawnPalette.Error.copy(alpha = 0.35f), MaterialTheme.shapes.medium),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = SawnPalette.Error, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = SawnPalette.OnErrorSoft)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SawnPalette.OnErrorSoft.copy(alpha = 0.8f))
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = SawnPalette.Error)
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    var fingerprint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        fingerprint = withContext(Dispatchers.Default) {
            runCatching {
                val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
                    )
                        .signingInfo
                        ?.signingCertificateHistory
                        ?.firstOrNull()
                        ?.toByteArray()
                        ?: return@runCatching null
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.GET_SIGNATURES,
                    ).signatures?.firstOrNull()?.toByteArray()
                        ?: return@runCatching null
                }
                MessageDigest.getInstance("SHA-256")
                    .digest(info)
                    .joinToString(":") { "%02X".format(it) }
            }.getOrNull()
        }
    }

    DawnCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_about_version), style = MaterialTheme.typography.bodyMedium, color = SawnPalette.InkSoft)
            Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = SawnPalette.Ink)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = SawnPalette.LineSoft)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.settings_about_fingerprint),
                style = MaterialTheme.typography.bodySmall,
                color = SawnPalette.InkSoft,
            )
            Text(
                fingerprint ?: "…",
                style = MaterialTheme.typography.labelSmall,
                color = SawnPalette.Ink,
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = SawnPalette.LineSoft)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.settings_about_contact) + " · " + stringResource(R.string.settings_about_rate) +
                " · " + stringResource(R.string.settings_about_opening_soon),
            style = MaterialTheme.typography.bodySmall,
            color = SawnPalette.Muted,
        )
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialAge: String,
    onSave: (String?, Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var age by remember { mutableStateOf(initialAge) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text(stringResource(R.string.settings_profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = age,
                    onValueChange = { value -> if (value.all(Char::isDigit) && value.length <= 3) age = value },
                    label = { Text(stringResource(R.string.settings_profile_age_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name.trim().takeIf { it.isNotBlank() }, age.toIntOrNull())
                onDismiss()
            }) {
                Text(stringResource(android.R.string.ok), color = SawnPalette.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
