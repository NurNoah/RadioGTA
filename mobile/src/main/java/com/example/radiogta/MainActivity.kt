package com.example.radiogta

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var mediaBrowser: MediaBrowserCompat
    private var mediaController: MediaControllerCompat? = null

    companion object {
        private const val TAG = "MainActivity"
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

        // MediaBrowser verbinden
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, RadioService::class.java),
            connectionCallbacks,
            null
        )
    }

    override fun onStart() {
        super.onStart()
        mediaBrowser.connect()
    }

    override fun onStop() {
        super.onStop()
        mediaController?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }

    private val connectionCallbacks = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            Log.d(TAG, "MediaBrowser connected")

            mediaBrowser.sessionToken.also { token ->
                mediaController = MediaControllerCompat(this@MainActivity, token)
                MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

                // Callback registrieren
                mediaController?.registerCallback(controllerCallback)

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
        }
    }
}