package com.sawn.domain.block

/**
 * DNS rewrites for "safe" variants of search engines and video platforms. When Porn Guard
 * is on, a query for these hosts is answered with the safe-mode host's address instead of
 * the real one, forcing the provider's own restricted mode (the same trick parental DNS
 * services like CleanBrowsing use).
 *
 * We implement it the DNS way: map each unsafe origin host to the *IP answer* the safe
 * host would return — done by re-pointing the question to the safe host's canonical name
 * is out of scope for a sinkhole, so instead we **forward the query to the safe host**
 * by answering A-queries with a fixed safe-host IPv4 we resolve once at runtime.
 */
object SafeSearchHosts {

    /**
     * Origin hosts that should be re-pointed and the canonical safe-mode hostname we
     * resolve to enforce it. `forcesafesearch.google.com` is Google's official
     * strict-SafeSearch CNAME anchor; Bing/DuckDuckGo have their own.
     */
    val MAPPINGS: Map<String, String> = mapOf(
        "www.google.com" to "forcesafesearch.google.com",
        "google.com" to "forcesafesearch.google.com",
        "www.youtube.com" to "restrict.youtube.com",        // strict mode
        "youtube.com" to "restrict.youtube.com",
        "m.youtube.com" to "restrict.youtube.com",
        "youtubei.googleapis.com" to "restrict.youtube.com",
        "www.bing.com" to "strict.bing.com",
        "bing.com" to "strict.bing.com",
        "duckduckgo.com" to "safe.duckduckgo.com",
        "www.duckduckgo.com" to "safe.duckduckgo.com",
        "search.brave.com" to "safe-search.brave.com",
    )

    /** Yahoo's safesearch is a path, not a host — its host is unmapped. */
    fun safeHostFor(domain: String): String? = MAPPINGS[domain]
}
