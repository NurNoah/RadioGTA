package com.example.radiogta // Dein Package Name anpassen

import android.net.Uri

data class RadioStation(
    val id: String,
    val name: String,
    val genre: String,
    val assetFileName: String, // Dateiname im assets/ oder raw/ Ordner
)

object StationManager {

    // Simuliert, wann der "Server" gestartet wurde, damit alle Radios synchron laufen
    private val globalRadioStartTime = System.currentTimeMillis()

    // Hier deine Sender eintragen (Du brauchst die mp3 Dateien!)
    val stations = listOf(
        // --- Music Stations ---
        RadioStation("blonded_radio", "Blonded Radio", "Soul / R&B / Rap", "blonded_radio.mp3", R.drawable.ic_blonded),
        RadioStation("blue_ark", "Blue Ark", "Reggae / Dancehall", "blue_ark.mp3", R.drawable.ic_blueark),
        RadioStation("channel_x", "Channel X", "Punk Rock", "channel_x.mp3", R.drawable.ic_channelx),
        RadioStation("east_los_fm", "East Los FM", "Mexican Electronica", "east_los_fm.mp3", R.drawable.ic_eastlos),
        RadioStation("flylo_fm", "FlyLo FM", "IDM / Experimental", "flylo_fm.mp3", R.drawable.ic_flylo),
        RadioStation("los_santos_rock", "Los Santos Rock Radio", "Classic Rock", "los_santos_rock_radio.mp3", R.drawable.ic_ls_rock),
        RadioStation("los_santos_underground", "LS Underground Radio", "House / Techno", "los_santos_underground_radio.mp3", R.drawable.ic_ls_underground),
        RadioStation("non_stop_pop", "Non-Stop-Pop FM", "Pop / Dance", "non_stop_pop_fm.mp3", R.drawable.ic_non_stop_pop),
        RadioStation("radio_los_santos", "Radio Los Santos", "Modern Hip Hop", "radio_los_santos.mp3", R.drawable.ic_radio_ls),
        RadioStation("radio_mirror_park", "Radio Mirror Park", "Indie Pop", "radio_mirror_park.mp3", R.drawable.ic_mirror_park),
        RadioStation("rebel_radio", "Rebel Radio", "Country", "rebel_radio.mp3", R.drawable.ic_rebel),
        RadioStation("soulwax_fm", "Soulwax FM", "Techno", "soulwax_fm.mp3", R.drawable.ic_soulwax),
        RadioStation("space_103_2", "Space 103.2", "Funk", "space_103_2.mp3", R.drawable.ic_space),
        RadioStation("the_lab", "The Lab", "Hip Hop / Synth", "the_lab.mp3", R.drawable.ic_lab),
        RadioStation("the_lowdown", "The Lowdown 91.1", "Classic Soul", "the_lowdown_91_1.mp3", R.drawable.ic_lowdown),
        RadioStation("vinewood_blvd", "Vinewood Boulevard Radio", "Alternative Rock", "vinewood_boulevard_radio.mp3", R.drawable.ic_vinewood),
        RadioStation("west_coast_classics", "West Coast Classics", "Old School Hip Hop", "west_coast_classics.mp3", R.drawable.ic_wcc),
        RadioStation("worldwide_fm", "WorldWide FM", "Chillwave / Jazz", "worldwide_fm.mp3", R.drawable.ic_worldwide),

        // --- Talk Radio ---
        RadioStation("blaine_county_radio", "Blaine County Radio", "Talk Radio", "blaine_county_radio.mp3", R.drawable.ic_blaine_county),
        RadioStation("west_coast_talk", "West Coast Talk Radio", "Talk Radio", "west_coast_talk_radio.mp3", R.drawable.ic_wctr),
        RadioStation("gta_commercials", "GTA V Commercials", "Satire / Ads", "gta_v_radio_commercials.mp3", R.drawable.ic_commercials)
    )

    // Einfache Favoriten-Speicherung (im echten Leben SharedPreferences nutzen)
    val favoriteIds = mutableListOf("los_santos_rock", "west_coast_classics")

    fun getStationById(id: String): RadioStation? = stations.find { it.id == id }

    // DAS IST DIE MAGIE: Berechnet, an welcher Stelle der Song gerade wäre
    fun getSimulatedPosition(durationMs: Long): Long {
        if (durationMs <= 0) return 0
        val timePassed = System.currentTimeMillis() - globalRadioStartTime
        // Modulo-Operator sorgt für den Loop-Effekt
        return timePassed % durationMs
    }
}