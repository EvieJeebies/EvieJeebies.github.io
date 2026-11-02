package com.evie.suicareader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.evie.suicareader.ReaderUiState
import com.evie.suicareader.suica.StationDirectory
import com.evie.suicareader.suica.SuicaCard
import com.evie.suicareader.suica.SuicaHistoryEntry
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun yen(amount: Int): String = "¥%,d".format(amount)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    directory: StationDirectory,
    onReset: () -> Unit,
    onShareCsv: (SuicaCard) -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suica Reader") },
                actions = {
                    if (state is ReaderUiState.Success) {
                        IconButton(onClick = { onShareCsv(state.card) }) {
                            Icon(Icons.Default.Share, contentDescription = "Export as CSV")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            when (state) {
                is ReaderUiState.NfcUnavailable -> CentredMessage(
                    icon = Icons.Default.ErrorOutline,
                    title = "No NFC on this device",
                    body = state.message,
                )

                ReaderUiState.NfcDisabled -> CentredMessage(
                    icon = Icons.Default.ErrorOutline,
                    title = "NFC is switched off",
                    body = "Turn NFC on, then come back and tap your card.",
                    action = "Open NFC settings" to onOpenNfcSettings,
                )

                ReaderUiState.Waiting -> CentredMessage(
                    icon = Icons.Default.Contactless,
                    title = "Tap your card",
                    body = "Hold the card flat against the upper-middle of the phone's back " +
                        "and keep it still until the balance appears.",
                )

                ReaderUiState.Reading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Reading card…", style = MaterialTheme.typography.titleMedium)
                }

                is ReaderUiState.Error -> CentredMessage(
                    icon = Icons.Default.ErrorOutline,
                    title = "Couldn't read that card",
                    body = state.message,
                    action = "Try again" to onReset,
                )

                is ReaderUiState.Success -> CardDetails(state.card, directory, onReset)
            }
        }
    }
}

@Composable
private fun CentredMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = action.second) { Text(action.first) }
        }
    }
}

@Composable
private fun CardDetails(
    card: SuicaCard,
    directory: StationDirectory,
    onReset: () -> Unit,
) {
    var showRaw by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "Balance",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        card.balance?.let(::yen) ?: "unknown",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(10.dp))
                    KeyValue("Card ID (IDm)", card.idm, onPrimary = true)
                    card.cardTypeCode?.let {
                        KeyValue("Card type byte", "0x%02X".format(it), onPrimary = true)
                    }
                    KeyValue("Balance read from", card.balanceSource, onPrimary = true)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "History (${card.history.size} records)",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide raw bytes" else "Show raw bytes")
                }
            }
        }

        if (card.history.isEmpty()) {
            item {
                Text(
                    "This card served no history records. Some cards clear the log after a " +
                        "reissue, and gate records can take a moment to be written.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        items(card.history, key = { it.index }) { entry ->
            HistoryRow(entry, directory, showRaw)
            HorizontalDivider()
        }

        if (directory.isEmpty && card.history.isNotEmpty()) {
            item {
                Text(
                    "Station names are shown as raw line/station codes: no station table is " +
                        "bundled. Add one at app/src/main/assets/stations.csv to see names.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                Text("Read another card")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistoryRow(
    entry: SuicaHistoryEntry,
    directory: StationDirectory,
    showRaw: Boolean,
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.processLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(entry.date?.format(dateFormat) ?: "date unknown")
                        entry.time?.let { append(" · ").append(it.format(timeFormat)) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                entry.delta?.takeIf { it != 0 }?.let { delta ->
                    Text(
                        (if (delta > 0) "+" else "−") + yen(kotlin.math.abs(delta)),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (delta > 0) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Text(
                    yen(entry.balance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!entry.isShop) {
            val from = directory.label(entry.regionCode, entry.entryLine, entry.entryStation)
            val to = directory.label(entry.regionCode, entry.exitLine, entry.exitStation)
            if (from != "—" || to != "—") {
                Text(
                    "$from → $to",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "${entry.terminalLabel} · ${entry.entryExitLabel} · #${entry.sequence} · " +
                entry.regionLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (showRaw) {
            Text(
                entry.rawHex,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String, onPrimary: Boolean = false) {
    val colour = if (onPrimary) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colour)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = colour,
        )
    }
}
