package com.example.radiogta

import android.content.Context
import android.util.Log

data class RadioStation(
    val id: String,
    val name: String,
    val genre: String,
    val assetFileName: String
)

object StationManager {

    private const val TAG = "StationManager"
    private const val PREFS_NAME = "radio_prefs"
    private const val KEY_LAST_TIMESTAMP = "last_timestamp"
    private const val KEY_GLOBAL_START_TIME = "global_start_time"
    private const val KEY_LAST_STATION_ID = "last_station_id"

    // Globaler Radio-Start-Zeitpunkt (wird einmal beim ersten Start gesetzt)
    private var globalRadioStartTime = System.currentTimeMillis()

    val stations = listOf(
        // --- Music Stations ---
        RadioStation("los_santos_rock", "Los Santos Rock Radio", "Classic Rock", "los_santos_rock_radio.mp3"),
        RadioStation("non_stop_pop", "Non-Stop-Pop FM", "Pop / Dance", "non_stop_pop_fm.mp3"),
        RadioStation("west_coast_classics", "West Coast Classics", "Old School Hip Hop", "west_coast_classics.mp3"),
        RadioStation("vinewood_blvd", "Vinewood Boulevard Radio", "Alternative Rock", "vinewood_boulevard_radio.mp3"),
        RadioStation("space_103_2", "Space 103.2", "Funk", "space_103_2.mp3"),
        RadioStation("blonded_radio", "Blonded Radio", "Soul / R&B / Rap", "blonded_radio.mp3"),
        RadioStation("blue_ark", "Blue Ark", "Reggae / Dancehall", "blue_ark.mp3"),
        RadioStation("channel_x", "Channel X", "Punk Rock", "channel_x.mp3"),
        RadioStation("east_los_fm", "East Los FM", "Mexican Electronica", "east_los_fm.mp3"),
        RadioStation("flylo_fm", "FlyLo FM", "IDM / Experimental", "flylo_fm.mp3"),
        RadioStation("los_santos_underground", "LS Underground Radio", "House / Techno", "los_santos_underground_radio.mp3"),
        RadioStation("non_stop_pop", "Non-Stop-Pop FM", "Pop / Dance", "non_stop_pop_fm.mp3"),
        RadioStation("radio_los_santos", "Radio Los Santos", "Modern Hip Hop", "radio_los_santos.mp3"),
        RadioStation("radio_mirror_park", "Radio Mirror Park", "Indie Pop", "radio_mirror_park.mp3"),
        RadioStation("rebel_radio", "Rebel Radio", "Country", "rebel_radio.mp3"),
        RadioStation("soulwax_fm", "Soulwax FM", "Techno", "soulwax_fm.mp3"),
        RadioStation("the_lab", "The Lab", "Hip Hop / Synth", "the_lab.mp3"),
        RadioStation("the_lowdown", "The Lowdown 91.1", "Classic Soul", "the_lowdown_91_1.mp3"),
        RadioStation("worldwide_fm", "WorldWide FM", "Chillwave / Jazz", "worldwide_fm.mp3"),

        // --- Talk Radio & Specials ---
        RadioStation("blaine_county_radio", "Blaine County Radio", "Talk Radio", "blaine_county_radio.mp3"),
        RadioStation("gta_commercials", "GTA V Commercials", "Satire / Ads", "gta_v_radio_commercials.mp3")
    )

    // Deine Favoriten
    val favoriteIds = mutableListOf("los_santos_rock", "non_stop_pop", "west_coast_classics", "space_103_2")

    fun getStationById(id: String): RadioStation? = stations.find { it.id == id }

    /**
     * Speichert die ID des aktuell gespielten Senders
     */
    fun saveCurrentStation(context: Context, stationId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_STATION_ID, stationId).apply()
        Log.d(TAG, "Saved current station: $stationId")
    }

    /**
     * Lädt den zuletzt gespielten Sender oder gibt den ersten Sender zurück
     */
    fun getLastStation(context: Context): RadioStation {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastId = prefs.getString(KEY_LAST_STATION_ID, null)

        return if (lastId != null) {
            getStationById(lastId) ?: stations[0]
        } else {
            stations[0] // Default: Erster Sender (Blonded Radio)
        }
    }

    /**
     * Berechnet die aktuelle Position im Radio-Stream basierend auf der verstrichenen Zeit
     * seit dem globalen Start-Zeitpunkt
     */
    fun getSimulatedPosition(durationMs: Long): Long {
        if (durationMs <= 0) return 0

        val timePassed = System.currentTimeMillis() - globalRadioStartTime
        val position = timePassed % durationMs

        Log.d(TAG, "Simulated position: ${position}ms of ${durationMs}ms (time passed: ${timePassed}ms)")
        return position
    }

    /**
     * Speichert die aktuelle Playback-Position und den Zeitstempel
     */
    fun savePlaybackPosition(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()

        prefs.edit().apply {
            putLong(KEY_LAST_TIMESTAMP, currentTime)
            putLong(KEY_GLOBAL_START_TIME, globalRadioStartTime)
            apply()
        }

        Log.d(TAG, "Saved playback position at timestamp: $currentTime")
    }

    /**
     * Stellt die Playback-Position wieder her basierend auf der verstrichenen Zeit
     */
    fun restorePlaybackPosition(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastTimestamp = prefs.getLong(KEY_LAST_TIMESTAMP, 0)
        val savedStartTime = prefs.getLong(KEY_GLOBAL_START_TIME, 0)

        if (savedStartTime > 0) {
            // Stelle den globalen Start-Zeitpunkt wieder her
            globalRadioStartTime = savedStartTime

            val timeSinceLastSave = System.currentTimeMillis() - lastTimestamp
            Log.d(TAG, "Restored playback position. Time since last save: ${timeSinceLastSave}ms")
            Log.d(TAG, "Radio has been running since: $globalRadioStartTime")
        } else {
            // Erstes Mal - setze neuen Start-Zeitpunkt
            globalRadioStartTime = System.currentTimeMillis()
            Log.d(TAG, "First launch - initialized radio start time: $globalRadioStartTime")
        }
    }
}