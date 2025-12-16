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
import android.net.Uri
import android.util.Log
import java.io.File

class RadioService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var exoPlayer: ExoPlayer

    private val MY_MEDIA_ROOT_ID = "root_media"
    private val MY_FAVORITES_ID = "root_favorites"

    companion object {
        private const val TAG = "RadioService"
    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "GTARadioService").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(mediaSessionCallback)
            setSessionToken(sessionToken)
            isActive = true
        }

        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(playerListener)
    }

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
            // TODO: Zum nächsten Sender springen
        }

        override fun onSkipToPrevious() {
            // TODO: Zum vorherigen Sender springen
        }
    }

    private fun playStation(station: RadioStation) {
        Log.d(TAG, "Attempting to play station: ${station.name}")

        // Hole die Datei aus der OBB (wird beim ersten Mal extrahiert und gecacht)
        val localFile = ObbHelper.getRadioFile(this, station.assetFileName)

        if (localFile == null || !localFile.exists()) {
            Log.e(TAG, "Could not load radio file: ${station.assetFileName}")
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }

        Log.d(TAG, "Playing from file: ${localFile.absolutePath}")

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
                val duration = exoPlayer.duration
                val seekPosition = StationManager.getSimulatedPosition(duration)

                if (exoPlayer.currentPosition < 1000) {
                    exoPlayer.seekTo(seekPosition)
                }

                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
            } else if (playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
                exoPlayer.play()
            } else if (playbackState == Player.STATE_IDLE) {
                updatePlaybackState(PlaybackStateCompat.STATE_NONE)
            }
        }

        override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        }
    }

    private fun getAlbumArtBitmapFromFile(file: File): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture
            if (art != null && art.isNotEmpty()) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting album art", e)
            null
        } finally {
            retriever.release()
        }
    }

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
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            )
            .setState(state, exoPlayer.currentPosition, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        val mediaItems = mutableListOf<MediaItem>()

        if (parentId == MY_MEDIA_ROOT_ID) {
            mediaItems.add(createBrowsableItem(MY_FAVORITES_ID, "Favoriten", "Deine Top 4"))

            StationManager.stations.forEach { station ->
                mediaItems.add(createPlayableItem(station))
            }
        } else if (parentId == MY_FAVORITES_ID) {
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