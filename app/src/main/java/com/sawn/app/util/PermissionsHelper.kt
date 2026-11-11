package com.sawn.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.sawn.app.accessibility.AppBlockAccessibilityService
import com.sawn.app.usage.UsageStatsHelper

/** Central checks for the special-access permissions Sawn relies on. */
object PermissionsHelper {

    /**
     * Accessibility is enabled only when the system master switch is on **and** our
     * component is in the enabled list.
     *
     * The old check compared one exact component string. Some OEMs (Xiaomi, ColorOS,
     * One UI) store the short form (`pkg/.accessibility.Service`) or re-case it, so a
     * working service was reported as missing and the app kept telling the user to
     * grant a permission they had already granted. Match the class suffix instead.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        val masterOn = runCatching {
            Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED) == 1
        }.getOrDefault(false)
        if (!masterOn) return false

        val enabled = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val pkg = context.packageName
        return enabled.split(':').any { raw ->
            val entry = raw.trim()
            if (entry.isEmpty()) return@any false
            val entryPkg = entry.substringBefore('/', "")
            val entryClass = entry.substringAfter('/', "")
            entryPkg.equals(pkg, ignoreCase = true) &&
                entryClass.endsWith(
                    AppBlockAccessibilityService::class.java.simpleName,
                    ignoreCase = true,
                )
        }
    }

    fun hasUsageAccess(context: Context): Boolean = UsageStatsHelper.hasPermission(context)

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Accessibility is the one truly essential grant (app blocking + in-app feeds). */
    fun essentialsGranted(context: Context): Boolean = isAccessibilityEnabled(context)

    /** True once the user has approved the system VPN dialog (no prompt pending). */
    fun hasVpnConsent(context: Context): Boolean = runCatching {
        VpnService.prepare(context) == null
    }.getOrDefault(false)

    /**
     * إذن الاتصال وحده لا يكفي: الخدمة تحتاج أيضاً إشعاراً مسموحاً كي يظهر «الحماية
     * تعمل». نجمع الاثنين في فحصٍ واحد تفهمه شاشة الفحص.
     */
    fun filterReady(context: Context): Boolean = hasVpnConsent(context) && hasNotifications(context)

    /** Not "granted" by us — the user picks Sawn → No restrictions in system settings. */
    fun isBatteryOptimizationIgnored(context: Context): Boolean =
        runCatching {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

    /** Always-on VPN is configured for Sawn, so the OS re-arms the filter after reboots. */
    fun isAlwaysOnVpnConfigured(context: Context): Boolean = runCatching {
        Settings.Secure.getString(context.contentResolver, "always_on_vpn_app") == context.packageName
    }.getOrDefault(false)

    /** Notification access is what lets Sawn silence notifications from blocked apps. */
    fun isNotificationListenerEnabled(context: Context): Boolean = runCatching {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        enabled.split(':').any { it.contains(context.packageName, ignoreCase = true) }
    }.getOrDefault(false)

    /**
     * Strict Private DNS sends queries over DoT straight to the configured resolver, so the
     * Sawn tunnel never sees them — the DNS filter is silently bypassed (API 28+). Even
     * "Automatic" may use DoT on networks/carriers that advertise it.
     */
    fun isPrivateDnsStrict(context: Context): Boolean = runCatching {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Settings.Global.getString(context.contentResolver, "private_dns_mode") == "hostname"
    }.getOrDefault(false)

    /**
     * Non-Play installs on Android 13+ need a one-time "Allow restricted settings" from the
     * App Info menu before Accessibility can be switched on.
     */
    fun isSideloaded(context: Context): Boolean = runCatching {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer != "com.android.vending"
    }.getOrDefault(false)

    /**
     * يطلب استثناء البطارية مباشرةً (نافذة نظام واحدة) بدل إرسال المستخدم إلى قائمةٍ
     * عليه أن يبحث فيها عن التطبيق. يعود إلى القائمة العامة إن لم يكن الطلب المباشر
     * مدعوماً على هذه الشركة — بعض واجهات OEM تحجبه.
     */
    fun requestBatteryExemption(context: Context): Boolean {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct) }.isSuccess) return true
        openBatterySettings(context)
        return false
    }

    /**
     * Opens the battery screen that actually exists on this device. The generic list is
     * unhelpful on Chinese OEMs, whose aggressive killers hide their per-app exemption
     * behind vendor activities; try those first, then fall back safely.
     */
    fun openBatterySettings(context: Context) {
        val label = runCatching {
            context.packageManager.getApplicationLabel(context.applicationInfo).toString()
        }.getOrDefault(context.packageName)

        val vendorTargets: List<Pair<ComponentName, Array<Pair<String, String>>?>> = listOf(
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            ) to arrayOf("package_name" to context.packageName, "package_label" to label),
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity",
            ) to null,
            ComponentName(
                "com.coloros.oppoguardelf",
                "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity",
            ) to null,
            ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ) to null,
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ) to null,
        )

        for ((component, extras) in vendorTargets) {
            val intent = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            extras?.forEach { (key, value) -> intent.putExtra(key, value) }
            val resolvable = runCatching {
                context.packageManager.resolveActivity(intent, 0) != null
            }.getOrDefault(false)
            if (!resolvable) continue
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }

        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
