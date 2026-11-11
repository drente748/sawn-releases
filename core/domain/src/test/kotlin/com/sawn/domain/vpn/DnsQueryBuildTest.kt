package com.sawn.domain.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsQueryBuildTest {

    @Test
    fun buildQuery_roundTripThroughParser() {
        val query = DnsPacket.buildQuery("forcesafesearch.google.com", 0x1234)
        // buildQuery emits the bare DNS payload (no IP/UDP wrapper) — verify the question
        // section textually: header + QNAME labels + QTYPE A.
        val text = query.toString(Charsets.ISO_8859_1)
        assertTrue(text.contains("forcesafesearch"))
        assertTrue(text.contains("google"))
        // TYPE A = 00 01 in question trailer, id echoed big-endian.
        assertTrue(query[0] == 0x12.toByte() && query[1] == 0x34.toByte())
        val tail = query.sliceArray(query.size - 4 until query.size)
        assertTrue(tail[0] == 0.toByte() && tail[1] == 1.toByte() && tail[2] == 0.toByte() && tail[3] == 1.toByte())
    }

    @Test
    fun parseFirstA_replyWithAddr_extractsIt() {
        // Craft a minimal DNS response for forcesafesearch... with one A record.
        val qname = "forcesafesearch.google.com"
        val id = 0x4321
        val question = ByteArray(1 + 9 + 1 + 7 + 1 + 4 + 1 /* noop */)
        // Build properly: labels google(6) forcesafesearch... simpler: use helper to build
        // the question section bytes.
        val qbuf = java.io.ByteArrayOutputStream()
        for (label in qname.split('.')) { qbuf.write(label.length); qbuf.write(label.toByteArray()); }
        qbuf.write(0)
        qbuf.write(0x00); qbuf.write(0x01) // TYPE A
        qbuf.write(0x00); qbuf.write(0x01) // CLASS IN
        val questionBytes = qbuf.toByteArray()

        val addr = byteArrayOf(142.toByte(), 250.toByte(), 1, 100)
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C, // pointer to qname
            0x00, 0x01, // TYPE A
            0x00, 0x01, // CLASS IN
            0x00, 0x00, 0x01, 0x00, // TTL
            0x00, 0x04,
            addr[0], addr[1], addr[2], addr[3],
        )
        val dns = byteArrayOf(
            (id shr 8).toByte(), id.toByte(),
            0x81.toByte(), 0x80.toByte(), // QR, RD, RA
            0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
        ) + questionBytes + answer
        val a = DnsPacket.parseFirstA(dns, dns.size)
        // 142.250.1.100 → 0x8EFA0164 exactly as the parser packs (b0<<24|b1<<16|b2<<8|b3).
        assertEquals(2398749028L, (a ?: -1).toLong() and 0xFFFFFFFFL)
    }

    @Test
    fun parseFirstA_noAnswer_returnsNull() {
        val qbuf = java.io.ByteArrayOutputStream()
        for (label in "example.com".split('.')) { qbuf.write(label.length); qbuf.write(label.toByteArray()); }
        qbuf.write(0)
        qbuf.write(0x00); qbuf.write(0x01); qbuf.write(0x00); qbuf.write(0x01)
        val dns = byteArrayOf(
            0x00, 0x01, 0x81.toByte(), 0x80.toByte(),
            0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        ) + qbuf.toByteArray()
        assertNull(DnsPacket.parseFirstA(dns, dns.size))
    }

    @Test
    fun buildQuery_idIsEchoed() {
        val query = DnsPacket.buildQuery("example.com", 0xBEEF)
        assertEquals(0xBE.toByte(), query[0])
        assertEquals(0xEF.toByte(), query[1])
    }
}
