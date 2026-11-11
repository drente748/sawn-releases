package com.sawn.domain.block

/**
 * Pure domain/URL matching, shared by the VPN filter, the browser URL monitor, and tests.
 *
 * [normalizeUrl] turns an address-bar text like "https://www.Example.com/x?q=1" into a bare
 * lowercase host ("example.com"), collapsing the forms users actually type. It deliberately
 * rejects anything that does not look like a host (spaces = a search query, empty text).
 */
object DomainMatcher {

    /** Strips scheme, path, userinfo, port and www. from address-bar text. Null when the
     *  text is blank, has spaces (a search term, not a URL), or reduces to nothing. */
    fun normalizeUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        var t = text.trim().lowercase()
        if (t.contains(' ')) return null
        t = t.removePrefix("https://").removePrefix("http://")
        var host = t.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.contains('@')) host = host.substringAfterLast('@') // strip userinfo
        host = host.substringBefore(':').trimEnd('.') // port (v4/hostname); keeps [v6] unusable (rare in bars)
        host = host.removePrefix("www.")
        if (host.isBlank() || host == "about:blank") return null
        return host
    }

    /** True when [host] equals or is a subdomain of any entry in [domains]. */
    fun matches(host: String?, domains: Set<String>): Boolean {
        var candidate = host?.trim()?.lowercase()?.trimEnd('.') ?: return false
        if (candidate.isBlank() || domains.isEmpty()) return false
        while (true) {
            if (candidate in domains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    fun isValidDomain(domain: String?): Boolean {
        val host = domain?.trim()?.lowercase()?.trimEnd('.') ?: return false
        if (host.length > 253 || !host.contains('.') || host.all { it.isDigit() || it == '.' }) {
            return false
        }
        return host.split('.').all { label ->
            label.length in 1..63 &&
                !label.startsWith('-') && !label.endsWith('-') &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
        }
    }
}
