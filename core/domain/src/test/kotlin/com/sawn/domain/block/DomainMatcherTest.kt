package com.sawn.domain.block

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {

    @Test
    fun normalizeUrl_stripsSchemeWwwPath() {
        assertEquals("example.com", DomainMatcher.normalizeUrl("https://www.Example.com/x?q=1"))
        assertEquals("example.com", DomainMatcher.normalizeUrl("http://example.com/"))
        assertEquals("example.com", DomainMatcher.normalizeUrl("example.com/page#frag"))
    }

    @Test
    fun normalizeUrl_handlesUserInfoAndPort() {
        assertEquals("example.com", DomainMatcher.normalizeUrl("https://user:pass@example.com:8080/x"))
    }

    @Test
    fun normalizeUrl_rejectsSearchQueriesAndBlank() {
        assertNull(DomainMatcher.normalizeUrl("two words"))
        assertNull(DomainMatcher.normalizeUrl(""))
        assertNull(DomainMatcher.normalizeUrl(null))
        assertNull(DomainMatcher.normalizeUrl("  "))
    }

    @Test
    fun matches_suffixMatchesSubdomains() {
        val domains = setOf("pornhub.com")
        assertTrue(DomainMatcher.matches("pornhub.com", domains))
        assertTrue(DomainMatcher.matches("www.pornhub.com", domains))
        assertTrue(DomainMatcher.matches("api.pornhub.com", domains))
        assertFalse(DomainMatcher.matches("notpornhub.com", domains))
        assertFalse(DomainMatcher.matches("pornhub.com.evil.net", domains))
        assertFalse(DomainMatcher.matches(null, domains))
        assertFalse(DomainMatcher.matches("", domains))
    }

    @Test
    fun matches_normalizesHostAndChecksParents() {
        val domains = setOf("example.com", "ads.trackers.net")
        assertTrue(DomainMatcher.matches("WWW.Example.COM.", domains))
        assertTrue(DomainMatcher.matches("a.b.example.com", domains))
        assertTrue(DomainMatcher.matches("x.ads.trackers.net", domains))
        assertFalse(DomainMatcher.matches("example.net", domains))
    }

    @Test
    fun domainValidation_acceptsNumericLabelsAndRejectsMalformedHosts() {
        assertTrue(DomainMatcher.isValidDomain("3letter.example"))
        assertTrue(DomainMatcher.isValidDomain("123.example.com"))
        assertTrue(DomainMatcher.isValidDomain("xn--example-9za.com"))
        assertFalse(DomainMatcher.isValidDomain("127.0.0.1"))
        assertFalse(DomainMatcher.isValidDomain("localhost"))
        assertFalse(DomainMatcher.isValidDomain("bad_domain.com"))
        assertFalse(DomainMatcher.isValidDomain("-bad.example.com"))
        assertFalse(DomainMatcher.isValidDomain("bad..example.com"))
        assertFalse(DomainMatcher.isValidDomain("example.com/path"))
    }

    @Test
    fun adList_containsOnlyValidNormalizedDomains() {
        assertTrue(AdHosts.ALL.isNotEmpty())
        AdHosts.ALL.forEach { domain ->
            assertTrue(domain, DomainMatcher.isValidDomain(domain))
            assertEquals(domain, domain.lowercase())
        }
    }

    @Test
    fun adultCoreList_hasNoBlankOrCorruptedEntries() {
        assertTrue(AdultHosts.ALL.isNotEmpty())
        AdultHosts.ALL.forEach { d ->
            assertTrue("entry contains space: $d", d == d.trim() && !d.contains(' '))
            assertTrue("entry not lowercase: $d", d == d.lowercase())
            assertTrue("entry has no dot (TLD): $d", d.contains('.'))
            assertFalse("entry starts with dot: $d", d.startsWith("."))
        }
    }

    @Test
    fun safeSearch_knownHostsMap() {
        assertEquals("forcesafesearch.google.com", SafeSearchHosts.safeHostFor("www.google.com"))
        assertEquals("restrict.youtube.com", SafeSearchHosts.safeHostFor("m.youtube.com"))
        assertEquals("strict.bing.com", SafeSearchHosts.safeHostFor("bing.com"))
        assertNull(SafeSearchHosts.safeHostFor("example.com"))
    }
}
