package com.example.radiogta

data class RadioStation(
    val id: String,
    val name: String,
    val genre: String,
    val assetFileName: String
)

object StationManager {

    private val globalRadioStartTime = System.currentTimeMillis()

    val stations = listOf(
        // --- Music Stations ---
        RadioStation("blonded_radio", "Blonded Radio", "Soul / R&B / Rap", "blonded_radio.mp3"),
        RadioStation("blue_ark", "Blue Ark", "Reggae / Dancehall", "blue_ark.mp3"),
        RadioStation("channel_x", "Channel X", "Punk Rock", "channel_x.mp3"),
        RadioStation("east_los_fm", "East Los FM", "Mexican Electronica", "east_los_fm.mp3"),
        RadioStation("flylo_fm", "FlyLo FM", "IDM / Experimental", "flylo_fm.mp3"),
        RadioStation("los_santos_rock", "Los Santos Rock Radio", "Classic Rock", "los_santos_rock_radio.mp3"),
        RadioStation("los_santos_underground", "LS Underground Radio", "House / Techno", "los_santos_underground_radio.mp3"),
        RadioStation("non_stop_pop", "Non-Stop-Pop FM", "Pop / Dance", "non_stop_pop_fm.mp3"),
        RadioStation("radio_los_santos", "Radio Los Santos", "Modern Hip Hop", "radio_los_santos.mp3"),
        RadioStation("radio_mirror_park", "Radio Mirror Park", "Indie Pop", "radio_mirror_park.mp3"),
        RadioStation("rebel_radio", "Rebel Radio", "Country", "rebel_radio.mp3"),
        RadioStation("soulwax_fm", "Soulwax FM", "Techno", "soulwax_fm.mp3"),
        RadioStation("space_103_2", "Space 103.2", "Funk", "space_103_2.mp3"),
        RadioStation("the_lab", "The Lab", "Hip Hop / Synth", "the_lab.mp3"),
        RadioStation("the_lowdown", "The Lowdown 91.1", "Classic Soul", "the_lowdown_91_1.mp3"),
        RadioStation("vinewood_blvd", "Vinewood Boulevard Radio", "Alternative Rock", "vinewood_boulevard_radio.mp3"),
        RadioStation("west_coast_classics", "West Coast Classics", "Old School Hip Hop", "west_coast_classics.mp3"),
        RadioStation("worldwide_fm", "WorldWide FM", "Chillwave / Jazz", "worldwide_fm.mp3"),

        // --- Talk Radio & Specials ---
        RadioStation("blaine_county_radio", "Blaine County Radio", "Talk Radio", "blaine_county_radio.mp3"),
        RadioStation("west_coast_talk", "West Coast Talk Radio", "Talk Radio", "west_coast_talk_radio.mp3"),
        RadioStation("gta_commercials", "GTA V Commercials", "Satire / Ads", "gta_v_radio_commercials.mp3")
    )

    // Deine Favoriten
    val favoriteIds = mutableListOf("los_santos_rock", "non_stop_pop", "west_coast_classics", "blonded_radio")

    fun getStationById(id: String): RadioStation? = stations.find { it.id == id }

    fun getSimulatedPosition(durationMs: Long): Long {
        if (durationMs <= 0) return 0
        val timePassed = System.currentTimeMillis() - globalRadioStartTime
        return timePassed % durationMs
    }
}