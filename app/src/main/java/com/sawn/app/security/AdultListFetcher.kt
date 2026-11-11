package com.sawn.app.security

import android.content.Context
import com.sawn.domain.block.DomainMatcher
import com.sawn.app.sawnContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Refreshes the adult-content domain list from open, maintained blocklist sources.
 *
 * Design notes:
 * - Opt-in and strictly additive: fetched domains are merged INTO [AdultHosts.ALL]'s
 *   runtime view; they never remove entries.
 * - The raw source is hashed and compared to the last-seen hash so re-downloading the
 *   same content is skipped (saves battery + data).
 * - Requests contain no app identifiers or browsing data. The downloaded set is kept in
 *   private app storage and merged only after every source returns valid content.
 * - A stale list is better than no list: on any failure the previous merged set stays.
 */
class AdultListFetcher(private val context: Context) {

    data class FetchResult(val added: Int, val skippedSameContent: Boolean, val error: String? = null)

    /** Hosts-format sources, mirrored from actively maintained open blocklists. */
    private val SOURCES = listOf(
        // PornAway's consolidated adult list
        "https://raw.githubusercontent.com/mhxion/pornaway/master/hosts/porn_hosts",
        // StevenBlack unified hosts: porn extension
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
    )

    suspend fun refreshIfDue(force: Boolean = false): FetchResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("adult_lists", Context.MODE_PRIVATE)
        val lastMillis = prefs.getLong(KEY_LAST_FETCH, 0L)
        val now = System.currentTimeMillis()
        if (!force && now - lastMillis < MIN_INTERVAL_MS) {
            return@withContext FetchResult(0, skippedSameContent = true)
        }
        try {
            val domains = SOURCES.flatMap { source -> fetchDomains(source) }.distinct()
            if (domains.isEmpty()) throw IOException("No valid domains downloaded")
            val contentHash = sha256(domains.sorted().joinToString("\n"))
            if (contentHash == prefs.getString(KEY_HASH, null)) {
                prefs.edit().putLong(KEY_LAST_FETCH, now).apply()
                return@withContext FetchResult(0, skippedSameContent = true)
            }
            val container = context.sawnContainer
            val existing = container.settings.adultExtraDomains.first()
            val merged = existing + domains
            // Existing entries come first, so the cap cannot discard the previous valid set.
            val capped = if (merged.size > MAX_EXTRA) merged.take(MAX_EXTRA).toSet() else merged
            container.settings.setAdultExtraDomains(capped)
            prefs.edit()
                .putLong(KEY_LAST_FETCH, now)
                .putString(KEY_HASH, contentHash)
                .apply()
            FetchResult(capped.size - existing.size, skippedSameContent = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchResult(0, skippedSameContent = false, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fetchDomains(source: String): List<String> {
        val conn = URL(source).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = "GET"
        try {
            val status = conn.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")
            conn.inputStream.bufferedReader().use { reader ->
                val domains = mutableListOf<String>()
                var totalChars = 0L
                while (true) {
                    val line = reader.readLine() ?: break
                    totalChars += line.length
                    if (totalChars > MAX_SOURCE_CHARS) throw IOException("Source is too large")
                    domains += parseDomains(line)
                    if (domains.size > MAX_SOURCE_DOMAINS) throw IOException("Too many domains")
                }
                return domains
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseDomains(line: String): List<String> {
        val content = line.substringBefore('#').trim()
        if (content.isBlank()) return emptyList()
        val tokens = content.split(WHITESPACE)
        val candidates = if (tokens.size > 1 && tokens.first().isIpLiteral()) tokens.drop(1) else tokens
        return candidates.map { it.trim().trimEnd('.').lowercase() }
            .filter { DomainMatcher.isValidDomain(it) }
    }

    private fun String.isIpLiteral(): Boolean =
        contains(':') || (contains('.') && all { it in '0'..'9' || it == '.' })

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        const val KEY_LAST_FETCH = "last_fetch_millis"
        const val KEY_HASH = "last_content_hash"
        const val MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L // at most once a day
        const val MAX_EXTRA = 100_000
        const val MAX_SOURCE_DOMAINS = 300_000
        const val MAX_SOURCE_CHARS = 12_000_000L
    }
}
