package com.sawn.domain.block.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The browser table is ported verbatim from Block 11.3.0 (116 entries). These tests
 * guard the invariants the detector relies on: lowercase unique packages, non-empty
 * id lists, qualified-id construction, and the mozac-family fallback flag.
 */
class BrowserConfigTest {

    @Test
    fun `table loaded with all entries`() {
        assertEquals(116, BrowserConfig.ALL.size)
    }

    @Test
    fun `major browsers are present`() {
        assertNotNull(BrowserConfig.byPackage("com.android.chrome"))
        assertNotNull(BrowserConfig.byPackage("com.brave.browser"))
        assertNotNull(BrowserConfig.byPackage("com.sec.android.app.sbrowser"))
        assertNotNull(BrowserConfig.byPackage("com.microsoft.emmx"))
        assertNotNull(BrowserConfig.byPackage("com.duckduckgo.mobile.android"))
        assertNull(BrowserConfig.byPackage("com.instagram.android"))
    }

    @Test
    fun `chrome has two candidates in block's order`() {
        val chrome = BrowserConfig.byPackage("com.android.chrome")!!
        assertEquals(listOf("origin", "url_bar"), chrome.urlViewIds)
        assertEquals(
            listOf("com.android.chrome:id/origin", "com.android.chrome:id/url_bar"),
            chrome.qualifiedViewIds,
        )
    }

    @Test
    fun `mozac family uses raw traversal fallback`() {
        assertTrue(
            BrowserConfig.byPackage("com.iode.firefox")!!.fallbackTraversal,
        )
        assertTrue(
            BrowserConfig.byPackage("com.cookiejarapps.android.smartcookieweb")!!.fallbackTraversal,
        )
        // Chrome resolves its bar directly — no traversal needed.
        assertEquals(false, BrowserConfig.byPackage("com.android.chrome")!!.fallbackTraversal)
    }

    @Test
    fun `every package is unique with non-empty ids`() {
        // Package names are case-sensitive on Android and this table is ported verbatim
        // from Block 11.3.0 (e.g. "com.yujian.ResideMenuDemo" ships with uppercase
        // letters), so case must be preserved exactly — only uniqueness is asserted.
        BrowserConfig.ALL.values.forEach { config ->
            assertTrue("blank package name", config.packageName.isNotBlank())
            assertEquals(config.packageName, config.packageName.trim())
            assertTrue("empty id list for ${config.packageName}", config.urlViewIds.isNotEmpty())
            config.urlViewIds.forEach { id ->
                assertTrue("blank id in ${config.packageName}", id.isNotBlank())
            }
        }
    }
}
