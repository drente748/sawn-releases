package com.sawn.domain.block.web

/**
 * Per-browser address-bar configuration, rebuilt from a decompiled `BrowserConfig`
 * constant from a mature blocker. Each entry maps a
 * browser package to the view-id(s) of its URL bar (tried in
 * order — e.g. Chrome exposes both "origin" and "url_bar"), plus whether a
 * raw tree traversal may be used as a fallback for that browser (needed for
 * some Firefox-family builds where the toolbar node is not directly
 * addressable).
 *
 * View ids here are resource suffixes; the real lookup is
 * "<package>:id/<id>", which is what AccessibilityNodeInfo expects.
 */
data class BrowserConfig(
    val packageName: String,
    val urlViewIds: List<String>,
    val fallbackTraversal: Boolean,
) {
    /** Fully-qualified ids, ready for findAccessibilityNodeInfosByViewId. */
    val qualifiedViewIds: List<String> =
        urlViewIds.map { "$packageName:id/$it" }

    val viewIdSet: Set<String> = urlViewIds.toSet()

    companion object {

        val ALL: Map<String, BrowserConfig> = listOf(
        BrowserConfig("com.nationaledtech.spinbrowser", listOf("mozac_browser_toolbar_url_view"), fallbackTraversal = true),
        BrowserConfig("alook.browser", listOf("search_fragment_input_view"), fallbackTraversal = false),
        BrowserConfig("alook.browser.google", listOf("search_fragment_input_view"), fallbackTraversal = false),
        BrowserConfig("app.vanadium.browser", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.amazon.cloud9", listOf("url"), fallbackTraversal = false),
        BrowserConfig("com.android.browser", listOf("url"), fallbackTraversal = false),
        BrowserConfig("com.android.chrome", listOf("origin", "url_bar"), fallbackTraversal = false),
        BrowserConfig("com.avast.android.secure.browser", listOf("editor"), fallbackTraversal = false),
        BrowserConfig("com.avg.android.secure.browser", listOf("editor"), fallbackTraversal = false),
        BrowserConfig("com.brave.browser", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.brave.browser_beta", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.brave.browser_default", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.brave.browser_dev", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.brave.browser_nightly", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.chrome.beta", listOf("origin", "url_bar"), fallbackTraversal = false),
        BrowserConfig("com.chrome.canary", listOf("origin", "url_bar"), fallbackTraversal = false),
        BrowserConfig("com.chrome.dev", listOf("origin", "url_bar"), fallbackTraversal = false),
        BrowserConfig("com.cookiegames.smartcookie", listOf("search"), fallbackTraversal = false),
        BrowserConfig("com.cookiejarapps.android.smartcookieweb", listOf("mozac_browser_toolbar_url_view"), fallbackTraversal = true),
        BrowserConfig("com.duckduckgo.mobile.android", listOf("omnibarTextInput"), fallbackTraversal = false),
        BrowserConfig("com.ecosia.android", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.google.android.apps.chrome", listOf("origin", "url_bar"), fallbackTraversal = false),
        BrowserConfig("com.google.android.apps.chrome_dev", listOf("origin", "url_bar"), fallbackTraversal = false),
        BrowserConfig("com.iode.firefox", listOf("mozac_browser_toolbar_url_view"), fallbackTraversal = true),
        BrowserConfig("com.jamal2367.styx", listOf("search"), fallbackTraversal = false),
        BrowserConfig("com.kiwibrowser.browser", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.kiwibrowser.browser.dev", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.ktllq.play", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.microsoft.emmx", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.microsoft.emmx.beta", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.microsoft.emmx.canary", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.microsoft.emmx.dev", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.mmbox.browser", listOf("search_box"), fallbackTraversal = false),
        BrowserConfig("com.mmbox.xbrowser", listOf("search_box"), fallbackTraversal = false),
        BrowserConfig("com.mycompany.app.soulbrowser", listOf("edit_text"), fallbackTraversal = false),
        BrowserConfig("com.naver.whale", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.neeva.app", listOf("full_url_text_view"), fallbackTraversal = false),
        BrowserConfig("com.opera.browser", listOf("url_field"), fallbackTraversal = false),
        BrowserConfig("com.opera.browser.beta", listOf("url_field"), fallbackTraversal = false),
        BrowserConfig("com.opera.gx", listOf("addressbarEdit"), fallbackTraversal = false),
        BrowserConfig("com.opera.mini.native", listOf("url_field"), fallbackTraversal = false),
        BrowserConfig("com.opera.mini.native.beta", listOf("url_field"), fallbackTraversal = false),
        BrowserConfig("com.opera.touch", listOf("addressbarEdit"), fallbackTraversal = false),
        BrowserConfig("com.qflair.browserq", listOf("url"), fallbackTraversal = false),
        BrowserConfig("com.qwant.liberty", listOf("mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("com.rainsee.create", listOf("search_box"), fallbackTraversal = false),
        BrowserConfig("com.sec.android.app.sbrowser", listOf("location_bar_edit_text"), fallbackTraversal = false),
        BrowserConfig("com.sec.android.app.sbrowser.beta", listOf("location_bar_edit_text"), fallbackTraversal = false),
        BrowserConfig("com.stoutner.privacybrowser.free", listOf("url_edittext"), fallbackTraversal = false),
        BrowserConfig("com.stoutner.privacybrowser.standard", listOf("url_edittext"), fallbackTraversal = false),
        BrowserConfig("com.vivaldi.browser", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.vivaldi.browser.snapshot", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.vivaldi.browser.sopranos", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.yandex.browser", listOf("bro_omnibar_address_title_text", "bro_omnibox_collapsed_title"), fallbackTraversal = false),
        BrowserConfig("com.yjllq.chrome.beta", listOf("search_box"), fallbackTraversal = false),
        BrowserConfig("com.yjllq.internet", listOf("search_box"), fallbackTraversal = false),
        BrowserConfig("com.yjllq.kito", listOf("search_box"), fallbackTraversal = false),
        BrowserConfig("com.yjllqint.kito", listOf("search_box"), fallbackTraversal = false),
        BrowserConfig("com.yujian.ResideMenuDemo", listOf("search_box"), fallbackTraversal = false),
        BrowserConfig("com.z28j.feel", listOf("g2"), fallbackTraversal = false),
        BrowserConfig("idm.internet.download.manager", listOf("search"), fallbackTraversal = false),
        BrowserConfig("idm.internet.download.manager.adm.lite", listOf("search"), fallbackTraversal = false),
        BrowserConfig("idm.internet.download.manager.plus", listOf("search"), fallbackTraversal = false),
        BrowserConfig("io.github.forkmaintainers.iceraven", listOf("mozac_browser_toolbar_url_view"), fallbackTraversal = true),
        BrowserConfig("mark.via", listOf("am", "an"), fallbackTraversal = false),
        BrowserConfig("mark.via.gp", listOf("as"), fallbackTraversal = false),
        BrowserConfig("net.dezor.browser", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("net.slions.fulguris.full.download", listOf("search"), fallbackTraversal = false),
        BrowserConfig("net.slions.fulguris.full.download.debug", listOf("search"), fallbackTraversal = false),
        BrowserConfig("net.slions.fulguris.full.playstore", listOf("search"), fallbackTraversal = false),
        BrowserConfig("net.slions.fulguris.full.playstore.debug", listOf("search"), fallbackTraversal = false),
        BrowserConfig("org.adblockplus.browser", listOf("url_bar", "url_bar_title"), fallbackTraversal = false),
        BrowserConfig("org.adblockplus.browser.beta", listOf("url_bar", "url_bar_title"), fallbackTraversal = false),
        BrowserConfig("org.bromite.bromite", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("org.bromite.chromium", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("org.chromium.chrome", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("org.codeaurora.swe.browser", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("org.cromite.cromite", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("org.gnu.icecat", listOf("url_bar_title", "mozac_browser_toolbar_url_view"), fallbackTraversal = true),
        BrowserConfig("org.ironfoxoss.ironfox", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("org.ironfoxoss.ironfox.nightly", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.fenix", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.fenix.nightly", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.fennec_aurora", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.fennec_fdroid", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.firefox", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.firefox_beta", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.focus", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "display_url"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.focus.beta", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "display_url"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.focus.nightly", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "display_url"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.klar", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "display_url"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.reference.browser", listOf("mozac_browser_toolbar_url_view"), fallbackTraversal = true),
        BrowserConfig("org.mozilla.rocket", listOf("display_url"), fallbackTraversal = false),
        BrowserConfig("org.torproject.torbrowser", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("org.torproject.torbrowser_alpha", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("org.ungoogled.chromium.extensions.stable", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("org.ungoogled.chromium.stable", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("us.spotco.fennec_dos", listOf("ADDRESSBAR_URL_BOX", "mozac_browser_toolbar_url_view", "url_bar_title"), fallbackTraversal = true),
        BrowserConfig("acr.browser.barebones", listOf("search"), fallbackTraversal = false),
        BrowserConfig("acr.browser.lightning", listOf("search"), fallbackTraversal = false),
        BrowserConfig("com.feedback.browser.wjbrowser", listOf("addressbar_url"), fallbackTraversal = false),
        BrowserConfig("com.ghostery.android.ghostery", listOf("search_field"), fallbackTraversal = false),
        BrowserConfig("com.htc.sense.browser", listOf("title"), fallbackTraversal = false),
        BrowserConfig("com.jerky.browser2", listOf("enterUrl"), fallbackTraversal = false),
        BrowserConfig("com.ksmobile.cb", listOf("address_bar_edit_text"), fallbackTraversal = false),
        BrowserConfig("com.lemurbrowser.exts", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("com.linkbubble.playstore", listOf("url_text"), fallbackTraversal = false),
        BrowserConfig("com.mx.browser", listOf("address_editor_with_progress"), fallbackTraversal = false),
        BrowserConfig("com.mx.browser.tablet", listOf("address_editor_with_progress"), fallbackTraversal = false),
        BrowserConfig("com.nubelacorp.javelin", listOf("enterUrl"), fallbackTraversal = false),
        BrowserConfig("com.zoho.primeum.stable", listOf("url_bar"), fallbackTraversal = false),
        BrowserConfig("jp.co.fenrir.android.sleipnir", listOf("url_text"), fallbackTraversal = false),
        BrowserConfig("jp.co.fenrir.android.sleipnir_black", listOf("url_text"), fallbackTraversal = false),
        BrowserConfig("jp.co.fenrir.android.sleipnir_test", listOf("url_text"), fallbackTraversal = false),
        BrowserConfig("mobi.mgeek.TunnyBrowser", listOf("title"), fallbackTraversal = false),
        BrowserConfig("org.iron.srware", listOf("url_bar"), fallbackTraversal = false),
        ).associateBy { it.packageName }

        fun byPackage(packageName: String): BrowserConfig? = ALL[packageName]

        val size: Int get() = ALL.size
    }
}
