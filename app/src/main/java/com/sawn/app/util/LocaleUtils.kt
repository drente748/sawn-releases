package com.sawn.app.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** تطبيق لغة التطبيق عبر AppCompat per-app language API (API 26+). */
object LocaleUtils {

    const val SYSTEM = ""
    const val ARABIC = "ar"
    const val ENGLISH = "en"

    /** يحوّل وسم اللغة المحفوظ إلى LocaleListCompat ويطبّقه. */
    fun apply(context: Context, tag: String) {
        val list = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(list)
    }

    /** اللغة المختارة حاليًا (فارغة = تابع للنظام). */
    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) SYSTEM else locales[0]?.language.orEmpty()
    }
}
