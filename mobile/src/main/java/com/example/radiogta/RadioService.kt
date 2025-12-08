package com.example.radiogta

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem as ExoMediaItem
import com.google.android.exoplayer2.Player
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever

class RadioService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: ExoPlayer

    // IDs für die Ordner-Struktur
    private val MY_MEDIA_ROOT_ID = "root_media"
    private val MY_FAVORITES_ID = "root_favorites"

    override fun onCreate() {
        super.onCreate()

        // 1. MediaSession erstellen
        mediaSession = MediaSessionCompat(this, "GTARadioService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(mediaSessionCallback)
            setSessionToken(sessionToken)
            isActive = true
        }

        // 2. ExoPlayer initialisieren
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(playerListener)
    }

    // --- Die Logik zum Abspielen ---
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            mediaId?.let { id ->
                val station = StationManager.getStationById(id) ?: return
                playStation(station)
            }
        }

        override fun onPlay() {
            exoPlayer.play()
        }

        override fun onPause() {
            exoPlayer.pause()
        }

        override fun onSkipToNext() {
            // Logik um zum nächsten Sender in der Liste zu springen
        }

        override fun onSkipToPrevious() {
            // Logik um zum vorherigen Sender zu springen
        }
    }

    private fun playStation(station: RadioStation) {
        // Hier laden wir die Datei aus dem RAW ordner oder Assets
        // Für dieses Beispiel gehen wir davon aus, dass die Datei im "raw" Ordner liegt
        // Format: android.resource://paketname/raw/dateiname_ohne_endung

        // Achtung: Dateinamen müssen in StationManager ohne .mp3 Endung für Raw Resources sein,
        // oder du baust den Pfad für Assets anders. Hier Beispiel für Raw Resource:
        val resourceId = resources.getIdentifier(station.assetFileName.replace(".mp3", ""), "raw", packageName)
        val uri = android.net.Uri.parse("android.resource://$packageName/$resourceId")

        val mediaItem = ExoMediaItem.fromUri(uri)

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()

        // TRICK: Wir warten kurz bis die Duration bekannt ist, oder schätzen sie,
        // aber ExoPlayer kennt die Duration erst nach prepare().
        // Wir setzen playWhenReady = true, der Listener unten regelt den Seek.
        exoPlayer.playWhenReady = true

        updateMetadata(station)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                // JETZT simulieren wir, dass das Radio schon lief
                val duration = exoPlayer.duration
                val seekPosition = StationManager.getSimulatedPosition(duration)

                // Nur seeken, wenn wir ganz am Anfang sind (damit es nicht springt wenn man Pause/Play drückt)
                if (exoPlayer.currentPosition < 1000) {
                    exoPlayer.seekTo(seekPosition)
                }

                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            } else if (playbackState == Player.STATE_ENDED) {
                // Loop: Wenn Datei zu Ende, fang von vorne an (passiert automatisch durch Simulation beim nächsten Start nicht, aber hier wichtig)
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
        }
    }

// In RadioService.kt

    // Neue Hilfsfunktion, um die Metadaten zu extrahieren
    private fun getAlbumArtBitmap(station: RadioStation): Bitmap? {
        // 1. Pfad zur RAW-Ressource ermitteln
        val resourceId = resources.getIdentifier(station.assetFileName.replace(".mp3", ""), "raw", packageName)
        if (resourceId == 0) return null

        // 2. URI erstellen
        val uri = android.net.Uri.parse("android.resource://$packageName/$resourceId")

        val retriever = MediaMetadataRetriever()
        return try {
            // 3. Datei über URI an den Retriever übergeben
            retriever.setDataSource(this, uri)

            // 4. Eingebettetes Bild-Byte-Array holen
            val art = retriever.embeddedPicture

            // 5. Byte-Array zu Bitmap dekodieren
            if (art != null && art.isNotEmpty()) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                null
            }
        } catch (e: Exception) {
            // Fehlerbehandlung
            null
        } finally {
            retriever.release()
        }
    }

    // Aktualisierte Funktion, die die Bitmap in die MediaSession lädt
    private fun updateMetadata(station: RadioStation) {

        // Zuerst das Bild laden
        val albumArtBitmap = getAlbumArtBitmap(station)

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, station.id)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, station.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.genre)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Grand Theft Auto V Radio")

        if (albumArtBitmap != null) {
            // WICHTIG: Das Bild wird in zwei Schlüsseln gespeichert,
            // damit es von Android Auto (Media Session) korrekt verwendet wird
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArtBitmap)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, albumArtBitmap)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun updatePlaybackState(state: Int) {
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            .setState(state, exoPlayer.currentPosition, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    // --- Browser Struktur (Das Menü im Auto) ---
    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // Erlaubt jedem Auto/Handy Zugriff
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        val mediaItems = mutableListOf<MediaItem>()

        if (parentId == MY_MEDIA_ROOT_ID) {
            // Hauptmenü Ordner
            mediaItems.add(createBrowsableItem(MY_FAVORITES_ID, "Favoriten", "Deine Top 4"))

            // Liste aller Sender direkt anzeigen
            StationManager.stations.forEach { station ->
                mediaItems.add(createPlayableItem(station))
            }
        } else if (parentId == MY_FAVORITES_ID) {
            // Nur Favoriten laden
            StationManager.favoriteIds.forEach { favId ->
                val station = StationManager.getStationById(favId)
                if (station != null) {
                    mediaItems.add(createPlayableItem(station))
                }
            }
        }

        result.sendResult(mediaItems)
    }

    private fun createPlayableItem(station: RadioStation): MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(station.id)
            .setTitle(station.name)
            .setSubtitle(station.genre)
            // .setIconBitmap(...) // Hier Cover Art setzen für die Liste
            .build()
        return MediaItem(desc, MediaItem.FLAG_PLAYABLE)
    }

    private fun createBrowsableItem(id: String, title: String, subtitle: String): MediaItem {
        val desc = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaItem(desc, MediaItem.FLAG_BROWSABLE)
    }

    override fun onDestroy() {
        mediaSession.release()
        exoPlayer.release()
        super.onDestroy()
    }
}