package com.evie.suicareader

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.bundleOf
import com.evie.suicareader.suica.CardReader
import com.evie.suicareader.suica.ReadResult
import com.evie.suicareader.suica.StationDirectory
import com.evie.suicareader.suica.SuicaCard
import com.evie.suicareader.suica.toCsv
import com.evie.suicareader.ui.ReaderScreen
import com.evie.suicareader.ui.SuicaReaderTheme

/** What the screen is showing right now. */
sealed interface ReaderUiState {
    data class NfcUnavailable(val message: String) : ReaderUiState
    data object NfcDisabled : ReaderUiState
    data object Waiting : ReaderUiState
    data object Reading : ReaderUiState
    data class Error(val message: String) : ReaderUiState
    data class Success(val card: SuicaCard) : ReaderUiState
}

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private var uiState by mutableStateOf<ReaderUiState>(ReaderUiState.Waiting)
    private lateinit var stations: StationDirectory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        stations = StationDirectory.load(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            uiState = ReaderUiState.NfcUnavailable(
                "This phone reports no NFC adapter, so there's nothing to read a card with.",
            )
        }

        setContent {
            SuicaReaderTheme {
                ReaderScreen(
                    state = uiState,
                    directory = stations,
                    onReset = { uiState = ReaderUiState.Waiting },
                    onShareCsv = ::shareCsv,
                    onOpenNfcSettings = {
                        startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                )
            }
        }

        // A card tapped while the app was closed arrives as a TECH_DISCOVERED intent.
        handleTagIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTagIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) {
            uiState = ReaderUiState.NfcDisabled
            return
        }
        if (uiState is ReaderUiState.NfcDisabled) uiState = ReaderUiState.Waiting

        // Reader mode keeps Android's own NDEF handling and the tap sound out of
        // the way, and gives this app exclusive use of the radio while it's open.
        adapter.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            bundleOf(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY to 1000),
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    /** Called by the NFC stack on a background thread when a card is in range. */
    override fun onTagDiscovered(tag: Tag) {
        runOnUiThread { uiState = ReaderUiState.Reading }
        val result = CardReader.read(tag)
        runOnUiThread {
            uiState = when (result) {
                is ReadResult.Success -> ReaderUiState.Success(result.card)
                is ReadResult.Failure -> ReaderUiState.Error(result.message)
            }
        }
    }

    private fun handleTagIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (action != NfcAdapter.ACTION_TECH_DISCOVERED &&
            action != NfcAdapter.ACTION_TAG_DISCOVERED
        ) {
            return
        }
        @Suppress("DEPRECATION")
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag != null) onTagDiscovered(tag)
    }

    private fun shareCsv(card: SuicaCard) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Suica history ${card.idm}")
            putExtra(Intent.EXTRA_TEXT, card.toCsv())
        }
        startActivity(Intent.createChooser(send, "Export history"))
    }
}
