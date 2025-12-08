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
import android.content.Context
import android.os.Environment
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

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

    // Kopiert eine Asset-Datei in das app-spezifische Music-Verzeichnis (persistiert).
    fun ensureAssetCopiedToExternalMusic(context: Context, assetRelativePath: String): File {
        val destDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        if (!destDir.exists()) destDir.mkdirs()

        val fileName = assetRelativePath.substringAfterLast('/') // z.B. "los_santos_underground_radio.mp3"
        val outFile = File(destDir, fileName)
        if (outFile.exists()) return outFile

        context.assets.open(assetRelativePath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    // Alternative: kurzlebig in cache (nur für Testzwecke)
    fun ensureAssetCopiedToCache(context: Context, assetRelativePath: String): File {
        val outFile = File(context.cacheDir, assetRelativePath.substringAfterLast('/'))
        if (outFile.exists()) return outFile

        // Stelle sicher, dass ggf. Unterordner in cache existieren (normal nicht nötig)
        outFile.parentFile?.mkdirs()

        context.assets.open(assetRelativePath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun playStation(station: RadioStation) {
        // Wenn du assets/radio/... verwendet hast:
        val assetPath = "radio/${station.assetFileName}" // z.B. "radio/blonded_radio.mp3"

        // Kopiere Asset in persistenten App-Ordner (einmalig). Nutze externalFilesDir, weil die Dateien groß sind.
        val localFile = try {
            ensureAssetCopiedToExternalMusic(this, assetPath)
        } catch (e: Exception) {
            // Fallback: cache (nur falls external nicht geht)
            ensureAssetCopiedToCache(this, assetPath)
        }

        // Erstelle MediaItem aus lokalem File und spiele mit ExoPlayer
        val uri = Uri.fromFile(localFile)
        val mediaItem = ExoMediaItem.fromUri(uri)

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        updateMetadata(station, localFile)
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

    private fun getAlbumArtBitmapFromFile(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture
            if (art != null && art.isNotEmpty()) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else null
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    // Update updateMetadata: bekommt die lokale Datei (falls vorhanden)
    private fun updateMetadata(station: RadioStation, localFile: File? = null) {
        val albumArtBitmap = localFile?.let { getAlbumArtBitmapFromFile(it) }

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, station.id)
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, station.name)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.genre)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Grand Theft Auto V Radio")

        if (albumArtBitmap != null) {
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