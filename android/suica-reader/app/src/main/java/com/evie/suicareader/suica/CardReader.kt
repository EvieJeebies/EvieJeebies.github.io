package com.evie.suicareader.suica

import android.nfc.Tag
import com.evie.suicareader.felica.FelicaTag
import java.io.IOException

/** Outcome of a single tap. */
sealed interface ReadResult {
    data class Success(val card: SuicaCard) : ReadResult
    data class Failure(val message: String, val recoverable: Boolean = true) : ReadResult
}

/**
 * Turns a discovered [Tag] into a [SuicaCard].
 *
 * The whole exchange is: pick the transit system's IDm with a Polling command,
 * then read the attribute block and the history blocks with Read Without
 * Encryption. Both services are unauthenticated on these cards, which is why a
 * plain reader app can see them at all — and also why this app can only ever
 * read. Writing a balance needs the card's keys, which live in the secure
 * element and are not available to third-party software.
 */
object CardReader {

    fun read(tag: Tag): ReadResult {
        val felica = FelicaTag.from(tag)
            ?: return ReadResult.Failure(
                "That tag isn't a FeliCa card. Suica, PASMO, ICOCA and the other " +
                    "Japanese transit cards appear as NFC-F; this one didn't.",
            )

        return try {
            felica.use { card ->
                card.connect()

                val idm = card.polling(FelicaTag.SYSTEM_CODE_SUICA)
                    ?: return@use ReadResult.Failure(
                        "This FeliCa card has no transit application (system code 0x0003). " +
                            "It may be an Edy, nanaco or WAON card, or a FeliCa Lite tag.",
                    )

                val attribute = runCatching {
                    card.readBlocks(idm, SuicaCard.SERVICE_ATTRIBUTE, 1).firstOrNull()
                }.getOrNull()

                val history = runCatching {
                    card.readBlocks(idm, SuicaCard.SERVICE_HISTORY, SuicaCard.HISTORY_BLOCKS)
                }.getOrDefault(emptyList())

                if (attribute == null && history.isEmpty()) {
                    return@use ReadResult.Failure(
                        "The card answered, but served neither its attribute block nor its " +
                            "history. Hold it still against the back of the phone and try again.",
                    )
                }

                ReadResult.Success(SuicaCard.from(idm, attribute, history))
            }
        } catch (e: IOException) {
            ReadResult.Failure(
                "Lost the card mid-read (${e.message ?: "connection dropped"}). " +
                    "Keep it flat against the phone until the result appears.",
            )
        }
    }
}
