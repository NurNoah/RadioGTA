package com.example.radiogta

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
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
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentStationIndex = 0
    private var isMuted = false
    private var hasAudioFocus = false
    private var isAudioFocusRequestPending = false
    private var playbackRequested = false
    private var resumeOnAudioFocusGain = false
    private var isDucked = false
    private var pendingStation: RadioStation? = null
    private var pendingStationFile: File? = null

    private val MY_MEDIA_ROOT_ID = "root_media"
    private val MY_FAVORITES_ID = "root_favorites"
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID = "radio_playback_channel"

    // Cache für Album-Arts
    private val albumArtCache = mutableMapOf<String, Bitmap>()

    companion object {
        private const val TAG = "RadioService"
    }

    override fun onCreate() {
        super.onCreate()

        // Notification Channel erstellen
        createNotificationChannel()

        // AudioManager initialisieren
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Gespeicherte Position wiederherstellen
        StationManager.restorePlaybackPosition(this)

        mediaSession = MediaSessionCompat(this, "GTARadioService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(mediaSessionCallback)
            setSessionToken(sessionToken)
            isActive = true
        }

        // ExoPlayer mit AudioAttributes erstellen - KEIN Auto-Handling!
        val audioAttributes = com.google.android.exoplayer2.audio.AudioAttributes.Builder()
            .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
            .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, false) // handleAudioFocus = FALSE! Machen wir selbst
            .build()
        exoPlayer.addListener(playerListener)

        // Letzten Sender für Android Auto anzeigen. Die Wiedergabe wird erst durch einen
        // onPlay-/onPlayFromMediaId-Aufruf gestartet. So wird Audio Focus nicht schon beim
        // bloßen Verbinden des Fahrzeugs angefordert.
        val lastStation = StationManager.getLastStation(this)
        currentStationIndex = StationManager.stations.indexOf(lastStation)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        updateMetadata(
            lastStation,
            ObbHelper.getRadioFile(this, lastStation.assetFileName),
            showNotification = false
        )
        Log.d(TAG, "Ready with last station: ${lastStation.name}")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Radio playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestAudioFocus(): Int {
        if (hasAudioFocus) {
            Log.d(TAG, "Already have audio focus")
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (isAudioFocusRequestPending) {
            Log.d(TAG, "Audio focus request is already pending")
            return AudioManager.AUDIOFOCUS_REQUEST_DELAYED
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        isAudioFocusRequestPending = result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
        Log.d(TAG, "Audio focus request result: $result")
        return result
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus && !isAudioFocusRequestPending) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
        isAudioFocusRequestPending = false
        Log.d(TAG, "Audio focus abandoned")
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus changed: $focusChange")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                isAudioFocusRequestPending = false
                val shouldResume = resumeOnAudioFocusGain || isMuted || isDucked
                isDucked = false

                if (pendingStation != null && playbackRequested) {
                    startPendingStation()
                } else if (playbackRequested && shouldResume) {
                    isMuted = false
                    exoPlayer.volume = 1.0f
                    exoPlayer.play()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                    updateNotification()
                }
                resumeOnAudioFocusGain = false
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                isAudioFocusRequestPending = false
                resumeOnAudioFocusGain = false
                playbackRequested = false
                pendingStation = null
                pendingStationFile = null
                pauseForAudioFocusLoss()
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                isAudioFocusRequestPending = false
                resumeOnAudioFocusGain = playbackRequested
                pauseForAudioFocusLoss()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                isDucked = true
                exoPlayer.volume = 0.3f
            }
        }
    }

    private fun pauseForAudioFocusLoss() {
        isMuted = true
        exoPlayer.volume = 0.0f
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        updateNotification()
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {

        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            Log.d(TAG, "onPlayFromMediaId: $mediaId")
            mediaId?.let { id ->
                val station = StationManager.getStationById(id)
                if (station != null) {
                    currentStationIndex = StationManager.stations.indexOf(station)
                    playStation(station)
                }
            }
        }

        override fun onPlay() {
            Log.d(TAG, "onPlay called")

            if (exoPlayer.currentMediaItem == null) {
                // Wenn noch nichts geladen ist, letzten Sender laden
                val lastStation = StationManager.getLastStation(this@RadioService)
                currentStationIndex = StationManager.stations.indexOf(lastStation)
                playStation(lastStation)
            } else {
                playbackRequested = true
                resumeOnAudioFocusGain = true

                // Ab Android 15 darf Audio Focus nur als Top-App oder aus einem laufenden
                // Foreground Service angefordert werden. Deshalb zuerst die Media-
                // Benachrichtigung aktivieren und anschließend Focus anfordern.
                updateNotification()
                when (requestAudioFocus()) {
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> resumeCurrentStation()
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                        isMuted = true
                        exoPlayer.volume = 0.0f
                        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                        updateNotification()
                    }
                    else -> handleAudioFocusFailure()
                }
            }
        }

        override fun onPause() {
            Log.d(TAG, "onPause called")
            // Pause = Mute (nicht komplett stoppen)
            playbackRequested = false
            resumeOnAudioFocusGain = false
            pendingStation = null
            pendingStationFile = null
            isMuted = true
            exoPlayer.volume = 0.0f
            abandonAudioFocus()

            // Position speichern
            StationManager.savePlaybackPosition(this@RadioService)

            updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
            updateNotification()
            stopForeground(STOP_FOREGROUND_DETACH)
        }

        override fun onSkipToNext() {
            Log.d(TAG, "onSkipToNext called")
            // Nächster Sender
            currentStationIndex = (currentStationIndex + 1) % StationManager.stations.size
            playStation(StationManager.stations[currentStationIndex])
        }

        override fun onSkipToPrevious() {
            Log.d(TAG, "onSkipToPrevious called")
            // Vorheriger Sender
            currentStationIndex = if (currentStationIndex - 1 < 0) {
                StationManager.stations.size - 1
            } else {
                currentStationIndex - 1
            }
            playStation(StationManager.stations[currentStationIndex])
        }

        override fun onStop() {
            Log.d(TAG, "onStop called")
            // Position speichern bevor wir stoppen
            StationManager.savePlaybackPosition(this@RadioService)

            playbackRequested = false
            resumeOnAudioFocusGain = false
            pendingStation = null
            pendingStationFile = null
            exoPlayer.stop()
            abandonAudioFocus()
            updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun playStation(station: RadioStation) {
        Log.d(TAG, "=== playStation START: ${station.name} ===")

        // Sender speichern
        StationManager.saveCurrentStation(this, station.id)

        // Hole die Datei aus der OBB
        val localFile = ObbHelper.getRadioFile(this, station.assetFileName)

        if (localFile == null || !localFile.exists()) {
            Log.e(TAG, "Could not load radio file: ${station.assetFileName}")
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            return
        }

        Log.d(TAG, "Playing from file: ${localFile.absolutePath}")
        Log.d(TAG, "File size: ${localFile.length()} bytes")

        playbackRequested = true
        resumeOnAudioFocusGain = true
        pendingStation = station
        pendingStationFile = localFile
        isMuted = true
        exoPlayer.volume = 0.0f

        // startForeground muss vor requestAudioFocus passieren, sonst wird die Focus-
        // Anfrage bei targetSdk 35+ abgelehnt, wenn Android Auto im Vordergrund ist.
        updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
        updateMetadata(station, localFile)

        when (requestAudioFocus()) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> startPendingStation()
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                Log.d(TAG, "Waiting for delayed audio focus before starting playback")
            }
            else -> handleAudioFocusFailure()
        }

        Log.d(TAG, "=== playStation END ===")
    }

    private fun startPendingStation() {
        pendingStation ?: return
        val localFile = pendingStationFile ?: return
        pendingStation = null
        pendingStationFile = null
        resumeOnAudioFocusGain = false
        isMuted = false
        isDucked = false
        exoPlayer.volume = 1.0f

        val uri = Uri.fromFile(localFile)
        val mediaItem = ExoMediaItem.fromUri(uri)

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        Log.d(TAG, "ExoPlayer prepared and play requested")
    }

    private fun resumeCurrentStation() {
        resumeOnAudioFocusGain = false
        isMuted = false
        isDucked = false
        exoPlayer.volume = 1.0f
        exoPlayer.play()
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateNotification()
    }

    private fun handleAudioFocusFailure() {
        Log.e(TAG, "Audio focus request failed")
        playbackRequested = false
        resumeOnAudioFocusGain = false
        pendingStation = null
        pendingStationFile = null
        isMuted = true
        exoPlayer.volume = 0.0f
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        updatePlaybackState(
            PlaybackStateCompat.STATE_ERROR,
            "Audioausgabe konnte nicht gestartet werden"
        )
        updateNotification()
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when(playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "ExoPlayer state changed: $stateName, playWhenReady: ${exoPlayer.playWhenReady}")

            if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                val duration = exoPlayer.duration
                val seekPosition = StationManager.getSimulatedPosition(duration)

                // Zur berechneten Position springen
                if (exoPlayer.currentPosition < 1000 ||
                    kotlin.math.abs(exoPlayer.currentPosition - seekPosition) > 2000) {
                    exoPlayer.seekTo(seekPosition)
                    Log.d(TAG, "Seeked to position: ${seekPosition}ms (duration: ${duration}ms)")
                }

                val state = if (isMuted) PlaybackStateCompat.STATE_PAUSED else PlaybackStateCompat.STATE_PLAYING
                updatePlaybackState(state)
                updateNotification()
            } else if (playbackState == Player.STATE_ENDED) {
                // Loop
                exoPlayer.seekTo(0)
                exoPlayer.play()
            } else if (playbackState == Player.STATE_IDLE) {
                updatePlaybackState(PlaybackStateCompat.STATE_NONE)
            }
        }

        override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
            Log.e(TAG, "Player error: ${error.message}", error)
            Log.e(TAG, "Error cause: ${error.cause}")
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: $isPlaying")
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

    private fun updateMetadata(
        station: RadioStation,
        localFile: File? = null,
        showNotification: Boolean = true
    ) {
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
        if (showNotification) {
            updateNotification()
        }
    }

    private fun updatePlaybackState(state: Int, errorMessage: String? = null) {
        val stateName = when(state) {
            PlaybackStateCompat.STATE_PLAYING -> "PLAYING"
            PlaybackStateCompat.STATE_PAUSED -> "PAUSED"
            PlaybackStateCompat.STATE_STOPPED -> "STOPPED"
            PlaybackStateCompat.STATE_ERROR -> "ERROR"
            else -> "OTHER($state)"
        }
        Log.d(TAG, "updatePlaybackState: $stateName")

        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, exoPlayer.currentPosition, 1.0f)

        if (state == PlaybackStateCompat.STATE_ERROR && errorMessage != null) {
            playbackStateBuilder.setErrorMessage(
                PlaybackStateCompat.ERROR_CODE_APP_ERROR,
                errorMessage
            )
        }
        mediaSession.setPlaybackState(playbackStateBuilder.build())
    }

    private fun updateNotification() {
        val metadata = mediaSession.controller.metadata
        if (metadata == null) {
            Log.d(TAG, "No metadata available for notification")
            return
        }

        val description = metadata.description
        val state = mediaSession.controller.playbackState?.state ?: PlaybackStateCompat.STATE_NONE

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(description.title)
            .setContentText(description.subtitle)
            .setSubText(description.description)
            .setLargeIcon(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(mediaSession.controller.sessionActivity)
            .setDeleteIntent(
                androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_STOP
                )
            )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_previous,
                    "Previous",
                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                )
            )
            .addAction(
                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_pause,
                        "Pause",
                        androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this,
                            PlaybackStateCompat.ACTION_PAUSE
                        )
                    )
                } else {
                    NotificationCompat.Action(
                        android.R.drawable.ic_media_play,
                        "Play",
                        androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                            this,
                            PlaybackStateCompat.ACTION_PLAY
                        )
                    )
                }
            )
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_next,
                    "Next",
                    androidx.media.session.MediaButtonReceiver.buildMediaButtonPendingIntent(
                        this,
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    )
                )
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        // Result detachen für asynchrones Laden
        result.detach()

        // Im Hintergrund laden
        Thread {
            val mediaItems = mutableListOf<MediaItem>()

            if (parentId == MY_MEDIA_ROOT_ID) {
                mediaItems.add(createBrowsableItem(MY_FAVORITES_ID, "Favoriten", "Deine Top 4", null))

                StationManager.stations.forEach { station ->
                    val albumArt = getOrLoadAlbumArt(station)
                    mediaItems.add(createPlayableItem(station, albumArt))
                }
            } else if (parentId == MY_FAVORITES_ID) {
                StationManager.favoriteIds.forEach { favId ->
                    val station = StationManager.getStationById(favId)
                    if (station != null) {
                        val albumArt = getOrLoadAlbumArt(station)
                        mediaItems.add(createPlayableItem(station, albumArt))
                    }
                }
            }

            result.sendResult(mediaItems)
        }.start()
    }

    private fun getOrLoadAlbumArt(station: RadioStation): Bitmap? {
        // Prüfe Cache
        albumArtCache[station.id]?.let { return it }

        // Lade aus Datei
        val localFile = ObbHelper.getRadioFile(this, station.assetFileName)
        if (localFile != null && localFile.exists()) {
            val bitmap = getAlbumArtBitmapFromFile(localFile)
            if (bitmap != null) {
                albumArtCache[station.id] = bitmap
                return bitmap
            }
        }

        return null
    }

    private fun createPlayableItem(station: RadioStation, albumArt: Bitmap? = null): MediaItem {
        val descBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(station.id)
            .setTitle(station.name)
            .setSubtitle(station.genre)

        // Album-Art setzen wenn vorhanden
        if (albumArt != null) {
            descBuilder.setIconBitmap(albumArt)
        }

        return MediaItem(descBuilder.build(), MediaItem.FLAG_PLAYABLE)
    }

    private fun createBrowsableItem(id: String, title: String, subtitle: String, icon: Bitmap? = null): MediaItem {
        val descBuilder = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)

        if (icon != null) {
            descBuilder.setIconBitmap(icon)
        }

        return MediaItem(descBuilder.build(), MediaItem.FLAG_BROWSABLE)
    }

    override fun onDestroy() {
        // Position speichern bevor Service zerstört wird
        StationManager.savePlaybackPosition(this)

        abandonAudioFocus()
        mediaSession.release()
        exoPlayer.release()
        super.onDestroy()
    }
}
