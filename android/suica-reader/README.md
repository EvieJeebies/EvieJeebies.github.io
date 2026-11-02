# Suica Reader

An Android app that reads a physical Suica (or PASMO, ICOCA, TOICA, manaca,
Kitaca, SUGOCA, nimoca, hayakaken) card with the NFC radio in a Pixel 9, and
shows the balance and the last 20 transactions.

## Read this part first: what an app can and cannot do

There are two different things people mean by "make my phone work with a Suica
card", and only one of them is something an app can do.

**Reading a card you hold against the phone — yes.** That is this app. The
balance and travel history on a Suica live in FeliCa services that are readable
without authentication, so any app with the `NFC` permission can read them. No
root, no special partnership, no Japanese device.

**Turning the phone itself into a Suica — no, not from a third-party app.** This
is worth being clear about, because it is usually what the question is really
asking:

- A Mobile Suica lives in the phone's **secure element**, under keys held by
  FeliCa Networks and JR East. Android's host card emulation (`HostApduService`)
  cannot emulate FeliCa for transit at all, and secure-element access is granted
  only to the operator's own signed applet — Google Wallet and Mobile Suica. An
  app cannot write itself in there, and it cannot copy a plastic card's keys out
  either; the balance is cryptographically signed and the gate verifies it.
- On a Pixel, Suica in Google Wallet needs a **Japan-market device**. Google's
  FeliCa support has been limited to Japan-sold models since the Pixel 3, and
  Google Wallet offers Suica and PASMO only on FeliCa-equipped Android devices in
  Japan. (For the Pixel Watch, Google's own help page says outright that the
  watch must have been purchased in Japan.) Reports differ on which recent global
  Pixel units carry the FeliCa radio at all, and where the hardware is present
  the Osaifu-Keitai stack is still locked on non-Japanese models. Either way: it
  is a device and region question, not something software you install can change.

So if the goal is tapping the phone at a ticket gate, the route is a Japan-market
handset with Google Wallet (or the Mobile Suica app), not an APK. If the goal is
seeing what is on the plastic card you already carry — balance, where you
travelled, what you spent at the kiosk — that is this app, and it works on any
NFC-F capable Android phone.

Nothing here writes to a card. The app only issues read commands.

## What it shows

- Current balance, and the card's IDm (serial).
- The last 20 history records: date, what happened (gate exit, charge, shop
  purchase, bus, reissue…), the machine type that wrote it, the balance after
  the transaction, and the amount it moved by.
- Shop and vending-machine records include the `HH:MM` timestamp; gate records
  include entry and exit line/station codes instead (the card stores one or the
  other in the same two bytes).
- Raw hex per record, behind a toggle, for when you want to check the parse.
- Export to CSV via the share sheet.

Station names are **not** bundled — see below.

## Building

Requires Android Studio (or a command-line Android SDK) with API 35 platform
and JDK 17+.

```bash
# In Android Studio: File > Open > android/suica-reader, then Run.

# Or from the command line, with ANDROID_HOME set:
cd android/suica-reader
gradle wrapper          # one-off: generates gradlew + the wrapper jar
./gradlew :app:installDebug
```

Run the parser tests (no device or Android SDK needed for the logic, but the
Gradle module is an Android one, so the SDK is still required to configure it):

```bash
./gradlew :app:testDebugUnitTest
```

The parser and the FeliCa framing layer have been compiled and unit-tested
(7 tests, all passing). The Compose UI and `MainActivity` have not been compiled,
because building them needs the Android SDK, which wasn't available where this
was written — expect to fix the odd import on first sync.

The wrapper jar is deliberately not committed — `gradle wrapper` or the first
Android Studio sync will create it.

## Using it

1. Switch NFC on.
2. Open the app; it arms NFC reader mode while in the foreground.
3. Hold the card flat against the **upper-middle of the back** of the Pixel 9 —
   that is where the antenna is — and keep it still for a second.

The app also registers for `TECH_DISCOVERED`, so tapping a FeliCa card while the
app is closed offers it as a handler.

Common outcomes:

| What you see | What it means |
| --- | --- |
| "no transit application (system code 0x0003)" | The card is FeliCa but not a transit card — Edy, nanaco, WAON, or a FeliCa Lite tag. |
| "isn't a FeliCa card" | Likely a MIFARE/ISO-14443A card (most hotel keys, many office badges). |
| "Lost the card mid-read" | Moved too early, or the card is off the antenna sweet spot. |
| No history records | Some cards clear the log when reissued. |

## Station names

The card stores region, line and station as three bytes. Mapping them to names
needs the operators' station table, which this app does not ship and cannot
derive. Drop one in at `app/src/main/assets/stations.csv`:

```
region,line,station,operator,lineName,stationName
0x00,0x01,0x01,Example Rail,Example Line,Example Station
```

Numbers can be decimal or `0x`-prefixed hex; `#` starts a comment. Community
projects that maintain these tables (search for "Suica StationCode.csv" or the
tables used by nfcpy/FeliCa reader projects) publish data in roughly this shape;
reorder the columns to match the header above. With no file, the history screen
shows the raw codes, which is why it never shows a wrong station name.

## How it works

Android exposes a FeliCa card as `android.nfc.tech.NfcF`, a raw transport — the
protocol is built by hand in `felica/FelicaTag.kt`. A frame is
`[length][command][payload]`, length inclusive.

1. **Polling (0x00)** with system code `0x0003` picks the transit application
   and returns the IDm to address reads with. A card or phone can hold several
   FeliCa systems, each with its own IDm, so this step is not skippable even
   though Android already handed us *an* ID at discovery.
2. **Read Without Encryption (0x06)** on service `0x008B` returns the attribute
   block: card type at byte 0, balance at bytes 11–12, little-endian.
3. The same command on service `0x090F` returns 20 history blocks of 16 bytes,
   newest first. Reads are chunked four blocks at a time, falling back to single
   blocks if a card rejects the batch.

History block layout (`suica/SuicaCard.kt`):

```
 0      terminal type         (ticket gate, shop, vending machine, …)
 1      process type          (fare payment, charge, purchase, …)
 2      payment type
 3      entry/exit type
 4-5    date: yyyyyyy mmmm ddddd, year counted from 2000
 6-7    entry line + station, or HH:MM for shop/vending records
 8-9    exit line + station
10-11   balance after the transaction, little-endian
12-14   transaction sequence number, big-endian
15      region code
```

The code tables in `suica/SuicaCodes.kt` are community-documented, not official.
Unmapped values are rendered as raw hex rather than guessed at, and the region
byte is only labelled for Kanto/JR East because the published mappings for the
other regions contradict each other.

## Layout

```
app/src/main/java/com/evie/suicareader/
  MainActivity.kt            reader mode, UI state, share sheet
  felica/FelicaTag.kt        FeliCa framing: polling, read without encryption
  felica/Bytes.kt            endian/hex helpers
  suica/CardReader.kt        one tap -> one SuicaCard, or a readable error
  suica/SuicaCard.kt         block parsing and the card model
  suica/SuicaCodes.kt        code -> label tables
  suica/StationDirectory.kt  optional stations.csv lookup
  suica/CsvExport.kt         CSV serialisation
  ui/                        Compose screen and theme
app/src/test/                parser tests over hand-assembled blocks
```
