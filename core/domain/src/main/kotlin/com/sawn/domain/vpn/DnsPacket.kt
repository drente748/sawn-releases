package com.sawn.domain.vpn

import java.nio.ByteBuffer

/**
 * Minimal IPv4 + UDP + DNS helpers, just enough to sinkhole or forward DNS queries.
 * Deliberately handles only the common case (IPv4 / UDP / single question). TCP-DNS,
 * IPv6 and EDNS edge cases are punted to the forwarder and left as follow-ups.
 */
object DnsPacket {

    private const val IPV4_MIN_HEADER = 20
    private const val UDP_HEADER = 8
    const val DNS_PORT = 53

    data class Parsed(
        val ipHeaderLen: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dnsPayloadOffset: Int,
        val dnsPayloadLen: Int,
        val qname: String?,
        /** DNS question type (1=A, 28=AAAA, …) read right after the QNAME. */
        val queryType: Int,
        val dnsFlags: Int,
    )

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val MAX_LABEL_LENGTH = 63
    private const val MAX_NAME_LENGTH = 255

    /** Returns null if the packet isn't IPv4/UDP or is malformed. */
    fun parse(packet: ByteArray, length: Int): Parsed? {
        if (length < IPV4_MIN_HEADER + UDP_HEADER) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0xF) * 4
        // ihl comes from the packet itself; validate it fits before reading UDP/DNS at that
        // offset, so a malformed header can't make us parse stale bytes from the reused buffer.
        if (ihl < IPV4_MIN_HEADER || ihl + UDP_HEADER > length) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17 /* UDP */) return null

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val udpOffset = ihl
        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)
        val dnsOffset = udpOffset + UDP_HEADER
        val udpLength = ((packet[udpOffset + 4].toInt() and 0xFF) shl 8) or
            (packet[udpOffset + 5].toInt() and 0xFF)
        if (udpLength < UDP_HEADER + 12 || udpLength > length - udpOffset) return null
        val dnsLen = udpLength - UDP_HEADER
        val dnsEnd = dnsOffset + dnsLen
        val dnsFlags = ((packet[dnsOffset + 2].toInt() and 0xFF) shl 8) or
            (packet[dnsOffset + 3].toInt() and 0xFF)
        val questionCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8) or
            (packet[dnsOffset + 5].toInt() and 0xFF)
        if (dnsFlags and 0x8000 != 0 || questionCount != 1) return null

        val qnameResult = runCatching { readQName(packet, dnsOffset + 12, dnsEnd) }.getOrNull()
            ?.takeIf { it.first.isNotBlank() && it.second + 4 <= dnsEnd } ?: return null
        val qname = qnameResult.first
        // TYPE and CLASS immediately follow the bounded QNAME.
        val qtypeEnd = qnameResult.second
        val queryType = ((packet[qtypeEnd].toInt() and 0xFF) shl 8) or
            (packet[qtypeEnd + 1].toInt() and 0xFF)
        return Parsed(ihl, srcIp, dstIp, srcPort, dstPort, dnsOffset, dnsLen, qname, queryType, dnsFlags)
    }

    /** Reads the QNAME labels of the first DNS question, lowercased, no trailing dot.
     *  Returns (name, index right after the 0-length root label). */
    private fun readQName(buf: ByteArray, start: Int, end: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = start
        var wireLength = 0
        while (i < end) {
            val len = buf[i++].toInt() and 0xFF
            if (len == 0) return sb.toString().lowercase() to i
            if (len and 0xC0 != 0 || len > MAX_LABEL_LENGTH || i + len > end) return null
            wireLength += len + 1
            if (wireLength > MAX_NAME_LENGTH) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 0 until len) {
                val char = (buf[i + j].toInt() and 0xFF).toChar()
                if (char !in '!'..'~' || char == '.') return null
                sb.append(char)
            }
            i += len
        }
        return null
    }

    /**
     * Builds a minimally valid A query for [qname] with transaction id [tid], ready to be
     * sent to an upstream resolver through `forward()`-style plumbing. Used to resolve the
     * safe-search anchors once per safe host.
     */
    fun buildQuery(qname: String, tid: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write((tid shr 8) and 0xFF); out.write(tid and 0xFF)
        out.write(0x01); out.write(0x00) // RD
        out.write(0x00); out.write(0x01) // QDCOUNT 1
        out.write(0x00); out.write(0x00) // ANCOUNT
        out.write(0x00); out.write(0x00) // NSCOUNT
        out.write(0x00); out.write(0x00) // ARCOUNT
        for (label in qname.split('.')) {
            if (label.isBlank()) continue
            out.write(label.length)
            out.write(label.toByteArray(Charsets.ISO_8859_1))
        }
        out.write(0)
        out.write(0x00); out.write(0x01) // TYPE A
        out.write(0x00); out.write(0x01) // CLASS IN
        return out.toByteArray()
    }

    /**
     * Scans a DNS response for the first A record and returns its IPv4 address as an Int,
     * or null when there is none. Skips question records and skips CNAME answers until an
     * A record with a literal IPv4 rdata is found.
     */
    fun parseFirstA(dns: ByteArray, length: Int): Int? {
        if (length < 12) return null
        val qd = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
        val an = ((dns[6].toInt() and 0xFF) shl 8) or (dns[7].toInt() and 0xFF)
        var i = 12
        // Skip the question section.
        repeat(qd) {
            i = skipName(dns, i, length) ?: return null
            i += 4 // TYPE + CLASS
        }
        var answersLeft = an
        while (answersLeft-- > 0 && i < length) {
            i = skipName(dns, i, length) ?: return null
            if (i + 10 > length) return null
            val type = ((dns[i].toInt() and 0xFF) shl 8) or (dns[i + 1].toInt() and 0xFF)
            val rdlen = ((dns[i + 8].toInt() and 0xFF) shl 8) or (dns[i + 9].toInt() and 0xFF)
            val rdStart = i + 10
            if (rdStart + rdlen > length) return null
            if (type == TYPE_A && rdlen == 4) {
                return ((dns[rdStart].toInt() and 0xFF) shl 24) or
                    ((dns[rdStart + 1].toInt() and 0xFF) shl 16) or
                    ((dns[rdStart + 2].toInt() and 0xFF) shl 8) or
                    (dns[rdStart + 3].toInt() and 0xFF)
            }
            i = rdStart + rdlen
        }
        return null
    }

    /** Skips a (possibly compressed) name at [start]; returns index after the root label. */
    private fun skipName(buf: ByteArray, start: Int, length: Int): Int? {
        var i = start
        while (i < length) {
            val len = buf[i].toInt() and 0xFF
            if (len == 0) return i + 1
            if (len and 0xC0 != 0) return i + 2 // compression pointer
            i += 1 + len
        }
        return null
    }

    /**
     * Builds a response packet answering the query in [request] with A-record 0.0.0.0.
     * Used for A queries on blocked domains.
     */
    fun buildSinkholeResponse(request: ByteArray, length: Int, parsed: Parsed): ByteArray {
        require(parsed.queryType == TYPE_A) { "IPv4 sinkhole answer requires an A query" }
        return buildBlockedResponse(
            request,
            length,
            parsed,
            addressAnswer = true,
            answerType = TYPE_A,
        )
    }

    /**
     * Builds a response answering [request] with a specific IPv4 [addrInt] — used by the
     * safe-search rewriter to answer google.com etc. with the safe anchor's address.
     */
    fun buildSinkholeIpResponse(
        request: ByteArray,
        length: Int,
        parsed: Parsed,
        addrInt: Int,
    ): ByteArray {
        require(parsed.queryType == TYPE_A) { "Safe-search IP answer requires an A query" }
        val base = buildBlockedResponse(
            request,
            length,
            parsed,
            addressAnswer = true,
            answerType = TYPE_A,
        )
        // The A record's rdata sits in the last 4 bytes written by buildBlockedResponse.
        val n = base.size
        base[n - 4] = ((addrInt ushr 24) and 0xFF).toByte()
        base[n - 3] = ((addrInt ushr 16) and 0xFF).toByte()
        base[n - 2] = ((addrInt ushr 8) and 0xFF).toByte()
        base[n - 1] = (addrInt and 0xFF).toByte()
        return base
    }

    /**
     * Builds a NOERROR answer with zero records. Used for AAAA queries on blocked domains:
     * the domain has (synthetically) no IPv6 address, so clients stop probing ipv6 paths
     * instead of retrying against a malformed reply — and can't leak out over IPv6.
     *
     * Also used for blocked hosts queried with other record types (HTTPS/65, TXT, ...):
     * a NOERROR/0-answer reply unblocks the client instantly, whereas a dropped query
     * leaves Chromium retrying for seconds on end.
     */
    fun buildNullResponse(request: ByteArray, length: Int, parsed: Parsed): ByteArray =
        buildBlockedResponse(
            request,
            length,
            parsed,
            addressAnswer = false,
            answerType = parsed.queryType,
        )

    private fun buildBlockedResponse(
        request: ByteArray,
        length: Int,
        parsed: Parsed,
        addressAnswer: Boolean,
        answerType: Int,
    ): ByteArray {
        val dnsHeaderLen = 12
        val questionLen = parsed.dnsPayloadLen - dnsHeaderLen
        // Answer: pointer to name (0xC00C), queried type, class IN, TTL and 4-byte address.
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C,
            (answerType ushr 8).toByte(), answerType.toByte(),
            0x00, 0x01, // class IN
            0x00, 0x00, 0x00, 0x1E, // TTL 30s
            0x00, 0x04, // rdlength
            0x00, 0x00, 0x00, 0x00, // 0.0.0.0
        )
        val answerLen = if (addressAnswer) answer.size else 0
        val dnsLen = parsed.dnsPayloadLen + answerLen
        val totalLen = parsed.ipHeaderLen + UDP_HEADER + dnsLen
        val out = ByteBuffer.allocate(totalLen)

        // --- IPv4 header (copy request, swap addresses, fix length) ---
        out.put(request, 0, parsed.ipHeaderLen)
        out.putShort(2, totalLen.toShort())
        // swap src/dst
        for (k in 0 until 4) out.put(12 + k, parsed.dstIp[k])
        for (k in 0 until 4) out.put(16 + k, parsed.srcIp[k])
        out.putShort(10, 0) // checksum zeroed, recomputed below
        fixIpChecksum(out.array(), parsed.ipHeaderLen)

        // --- UDP header (swap ports, fix length) ---
        val u = parsed.ipHeaderLen
        out.putShort(u, parsed.dstPort.toShort())
        out.putShort(u + 2, parsed.srcPort.toShort())
        out.putShort(u + 4, (UDP_HEADER + dnsLen).toShort())
        out.putShort(u + 6, 0) // UDP checksum optional for IPv4

        // --- DNS: copy header+question from request, flip QR/AA, set answer count ---
        val d = parsed.ipHeaderLen + UDP_HEADER
        out.position(d)
        out.put(request, parsed.dnsPayloadOffset, dnsHeaderLen + questionLen)
        val arr = out.array()
        // Preserve opcode, recursion-desired and other request flags; set QR and AA.
        arr[d + 2] = ((parsed.dnsFlags ushr 8) or 0x84).toByte()
        arr[d + 3] = (parsed.dnsFlags or 0x80).toByte()
        arr[d + 6] = 0x00; arr[d + 7] = if (addressAnswer) 0x01 else 0x00 // ANCOUNT
        if (addressAnswer) {
            out.position(d + dnsHeaderLen + questionLen)
            out.put(answer)
        }
        return arr
    }

    private fun fixIpChecksum(buf: ByteArray, headerLen: Int) {
        var sum = 0L
        var i = 0
        while (i < headerLen) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv().toInt() and 0xFFFF
        buf[10] = (checksum ushr 8).toByte()
        buf[11] = (checksum and 0xFF).toByte()
    }
}
