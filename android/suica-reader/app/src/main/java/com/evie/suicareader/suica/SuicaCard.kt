package com.evie.suicareader.suica

import com.evie.suicareader.felica.beInt
import com.evie.suicareader.felica.leShort
import com.evie.suicareader.felica.toHex
import java.time.LocalDate
import java.time.LocalTime

/**
 * One record from the Suica ride/purchase history (service 0x090F).
 *
 * Block layout, 16 bytes:
 * ```
 *  0      terminal type
 *  1      process type
 *  2      payment type
 *  3      entry/exit type
 *  4-5    date: yyyyyyy mmmm ddddd  (year counted from 2000)
 *  6-7    entry line + station code, or a HH:MM timestamp for shop records
 *  8-9    exit line + station code
 * 10-11   remaining balance, little-endian
 * 12-14   transaction sequence number, big-endian
 * 15      region code
 * ```
 */
data class SuicaHistoryEntry(
    val index: Int,
    val terminalCode: Int,
    val processCode: Int,
    val paymentCode: Int,
    val entryExitCode: Int,
    val date: LocalDate?,
    val time: LocalTime?,
    val entryLine: Int,
    val entryStation: Int,
    val exitLine: Int,
    val exitStation: Int,
    val balance: Int,
    val sequence: Int,
    val regionCode: Int,
    val raw: ByteArray,
) {
    /** Change in balance against the previous (older) record, when known. */
    var delta: Int? = null
        internal set

    val isShop: Boolean get() = SuicaCodes.isShopTransaction(terminalCode)
    val terminalLabel: String get() = SuicaCodes.terminal(terminalCode)
    val processLabel: String get() = SuicaCodes.process(processCode)
    val entryExitLabel: String get() = SuicaCodes.entryExit(entryExitCode)
    val regionLabel: String get() = SuicaCodes.region(regionCode)
    val rawHex: String get() = raw.toHex(" ")

    /** True when the record is all zeroes, i.e. an unused history slot. */
    val isBlank: Boolean get() = raw.all { it == 0.toByte() }

    override fun equals(other: Any?): Boolean =
        other is SuicaHistoryEntry && raw.contentEquals(other.raw) && index == other.index

    override fun hashCode(): Int = 31 * index + raw.contentHashCode()

    companion object {
        fun parse(index: Int, block: ByteArray): SuicaHistoryEntry {
            require(block.size == 16) { "A history block is 16 bytes, got ${block.size}" }

            val terminal = block[0].toInt() and 0xFF
            val packedDate = block.beInt(4, 2)
            val year = (packedDate shr 9) and 0x7F
            val month = (packedDate shr 5) and 0x0F
            val day = packedDate and 0x1F
            val date = runCatching {
                if (month in 1..12 && day in 1..31) LocalDate.of(2000 + year, month, day) else null
            }.getOrNull()

            val shop = SuicaCodes.isShopTransaction(terminal)
            val packedTime = block.beInt(6, 2)
            val time = if (shop) {
                val hour = (packedTime shr 11) and 0x1F
                val minute = (packedTime shr 5) and 0x3F
                if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
            } else {
                null
            }

            return SuicaHistoryEntry(
                index = index,
                terminalCode = terminal,
                processCode = block[1].toInt() and 0xFF,
                paymentCode = block[2].toInt() and 0xFF,
                entryExitCode = block[3].toInt() and 0xFF,
                date = date,
                time = time,
                entryLine = if (shop) 0 else block[6].toInt() and 0xFF,
                entryStation = if (shop) 0 else block[7].toInt() and 0xFF,
                exitLine = block[8].toInt() and 0xFF,
                exitStation = block[9].toInt() and 0xFF,
                balance = block.leShort(10),
                sequence = block.beInt(12, 3),
                regionCode = block[15].toInt() and 0xFF,
                raw = block,
            )
        }
    }
}

/** Everything this app can learn from one tap. */
data class SuicaCard(
    val idm: String,
    val cardTypeCode: Int?,
    /**
     * Current balance in yen. Taken from the attribute service when the card
     * serves it, otherwise from the newest history record.
     */
    val balance: Int?,
    val balanceSource: String,
    val history: List<SuicaHistoryEntry>,
    val attributeBlockHex: String?,
) {
    companion object {
        /** Suica attribute service — card type and current balance. */
        const val SERVICE_ATTRIBUTE = 0x008B

        /** Suica history service — 20 blocks, newest first. */
        const val SERVICE_HISTORY = 0x090F

        const val HISTORY_BLOCKS = 20

        /**
         * Builds a card model from the raw service reads.
         *
         * In the attribute block, byte 0 is the card type and bytes 11-12 hold
         * the balance, little-endian. If that block is missing the newest
         * history record's own balance field is used instead, which is the same
         * number unless the card was written to between the two reads.
         */
        fun from(
            idm: ByteArray,
            attributeBlock: ByteArray?,
            historyBlocks: List<ByteArray>,
        ): SuicaCard {
            val history = historyBlocks
                .mapIndexed { i, block -> SuicaHistoryEntry.parse(i, block) }
                .filterNot { it.isBlank }

            // Records come back newest first, so the previous balance of entry
            // i is held by entry i + 1.
            for (i in history.indices) {
                val older = history.getOrNull(i + 1) ?: continue
                history[i].delta = history[i].balance - older.balance
            }

            val attributeBalance = attributeBlock?.takeIf { it.size == 16 }?.leShort(11)
            val balance = attributeBalance ?: history.firstOrNull()?.balance

            return SuicaCard(
                idm = idm.toHex(),
                cardTypeCode = attributeBlock?.takeIf { it.size == 16 }
                    ?.let { it[0].toInt() and 0xFF },
                balance = balance,
                balanceSource = if (attributeBalance != null) {
                    "attribute service 0x%04X".format(SERVICE_ATTRIBUTE)
                } else {
                    "newest history record"
                },
                history = history,
                attributeBlockHex = attributeBlock?.toHex(" "),
            )
        }
    }
}
