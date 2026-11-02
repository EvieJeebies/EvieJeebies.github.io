package com.evie.suicareader.suica

/**
 * Human-readable labels for the code bytes in a Suica history block.
 *
 * These maps come from the widely documented (community reverse-engineered)
 * Suica layout, not from an official JR East specification. They cover the
 * codes you meet in day-to-day use; anything unmapped is shown as raw hex
 * rather than guessed at, so an unexpected value is visible instead of
 * silently mislabelled.
 */
object SuicaCodes {

    /** Byte 0: the kind of machine that wrote the record. */
    private val terminals = mapOf(
        0x03 to "Fare adjustment machine",
        0x04 to "Portable terminal",
        0x05 to "On-board terminal",
        0x07 to "Ticket vending machine",
        0x08 to "Ticket vending machine",
        0x09 to "Deposit / adjustment machine",
        0x12 to "Ticket vending machine (other operator)",
        0x13 to "Ticket vending machine",
        0x14 to "Ticket vending machine",
        0x15 to "Ticket vending machine",
        0x16 to "Ticket gate",
        0x17 to "Simple ticket gate",
        0x18 to "Station office terminal",
        0x19 to "Station office terminal",
        0x1A to "Ticket gate terminal",
        0x1B to "Mobile FeliCa (phone / watch)",
        0x1C to "Entrance-ticket vending machine",
        0x1D to "Fare adjustment (other operator)",
        0x1F to "Cash charge machine",
        0x46 to "VIEW ALTTE",
        0x48 to "VIEW ALTTE (other operator)",
        0xC7 to "Shop (point of sale)",
        0xC8 to "Vending machine",
    )

    /** Byte 1: what happened. */
    private val processes = mapOf(
        0x01 to "Fare payment (exit gate)",
        0x02 to "Charge",
        0x03 to "Ticket purchase",
        0x04 to "Fare adjustment",
        0x05 to "Fare adjustment (other operator)",
        0x06 to "Refund at window",
        0x07 to "New card issued",
        0x08 to "Deduction",
        0x0D to "Bus (PiTaPa)",
        0x0F to "Bus (IruCa)",
        0x11 to "Card reissued",
        0x13 to "Shinkansen payment",
        0x14 to "Entry (auto-charge gate)",
        0x15 to "Exit (auto-charge gate)",
        0x1F to "Bus charge",
        0x23 to "Ticket purchase (other operator)",
        0x46 to "Purchase (shop)",
        0x48 to "Bonus charge",
        0x49 to "Register deposit",
        0x4A to "Purchase cancelled",
        0x4B to "Entry purchase",
        0xC6 to "Purchase (shop, split with cash)",
        0xCB to "Entry purchase (split with cash)",
    )

    /** Byte 3: whether this record is an entry, an exit, or neither. */
    private val entryExit = mapOf(
        0x00 to "—",
        0x01 to "Entry / exit",
        0x02 to "Entry",
        0x03 to "Exit",
        0x0F to "Entry / exit (other operator)",
    )

    fun terminal(code: Int): String = terminals[code] ?: "Terminal 0x%02X".format(code)

    fun process(code: Int): String = processes[code] ?: "Process 0x%02X".format(code)

    fun entryExit(code: Int): String = entryExit[code] ?: "0x%02X".format(code)

    /**
     * Byte 15: the region that issued the record. Only the Kanto/JR East value
     * is labelled; the rest are reported raw rather than guessed, since the
     * published mappings for the other regions disagree with each other.
     */
    fun region(code: Int): String = when (code) {
        0x00 -> "Kanto / JR East area"
        else -> "Region 0x%02X".format(code)
    }

    /** True when bytes 6-7 of a block hold a timestamp instead of station codes. */
    fun isShopTransaction(terminalCode: Int): Boolean =
        terminalCode == 0xC7 || terminalCode == 0xC8
}
