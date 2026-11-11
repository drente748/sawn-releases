package com.sawn.domain.block.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WebAddressPattern was rebuilt from a decompiled constant and proven to compile
 * with java.util.regex. These tests pin the observable behavior the extractor relies on:
 * whole-string URL matching that accepts full URLs, bare domains, punycode, IDN and
 * IPv4 — and rejects prose.
 */
class WebAddressPatternTest {

    @Test
    fun `full urls match`() {
        assertTrue(WebAddressPattern.matches("https://www.pornhub.com/x?q=1"))
        assertTrue(WebAddressPattern.matches("http://xnxx.com"))
    }

    @Test
    fun `bare domains match`() {
        assertTrue(WebAddressPattern.matches("pornhub.com"))
        assertTrue(WebAddressPattern.matches("www.example.co.uk:8080/path?x=1"))
    }

    @Test
    fun `punycode and arabic idn match`() {
        assertTrue(WebAddressPattern.matches("xn--frgbwaddaabacc6ce7udc0e5a.xn--p1ai"))
        assertTrue(WebAddressPattern.matches("موقع.كوم"))
    }

    @Test
    fun `ipv4 literals match`() {
        assertTrue(WebAddressPattern.matches("10.0.0.1"))
    }

    @Test
    fun `plain text is not a url`() {
        assertFalse(WebAddressPattern.matches("hello world"))
        assertFalse(WebAddressPattern.matches("just some text"))
        assertFalse(WebAddressPattern.matches("search query about cats"))
        assertFalse(WebAddressPattern.matches(""))
    }
}

class WebUrlExtractorTest {

    @Test
    fun `bar text is accepted when it is a url`() {
        assertEquals(
            "https://www.pornhub.com/x?q=1",
            WebUrlExtractor.extract("https://www.pornhub.com/x?q=1", null, isFocused = false),
        )
        assertEquals("pornhub.com", WebUrlExtractor.extract("pornhub.com", null, isFocused = false))
    }

    @Test
    fun `search query in the bar is not a url`() {
        assertNull(WebUrlExtractor.extract("porn websites free", null, isFocused = false))
        assertNull(WebUrlExtractor.extract("hello world", null, isFocused = false))
    }

    @Test
    fun `focused bar means typing and is never read`() {
        assertNull(
            WebUrlExtractor.extract("https://www.pornhub.com", null, isFocused = true),
        )
        assertNull(WebUrlExtractor.extract("pornhub", null, isFocused = true))
    }

    @Test
    fun `content description is scanned for urls`() {
        assertEquals(
            "https://www.pornhub.com/view_video.php",
            WebUrlExtractor.extract(
                null,
                "https://www.pornhub.com/view_video.php. Visit site",
                isFocused = false,
            ),
        )
    }

    @Test
    fun `content description bare domain fallback`() {
        assertEquals(
            "pornhub.com",
            WebUrlExtractor.extract(null, "pornhub.com", isFocused = false),
        )
    }

    @Test
    fun `prose in content description never matches`() {
        assertNull(WebUrlExtractor.extract(null, "Search or type URL", isFocused = false))
    }

    @Test
    fun `blank input yields nothing`() {
        assertNull(WebUrlExtractor.extract(null, null, isFocused = false))
        assertNull(WebUrlExtractor.extract("   ", "   ", isFocused = false))
    }
}
