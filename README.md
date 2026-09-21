# RadioGTA 📻

Die Radiosender aus GTA V als Android-App – mit **Android Auto**, Benachrichtigungs-Steuerung und Bluetooth-/Kopfhörer-Tasten.

Das Besondere: Die Sender laufen wie echtes Radio. Jeder Sender läuft "im Hintergrund" weiter – wenn du umschaltest oder die App später wieder öffnest, bist du mitten in der Sendung statt wieder am Anfang.

---

## Installation in 3 Schritten

### 1. App installieren

**[⬇️ RadioGTA.apk herunterladen](https://github.com/NurNoah/RadioGTA/releases/latest/download/RadioGTA.apk)** (neueste Version, ca. 8 MB)

1. Link oben direkt auf dem Handy öffnen und die APK herunterladen.
2. Die heruntergeladene Datei antippen.
3. Falls Android fragt: **"Aus dieser Quelle zulassen"** aktivieren (Installation unbekannter Apps) und dann auf **Installieren** tippen.

> Voraussetzung: Android 8.0 oder neuer. Alle Versionen findest du unter [Releases](https://github.com/NurNoah/RadioGTA/releases).

### 2. Sender-Dateien aufs Handy kopieren

Lege auf dem Handy einen Ordner an, z. B. `Music/GTARadio`, und kopiere deine Sender-Dateien hinein (per USB-Kabel, Cloud, Quick Share – egal wie). Pro Sender eine Datei, benannt wie in der [Tabelle unten](#dateinamen-der-sender).

> ⚠️ Nimm **nicht** den `Download`-Ordner selbst – Android erlaubt Apps keinen Zugriff auf den ganzen Download-Ordner. Ein Unterordner (z. B. `Download/GTARadio`) oder `Music/GTARadio` funktioniert.

### 3. Ordner in der App auswählen

1. **RadioGTA** öffnen.
2. Auf **"MP3-Ordner auswählen"** tippen.
3. Zu deinem Ordner navigieren → **"Diesen Ordner verwenden"** → **"Zulassen"**.

Fertig! Die App zeigt an, wie viele Sender gefunden wurden (z. B. *"20 von 20 Sendern gefunden"*). Tippe einen Sender an und es geht los. Kein PC, kein adb, keine OBB-Dateien nötig.

Du musst nicht alle Sender haben – fehlende werden ausgegraut und in Android Auto ausgeblendet. Wenn du später Dateien ergänzt, erkennt die App sie beim nächsten Öffnen automatisch.

---

## Woher kommen die Audiodateien?

**Die Audiodateien sind aus rechtlichen Gründen nicht Teil dieses Projekts und werden hier nicht zum Download angeboten.** Die Radiosender enthalten lizenzierte Musik; die Rechte liegen bei Rockstar Games / Take-Two Interactive und den jeweiligen Künstlern und Labels.

Du brauchst also eigene Dateien, die du rechtmäßig besitzt. Technisch ist die App ein ganz normaler Player: Du kannst jede beliebige lange Audiodatei (eigener Mix, Podcast, …) unter einem der Sendernamen ablegen.

### Dateinamen der Sender

| Sender | Genre | Dateiname |
|---|---|---|
| Los Santos Rock Radio | Classic Rock | `los_santos_rock_radio.mp3` |
| Non-Stop-Pop FM | Pop / Dance | `non_stop_pop_fm.mp3` |
| West Coast Classics | Old School Hip Hop | `west_coast_classics.mp3` |
| Vinewood Boulevard Radio | Alternative Rock | `vinewood_boulevard_radio.mp3` |
| Space 103.2 | Funk | `space_103_2.mp3` |
| Blonded Radio | Soul / R&B / Rap | `blonded_radio.mp3` |
| Blue Ark | Reggae / Dancehall | `blue_ark.mp3` |
| Channel X | Punk Rock | `channel_x.mp3` |
| East Los FM | Mexican Electronica | `east_los_fm.mp3` |
| FlyLo FM | IDM / Experimental | `flylo_fm.mp3` |
| LS Underground Radio | House / Techno | `los_santos_underground_radio.mp3` |
| Radio Los Santos | Modern Hip Hop | `radio_los_santos.mp3` |
| Radio Mirror Park | Indie Pop | `radio_mirror_park.mp3` |
| Rebel Radio | Country | `rebel_radio.mp3` |
| Soulwax FM | Techno | `soulwax_fm.mp3` |
| The Lab | Hip Hop / Synth | `the_lab.mp3` |
| The Lowdown 91.1 | Classic Soul | `the_lowdown_91_1.mp3` |
| WorldWide FM | Chillwave / Jazz | `worldwide_fm.mp3` |
| Blaine County Radio | Talk Radio | `blaine_county_radio.mp3` |
| GTA V Commercials | Satire / Ads | `gta_v_radio_commercials.mp3` |

Die App ist beim Namen tolerant: Groß-/Kleinschreibung, Leerzeichen, Bindestriche, Punkte und Unterstriche sind egal. `Non-Stop-Pop FM.mp3` wird genauso erkannt wie `non_stop_pop_fm.mp3`. Neben `.mp3` funktionieren auch `.m4a`, `.aac`, `.ogg`, `.opus`, `.flac` und `.wav`.

Ist in der Datei ein Cover eingebettet, wird es als Sender-Logo in der Benachrichtigung und in Android Auto angezeigt.

---

## Android Auto

Weil die App nicht aus dem Play Store kommt, muss Android Auto sie einmalig freischalten:

1. Auf dem Handy **Einstellungen → Verbundene Geräte → Android Auto** öffnen (oder nach "Android Auto" suchen).
2. Ganz nach unten scrollen und **10× auf "Version"** tippen → Entwicklereinstellungen bestätigen.
3. Oben rechts **⋮ → Entwicklereinstellungen** öffnen.
4. **"Unbekannte Quellen"** aktivieren.
5. Handy neu mit dem Auto verbinden – RadioGTA erscheint bei den Medien-Apps. Falls nicht: in den Android-Auto-Einstellungen unter **"Apps anpassen"** den Haken bei RadioGTA setzen.

Tipp: Öffne die App einmal auf dem Handy und wähle den Ordner aus, **bevor** du ins Auto steigst.

---

## Problemlösung

| Problem | Lösung |
|---|---|
| "0 von 20 Sendern gefunden" | Falscher Ordner gewählt oder Dateinamen passen nicht zur Tabelle. Die Dateien müssen direkt im gewählten Ordner liegen (nicht in Unterordnern). |
| Ordner lässt sich nicht auswählen ("Diesen Ordner verwenden" ist grau) | Android sperrt den `Download`-Hauptordner und das Speicher-Hauptverzeichnis. Unterordner anlegen, z. B. `Music/GTARadio`. |
| App lässt sich nicht installieren | "Installation unbekannter Apps" für deinen Browser/Dateimanager erlauben. Bei *"App nicht installiert"*: eine ältere, anders signierte Version (z. B. aus Android Studio) erst deinstallieren. |
| Play Protect warnt | Normal bei Apps außerhalb des Play Stores → "Trotzdem installieren". |
| Keine Steuerung in der Benachrichtigung | Benachrichtigungen für RadioGTA erlauben (wird beim ersten Start abgefragt). |
| App taucht in Android Auto nicht auf | "Unbekannte Quellen" aktivieren, siehe [Android Auto](#android-auto). |
| Wiedergabe stoppt nach einiger Zeit im Hintergrund | Akku-Optimierung für RadioGTA deaktivieren (Einstellungen → Apps → RadioGTA → Akku → "Nicht eingeschränkt"). |

---

## Für Entwickler

```bash
git clone https://github.com/NurNoah/RadioGTA.git
cd RadioGTA
./gradlew :mobile:assembleRelease
```

Die APK liegt danach unter `mobile/build/outputs/apk/release/`. Benötigt wird JDK 17–21 (z. B. das JBR von Android Studio).

- **Signierung:** Liegt im Projektordner eine `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`), wird damit signiert – sonst mit dem Debug-Key. Keystore und Properties sind in `.gitignore` und gehören nicht ins Repo.
- **Aufbau:** `RadioService` (MediaBrowserService + ExoPlayer + Audio-Focus), `RadioFiles` (findet die Dateien im gewählten Ordner via Storage Access Framework), `StationManager` (Senderliste + simulierte Live-Position), `MainActivity` (Ordnerauswahl + Senderliste).
- **OBB (alt):** Frühere Versionen haben die Sender aus OBB-Dateien gelesen (`create_obb.py`). Das funktioniert als Fallback weiterhin, der gewählte Ordner hat aber Vorrang.

---

## Rechtliches

Inoffizielles Fan-Projekt, nicht mit Rockstar Games oder Take-Two Interactive verbunden oder von ihnen unterstützt. "Grand Theft Auto" und alle Sendernamen sind Marken ihrer jeweiligen Inhaber. Dieses Repository enthält ausschließlich den Quellcode der App und **keine** Audioinhalte.
