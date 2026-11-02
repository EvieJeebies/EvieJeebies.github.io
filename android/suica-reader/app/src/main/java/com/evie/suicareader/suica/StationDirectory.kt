package com.evie.suicareader.suica

import android.content.Context
import java.io.BufferedReader

/**
 * Optional lookup from (region, line, station) code triples to station names.
 *
 * The codes on the card are just numbers; turning them into "Shinjuku" needs
 * the operator's station table, which is not something this app can derive or
 * ship. So the directory is loaded from `assets/stations.csv` if that file
 * exists, and otherwise stays empty — in which case the UI shows the raw codes.
 *
 * Expected CSV columns (header row optional, `#` comments ignored):
 *
 *     region,line,station,operator,lineName,stationName
 *
 * Numbers may be decimal or `0x`-prefixed hex. See the README for where to get
 * a table in this shape.
 */
class StationDirectory private constructor(
    private val names: Map<Int, Station>,
) {
    data class Station(val operator: String, val line: String, val name: String)

    val isEmpty: Boolean get() = names.isEmpty()

    fun lookup(region: Int, line: Int, station: Int): Station? =
        names[key(region, line, station)]

    /** A short label for a code pair, falling back to hex when unknown. */
    fun label(region: Int, line: Int, station: Int): String {
        if (line == 0 && station == 0) return "—"
        val hit = lookup(region, line, station)
        return hit?.let { if (it.line.isBlank()) it.name else "${it.name} (${it.line})" }
            ?: "line 0x%02X / station 0x%02X".format(line, station)
    }

    companion object {
        private const val ASSET_NAME = "stations.csv"

        private fun key(region: Int, line: Int, station: Int): Int =
            (region shl 16) or (line shl 8) or station

        private fun parseCode(text: String): Int? {
            val t = text.trim()
            return when {
                t.isEmpty() -> null
                t.startsWith("0x", ignoreCase = true) -> t.substring(2).toIntOrNull(16)
                else -> t.toIntOrNull()
            }
        }

        /** Never throws: a missing or malformed table degrades to raw codes. */
        fun load(context: Context): StationDirectory {
            val rows = mutableMapOf<Int, Station>()
            runCatching {
                context.assets.open(ASSET_NAME).bufferedReader().use { reader: BufferedReader ->
                    reader.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                        val cols = trimmed.split(',')
                        if (cols.size < 6) return@forEachLine
                        val region = parseCode(cols[0]) ?: return@forEachLine
                        val lineCode = parseCode(cols[1]) ?: return@forEachLine
                        val stationCode = parseCode(cols[2]) ?: return@forEachLine
                        rows[key(region, lineCode, stationCode)] = Station(
                            operator = cols[3].trim(),
                            line = cols[4].trim(),
                            name = cols[5].trim(),
                        )
                    }
                }
            }
            return StationDirectory(rows)
        }
    }
}
