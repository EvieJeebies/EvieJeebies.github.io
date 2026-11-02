package com.evie.suicareader

import com.evie.suicareader.suica.SuicaCard
import com.evie.suicareader.suica.SuicaHistoryEntry
import com.evie.suicareader.suica.toCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Parser tests built from hand-assembled blocks, so each field's encoding is
 * asserted independently of any particular card.
 */
class SuicaParserTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** Gate exit, 15 March 2024, ¥1,234 left, sequence 5. */
    private val gateBlock = bytes(
        0x16, 0x01, 0x00, 0x01,
        0x30, 0x6F, // date: year 24, month 3, day 15
        0x01, 0x02, // entry line / station
        0x03, 0x04, // exit line / station
        0xD2, 0x04, // balance 1234, little-endian
        0x00, 0x00, 0x05, // sequence
        0x00, // region
    )

    /** Shop purchase at 13:45 on 1 January 2025, ¥500 left. */
    private val shopBlock = bytes(
        0xC7, 0x46, 0x00, 0x00,
        0x32, 0x21, // date: year 25, month 1, day 1
        0x6D, 0xA0, // time 13:45
        0x00, 0x00,
        0xF4, 0x01, // balance 500
        0x00, 0x00, 0x06,
        0x00,
    )

    @Test
    fun `gate record decodes date balance and codes`() {
        val entry = SuicaHistoryEntry.parse(0, gateBlock)

        assertEquals(LocalDate.of(2024, 3, 15), entry.date)
        assertEquals(1234, entry.balance)
        assertEquals(5, entry.sequence)
        assertEquals(0x01, entry.entryLine)
        assertEquals(0x02, entry.entryStation)
        assertEquals(0x03, entry.exitLine)
        assertEquals(0x04, entry.exitStation)
        assertEquals("Ticket gate", entry.terminalLabel)
        assertEquals("Fare payment (exit gate)", entry.processLabel)
        assertEquals("Kanto / JR East area", entry.regionLabel)
        // Bytes 6-7 are station codes on a gate record, not a clock.
        assertNull(entry.time)
    }

    @Test
    fun `shop record decodes the timestamp instead of station codes`() {
        val entry = SuicaHistoryEntry.parse(0, shopBlock)

        assertTrue(entry.isShop)
        assertEquals(LocalDate.of(2025, 1, 1), entry.date)
        assertEquals(LocalTime.of(13, 45), entry.time)
        assertEquals(500, entry.balance)
        assertEquals(0, entry.entryLine)
        assertEquals(0, entry.entryStation)
    }

    @Test
    fun `an all-zero block is treated as an unused slot`() {
        assertTrue(SuicaHistoryEntry.parse(0, ByteArray(16)).isBlank)
    }

    @Test
    fun `an implausible date yields null rather than a wrong date`() {
        // Month 13, day 0.
        val block = gateBlock.copyOf()
        block[4] = 0x31.toByte()
        block[5] = 0xA0.toByte()
        assertNull(SuicaHistoryEntry.parse(0, block).date)
    }

    @Test
    fun `card drops blank blocks and computes deltas against the older record`() {
        val card = SuicaCard.from(
            idm = bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08),
            attributeBlock = null,
            historyBlocks = listOf(shopBlock, gateBlock, ByteArray(16)),
        )

        assertEquals("0102030405060708", card.idm)
        assertEquals(2, card.history.size)
        // Newest record spent 1234 - 500 = 734 yen.
        assertEquals(-734, card.history[0].delta)
        // Oldest record has nothing older to compare against.
        assertNull(card.history[1].delta)
        assertEquals(500, card.balance)
        assertEquals("newest history record", card.balanceSource)
    }

    @Test
    fun `attribute block supplies the authoritative balance`() {
        // Balance 9876 (0x2694) at bytes 11-12, little-endian.
        val attribute = ByteArray(16)
        attribute[0] = 0x00
        attribute[11] = 0x94.toByte()
        attribute[12] = 0x26.toByte()

        val card = SuicaCard.from(
            idm = ByteArray(8),
            attributeBlock = attribute,
            historyBlocks = listOf(gateBlock),
        )

        assertEquals(9876, card.balance)
        assertTrue(card.balanceSource.contains("008B"))
    }

    @Test
    fun `csv export has a row per record`() {
        val card = SuicaCard.from(ByteArray(8), null, listOf(shopBlock, gateBlock))
        val lines = card.toCsv().trim().lines()

        // 3 comment/header lines for the preamble, 1 column header, 2 data rows.
        assertEquals(6, lines.size)
        assertTrue(lines[3].startsWith("index,date,time"))
        assertTrue(lines[4].startsWith("0,2025-01-01,13:45"))
    }
}
