package com.evie.suicareader.felica

/** Hex helpers for the fixed-width fields a FeliCa card returns. */

fun ByteArray.toHex(separator: String = ""): String =
    joinToString(separator) { "%02X".format(it) }

/** Big-endian unsigned read of [length] bytes starting at [offset]. */
fun ByteArray.beInt(offset: Int, length: Int): Int {
    var value = 0
    for (i in 0 until length) value = (value shl 8) or (this[offset + i].toInt() and 0xFF)
    return value
}

/** Little-endian unsigned 16-bit read — how Suica stores the balance. */
fun ByteArray.leShort(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
