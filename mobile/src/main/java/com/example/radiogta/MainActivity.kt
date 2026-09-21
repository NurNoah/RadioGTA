package com.example.radiogta

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    private lateinit var statusText: TextView
    private lateinit var chooseFolderButton: Button
    private lateinit var nowPlayingText: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var stationAdapter: StationAdapter

    // IDs der Sender, für die eine Audiodatei gefunden wurde
    private var availableStationIds = emptySet<String>()

    companion object {
        private const val TAG = "MainActivity"
    }

    private val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
            if (treeUri == null) return@registerForActivityResult

            // Zugriff dauerhaft behalten (auch nach Neustart)
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            RadioFiles.setFolder(this, treeUri)
            mediaController?.transportControls
                ?.sendCustomAction(RadioService.ACTION_REFRESH_LIBRARY, null)
            refreshStations()
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "Notification permission granted: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        statusText = findViewById(R.id.statusText)
        chooseFolderButton = findViewById(R.id.chooseFolderButton)
        nowPlayingText = findViewById(R.id.nowPlayingText)
        playPauseButton = findViewById(R.id.playPauseButton)

        stationAdapter = StationAdapter()
        findViewById<ListView>(R.id.stationList).apply {
            adapter = stationAdapter
            setOnItemClickListener { _, _, position, _ ->
                val station = StationManager.stations[position]
                if (station.id in availableStationIds) {
                    mediaController?.transportControls?.playFromMediaId(station.id, null)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.error_station_file_missing,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        chooseFolderButton.setOnClickListener { folderPicker.launch(RadioFiles.getFolder(this)) }
        findViewById<ImageButton>(R.id.previousButton).setOnClickListener {
            mediaController?.transportControls?.skipToPrevious()
        }
        findViewById<ImageButton>(R.id.nextButton).setOnClickListener {
            mediaController?.transportControls?.skipToNext()
        }
        playPauseButton.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            if (controller.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        }

        // MediaBrowser verbinden
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, RadioService::class.java),
            connectionCallbacks,
            null
        )

        requestNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
        // Dateien könnten inzwischen in den Ordner kopiert worden sein
        RadioFiles.invalidate()
        refreshStations()
    }

    override fun onStop() {
        super.onStop()
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Prüft im Hintergrund, für welche Sender eine Datei vorhanden ist
     */
    private fun refreshStations() {
        Thread {
            val available = StationManager.stations
                .filter { RadioFiles.isAvailable(this, it) }
                .map { it.id }
                .toSet()

            runOnUiThread {
                availableStationIds = available
                stationAdapter.notifyDataSetChanged()

                val hasFolder = RadioFiles.getFolder(this) != null
                statusText.text = if (!hasFolder && available.isEmpty()) {
                    getString(R.string.status_no_folder)
                } else {
                    getString(
                        R.string.status_stations_found,
                        available.size,
                        StationManager.stations.size
                    )
                }
                chooseFolderButton.setText(
                    if (hasFolder) R.string.change_folder else R.string.choose_folder
                )
            }
        }.start()
    }

    private inner class StationAdapter : ArrayAdapter<RadioStation>(
        this@MainActivity,
        android.R.layout.simple_list_item_2,
        android.R.id.text1,
        StationManager.stations
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            val station = StationManager.stations[position]
            val available = station.id in availableStationIds

            view.findViewById<TextView>(android.R.id.text1).text = station.name
            view.findViewById<TextView>(android.R.id.text2).text = if (available) {
                station.genre
            } else {
                getString(R.string.station_file_missing, station.genre, station.assetFileName)
            }
            view.alpha = if (available) 1.0f else 0.4f
            return view
        }
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.d(TAG, "MediaBrowser connected")

            mediaBrowser.sessionToken.also { token ->
                mediaController = MediaControllerCompat(this@MainActivity, token)
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

                // Callback registrieren
                mediaController?.registerCallback(controllerCallback)
                controllerCallback.onMetadataChanged(mediaController?.metadata)
                controllerCallback.onPlaybackStateChanged(mediaController?.playbackState)

                Log.d(TAG, "MediaController ready. Current state: ${mediaController?.playbackState?.state}")
            }
        }

        override fun onConnectionSuspended() {
            Log.d(TAG, "MediaBrowser connection suspended")
        }

        override fun onConnectionFailed() {
            Log.e(TAG, "MediaBrowser connection failed")
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            Log.d(TAG, "Playback state changed: ${state?.state}")

            val isPlaying = state?.state == PlaybackStateCompat.STATE_PLAYING
            playPauseButton.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            playPauseButton.contentDescription =
                getString(if (isPlaying) R.string.pause else R.string.play)

            if (state?.state == PlaybackStateCompat.STATE_ERROR && state.errorMessage != null) {
                Toast.makeText(this@MainActivity, state.errorMessage, Toast.LENGTH_LONG).show()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            val title = metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            val genre = metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            nowPlayingText.text = if (title != null) {
                "$title · $genre"
            } else {
                getString(R.string.now_playing_nothing)
            }
        }
    }
}
