package com.evie.suicareader.suica

import java.time.format.DateTimeFormatter

private val csvDate = DateTimeFormatter.ISO_LOCAL_DATE

private val CSV_COLUMNS = listOf(
    "index", "date", "time", "process", "terminal", "entry_exit",
    "entry_line", "entry_station", "exit_line", "exit_station",
    "balance_yen", "delta_yen", "sequence", "region", "raw",
)

private fun csvField(value: Any?): String {
    val s = value?.toString() ?: ""
    return if (s.any { it == ',' || it == '"' || it == '\n' }) {
        "\"" + s.replace("\"", "\"\"") + "\""
    } else {
        s
    }
}

/** Flat CSV of the card's history, one row per record, newest first. */
fun SuicaCard.toCsv(): String = buildString {
    appendLine("# Suica Reader export")
    appendLine("# IDm,$idm")
    appendLine("# balance,${balance ?: ""}")
    appendLine(CSV_COLUMNS.joinToString(","))
    history.forEach { e ->
        appendLine(
            listOf(
                e.index,
                e.date?.format(csvDate) ?: "",
                e.time?.toString() ?: "",
                e.processLabel,
                e.terminalLabel,
                e.entryExitLabel,
                "0x%02X".format(e.entryLine),
                "0x%02X".format(e.entryStation),
                "0x%02X".format(e.exitLine),
                "0x%02X".format(e.exitStation),
                e.balance,
                e.delta?.toString() ?: "",
                e.sequence,
                "0x%02X".format(e.regionCode),
                e.rawHex.replace(" ", ""),
            ).joinToString(",", transform = ::csvField),
        )
    }
}
