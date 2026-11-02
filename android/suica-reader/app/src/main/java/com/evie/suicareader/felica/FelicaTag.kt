package com.evie.suicareader.felica

import android.nfc.Tag
import android.nfc.tech.NfcF
import java.io.Closeable
import java.io.IOException

/** Thrown when the card answers, but with an error status or a malformed frame. */
class FelicaException(message: String) : IOException(message)

/**
 * Minimal FeliCa command layer on top of [NfcF].
 *
 * Android hands us a raw NFC-F transport, so every frame has to be assembled by
 * hand. A FeliCa frame is `[length][command][payload...]`, where `length`
 * counts itself. Responses come back in the same shape.
 *
 * Only the two commands a transit card reader needs are implemented: Polling
 * (0x00) and Read Without Encryption (0x06). Everything a Suica exposes to the
 * outside world lives in services that are readable without authentication, so
 * no key material is involved anywhere in this app.
 */
class FelicaTag private constructor(private val nfcF: NfcF) : Closeable {

    companion object {
        /** System code of the transit application on Suica/PASMO/ICOCA/etc. */
        const val SYSTEM_CODE_SUICA = 0x0003

        /** Wildcard: "answer whatever system you have." */
        const val SYSTEM_CODE_WILDCARD = 0xFFFF

        private const val CMD_POLLING: Byte = 0x00
        private const val RES_POLLING: Byte = 0x01
        private const val CMD_READ_WITHOUT_ENCRYPTION: Byte = 0x06
        private const val RES_READ_WITHOUT_ENCRYPTION: Byte = 0x07

        /**
         * Blocks requested per command. The real limit is card-dependent; four
         * is the value Suica is documented to accept and leaves the response
         * comfortably inside the NFC-F frame limit.
         */
        private const val MAX_BLOCKS_PER_READ = 4

        fun from(tag: Tag): FelicaTag? = NfcF.get(tag)?.let(::FelicaTag)
    }

    fun connect() {
        if (!nfcF.isConnected) nfcF.connect()
    }

    override fun close() {
        runCatching { nfcF.close() }
    }

    /** The IDm Android reported at discovery time. */
    val discoveredIdm: ByteArray get() = nfcF.tag.id

    /**
     * Polling (0x00). Returns the IDm for [systemCode], or null when the card
     * has no such system.
     *
     * This matters for phones and multi-application cards: one physical card can
     * hold several systems, each with its own IDm, and reads must use the IDm of
     * the system that owns the service.
     */
    fun polling(systemCode: Int): ByteArray? {
        val request = byteArrayOf(
            0x06, // frame length
            CMD_POLLING,
            ((systemCode shr 8) and 0xFF).toByte(),
            (systemCode and 0xFF).toByte(),
            0x01, // request code: also return the system code
            0x00, // time slot: one response is enough
        )
        val response = try {
            nfcF.transceive(request)
        } catch (e: IOException) {
            return null
        }
        // length + response code + IDm(8) + PMm(8) = 18 bytes minimum
        if (response.size < 18 || response[1] != RES_POLLING) return null
        return response.copyOfRange(2, 10)
    }

    /**
     * Read Without Encryption (0x06) for a single chunk of up to
     * [MAX_BLOCKS_PER_READ] blocks.
     *
     * @return one 16-byte array per requested block, in request order.
     * @throws FelicaException if the card reports a non-zero status flag.
     */
    private fun readChunk(idm: ByteArray, serviceCode: Int, blocks: List<Int>): List<ByteArray> {
        require(idm.size == 8) { "IDm must be 8 bytes, was ${idm.size}" }
        require(blocks.isNotEmpty() && blocks.size <= MAX_BLOCKS_PER_READ)

        val frameLength = 1 + 1 + 8 + 1 + 2 + 1 + (2 * blocks.size)
        val request = ByteArray(frameLength)
        var i = 0
        request[i++] = frameLength.toByte()
        request[i++] = CMD_READ_WITHOUT_ENCRYPTION
        idm.copyInto(request, i); i += 8
        request[i++] = 1 // one service
        // Service code list is little-endian.
        request[i++] = (serviceCode and 0xFF).toByte()
        request[i++] = ((serviceCode shr 8) and 0xFF).toByte()
        request[i++] = blocks.size.toByte()
        for (block in blocks) {
            // Two-byte block list element: 0x80 = 2-byte format, service index 0.
            request[i++] = 0x80.toByte()
            request[i++] = (block and 0xFF).toByte()
        }

        val response = nfcF.transceive(request)

        // length + code + IDm(8) + status1 + status2 + blockCount = 13 bytes
        if (response.size < 13 || response[1] != RES_READ_WITHOUT_ENCRYPTION) {
            throw FelicaException("Unexpected response frame: ${response.toHex()}")
        }
        val status1 = response[10].toInt() and 0xFF
        val status2 = response[11].toInt() and 0xFF
        if (status1 != 0x00) {
            throw FelicaException(
                "Card refused read of service 0x%04X: status %02X %02X"
                    .format(serviceCode, status1, status2),
            )
        }
        val returned = response[12].toInt() and 0xFF
        if (response.size < 13 + (returned * 16)) {
            throw FelicaException("Truncated block data: ${response.toHex()}")
        }
        return (0 until returned).map { n ->
            response.copyOfRange(13 + (n * 16), 13 + (n * 16) + 16)
        }
    }

    /**
     * Reads [count] consecutive blocks from [serviceCode], starting at block 0.
     *
     * Reads are chunked, and a chunk that fails is retried one block at a time
     * before giving up: a card that rejects a 4-block request will often serve
     * single blocks happily, and a short history is better than no history. The
     * returned list stops at the first block the card will not hand over.
     */
    fun readBlocks(idm: ByteArray, serviceCode: Int, count: Int): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var next = 0
        while (next < count) {
            val chunk = (next until minOf(next + MAX_BLOCKS_PER_READ, count)).toList()
            val read = try {
                readChunk(idm, serviceCode, chunk)
            } catch (e: FelicaException) {
                // Fall back to single-block reads for this chunk.
                val singles = mutableListOf<ByteArray>()
                for (block in chunk) {
                    val one = try {
                        readChunk(idm, serviceCode, listOf(block))
                    } catch (inner: FelicaException) {
                        break
                    }
                    singles += one
                }
                singles
            }
            if (read.isEmpty()) break
            out += read
            next += read.size
        }
        return out
    }
}
