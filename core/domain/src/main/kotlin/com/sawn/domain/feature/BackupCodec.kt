package com.sawn.domain.feature

import java.util.Base64

/**
 * The portable body of a Sawn backup. Pure Kotlin so every restore decision can be
 * unit-tested without Android; the app only supplies the DataStore contents and applies
 * them back after [decode].
 *
 * Backups deliberately exclude the PIN (hash + salt) and device-scoped values (boot
 * count, elapsed-time anchors, high-water marks): restoring them on another phone would
 * either lock the user out or defeat the anti-tamper clock.
 */
data class BackupDocument(
    val profile: BackupProfile,
    val settings: BackupSettings,
    val blockedItems: List<BackupItem>,
    val schedules: List<BackupSchedule>,
    val protectedDays: List<String>,
    val enabledFeatures: List<String>,
    val onboardingAnswers: String?,
)

data class BackupProfile(val name: String?, val age: Int?)
data class BackupItem(
    val type: String,
    val value: String,
    val label: String,
    val enabled: Boolean,
    val interceptMode: String,
    val pauseSeconds: Int,
    val customPrompt: String?,
    val openLimitPerDay: Int,
    val dailyLimitMinutes: Int,
    val cooldownMinutes: Int,
)
data class BackupSchedule(
    val name: String,
    val daysMask: Int,
    val startMinute: Int,
    val endMinute: Int,
    val enabled: Boolean,
)
data class BackupSettings(
    val masterEnabled: Boolean,
    val alwaysOn: Boolean,
    val blockDoh: Boolean,
    val blockAds: Boolean,
    val blockNotifications: Boolean,
    val pornGuard: Boolean,
    val safeSearchForce: Boolean,
    val reduceMotion: Boolean,
    val locale: String,
    val deepFocusEnabled: Boolean,
    val deepFocusWhitelist: List<String>,
    val nightGuardEnabled: Boolean,
    val nightGuardStartMin: Int,
    val nightGuardEndMin: Int,
    val accountabilityName: String?,
    val accountabilityContact: String?,
    val nudgesEnabled: Boolean,
    val socialGuardEnabled: Boolean = false,
    val enabledSocialPlatforms: List<String> = emptyList(),
    // 2.2.0: تفضيلات التحديث التلقائي. تُحفظ في النسخة الاحتياطية حتى لا تعود
    // مفعّلةً بعد الاستعادة إن كان المستخدم قد أوقفها.
    val autoUpdateEnabled: Boolean = true,
    val autoDownloadEnabled: Boolean = true,
    val autoNotifyEnabled: Boolean = true,
)

/** Decoding deliberately has no Android dependency; bad input yields null, never a crash. */
object BackupCodec {

    const val VERSION = 3
    private const val HEADER_V1 = "SAWNBAK1:"
    private const val HEADER_V2 = "SAWNBAK2:"
    private const val HEADER = "SAWNBAK3:"

    fun encode(doc: BackupDocument): String = buildString {
        append(HEADER)
        append(b64(doc.profile.name.orEmpty())).append('\t')
        append(doc.profile.age?.toString().orEmpty()).append('\t')
        append(encodeSettings(doc.settings)).append('\t')
        append(b64(doc.onboardingAnswers.orEmpty())).append('\t')
        append(encodeItems(doc.blockedItems)).append('\t')
        append(encodeSchedules(doc.schedules)).append('\t')
        append(doc.protectedDays.joinToString(",")).append('\t')
        append(doc.enabledFeatures.joinToString(","))
    }

    /** Returns null for unsupported or malformed Sawn backups. */
    fun decode(text: String): BackupDocument? = runCatching {
        val header = when {
            text.startsWith(HEADER) -> HEADER
            text.startsWith(HEADER_V2) -> HEADER_V2
            text.startsWith(HEADER_V1) -> HEADER_V1
            else -> error("unsupported backup version")
        }
        val settingsVersion = when (header) {
            HEADER -> 3
            HEADER_V2 -> 2
            else -> 1
        }
        val parts = text.removePrefix(header).split('\t')
        require(parts.size >= 8) { "truncated" }
        BackupDocument(
            profile = BackupProfile(
                name = parts[0].unb64()?.ifBlank { null },
                age = parts[1].toIntOrNull(),
            ),
            settings = decodeSettings(parts[2], settingsVersion),
            // Must mirror [encode] exactly: name, age, settings, answers, items, schedules, days, features.
            onboardingAnswers = parts[3].unb64()?.ifBlank { null },
            blockedItems = decodeItems(parts[4]),
            schedules = decodeSchedules(parts[5]),
            protectedDays = parts[6].splitList(),
            enabledFeatures = parts[7].splitList(),
        )
    }.getOrNull()

    private fun String.splitList(): List<String> =
        if (isBlank()) emptyList() else split(',').filter { it.isNotBlank() }

    // Values contain tabs/newlines only in pathological cases; base64 keeps fields opaque.
    private fun encodeItems(items: List<BackupItem>): String = items.joinToString("\n") { i ->
        listOf(
            i.type, i.value, i.label, i.enabled.toString(), i.interceptMode,
            i.pauseSeconds.toString(), i.customPrompt.orEmpty(), i.openLimitPerDay.toString(),
            i.dailyLimitMinutes.toString(), i.cooldownMinutes.toString(),
        ).joinToString("|") { b64(it) }
    }

    private fun decodeItems(raw: String): List<BackupItem> = raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val f = line.split('|')
        if (f.size != 10) return@mapNotNull null
        // Every field on the wire is base64 — decoding the numeric ones as plain text silently
        // reset limits to their defaults, which made a restore look successful but lose data.
        fun field(index: Int): String? = f[index].unb64()
        val mode = field(4) ?: "BLOCK"
        BackupItem(
            type = field(0) ?: return@mapNotNull null,
            value = field(1) ?: return@mapNotNull null,
            label = field(2) ?: return@mapNotNull null,
            enabled = field(3)?.toBooleanStrictOrNull() ?: true,
            interceptMode = if (mode == "PAUSE" || mode == "BLOCK") mode else "BLOCK",
            pauseSeconds = field(5)?.toIntOrNull() ?: 15,
            customPrompt = field(6)?.ifBlank { null },
            openLimitPerDay = field(7)?.toIntOrNull() ?: 0,
            dailyLimitMinutes = field(8)?.toIntOrNull() ?: 0,
            cooldownMinutes = field(9)?.toIntOrNull() ?: 0,
        )
    }

    private fun encodeSchedules(items: List<BackupSchedule>): String = items.joinToString("\n") { s ->
        listOf(s.name, s.daysMask.toString(), s.startMinute.toString(), s.endMinute.toString(), s.enabled.toString())
            .joinToString("|") { b64(it) }
    }

    private fun decodeSchedules(raw: String): List<BackupSchedule> = raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val f = line.split('|')
        if (f.size != 5) return@mapNotNull null
        BackupSchedule(f[0].unb64() ?: return@mapNotNull null, f[1].unb64()?.toIntOrNull() ?: return@mapNotNull null,
            f[2].unb64()?.toIntOrNull() ?: return@mapNotNull null, f[3].unb64()?.toIntOrNull() ?: return@mapNotNull null,
            f[4].unb64()?.toBooleanStrictOrNull() ?: false)
    }

    private fun b64(v: String): String = Base64.getEncoder().encodeToString(v.toByteArray(Charsets.UTF_8))
    private fun String.unb64(): String? = runCatching { String(Base64.getDecoder().decode(this), Charsets.UTF_8) }.getOrNull()
}

/** Compact comma-separated settings block; unknown future keys are ignored on decode. */
private fun encodeSettings(s: BackupSettings): String = listOf(
    s.masterEnabled, s.alwaysOn, s.blockDoh, s.blockAds, s.blockNotifications, s.pornGuard, s.safeSearchForce,
    s.reduceMotion, s.locale, s.deepFocusEnabled, s.deepFocusWhitelist.joinToString("^"),
    s.nightGuardEnabled, s.nightGuardStartMin, s.nightGuardEndMin,
    s.accountabilityName.orEmpty(), s.accountabilityContact.orEmpty(), s.nudgesEnabled,
    s.socialGuardEnabled, s.enabledSocialPlatforms.joinToString("^"),
    s.autoUpdateEnabled, s.autoDownloadEnabled, s.autoNotifyEnabled,
).joinToString(",") {
    it.toString().replace(",", "\u0001")
}

private fun decodeSettings(raw: String, version: Int): BackupSettings {
    val f = raw.split(',').map { it.replace("\u0001", ",") }
    fun s(i: Int, d: String) = f.getOrNull(i)?.ifBlank { null } ?: d
    fun b(i: Int, d: Boolean) = f.getOrNull(i)?.toBooleanStrictOrNull() ?: d
    fun n(i: Int, d: Int) = f.getOrNull(i)?.toIntOrNull() ?: d
    return BackupSettings(
        masterEnabled = b(0, false),
        alwaysOn = b(1, true),
        blockDoh = b(2, false),
        blockAds = b(3, true),
        blockNotifications = b(4, false),
        pornGuard = b(5, true),
        safeSearchForce = b(6, true),
        reduceMotion = b(7, false),
        locale = s(8, ""),
        deepFocusEnabled = b(9, false),
        deepFocusWhitelist = s(10, "").split("^").filter { it.isNotBlank() },
        nightGuardEnabled = b(11, false),
        nightGuardStartMin = n(12, 1320),
        nightGuardEndMin = n(13, 360),
        accountabilityName = f.getOrNull(14)?.ifBlank { null },
        accountabilityContact = f.getOrNull(15)?.ifBlank { null },
        nudgesEnabled = b(16, false),
        socialGuardEnabled = version >= 2 && b(17, false),
        enabledSocialPlatforms = if (version < 2) emptyList() else s(18, "").split("^").filter { it.isNotBlank() },
        autoUpdateEnabled = if (version < 3) true else b(19, true),
        autoDownloadEnabled = if (version < 3) true else b(20, true),
        autoNotifyEnabled = if (version < 3) true else b(21, true),
    )
}
