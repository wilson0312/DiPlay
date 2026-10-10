package com.shilapi.xcertplay.vehicle.ecarx

import com.shilapi.xcertplay.media.AudioDuckingOwner
import com.shilapi.xcertplay.media.AudioRoutingConfig
import com.shilapi.xcertplay.media.LegacyStreamPolicy

/** Explicit opt-in; never infer E03 support or permissions from the manufacturer's name. */
enum class EcarxAudioMode(
    /** Stable preference value, independent of translated UI labels. */
    val preferenceValue: String,
) {
    UPSTREAM("upstream"),
    E03("e03_android9"),
    ;

    /** Keeps upstream choices intact so leaving E03 mode does not overwrite them. */
    fun routing(upstream: AudioRoutingConfig): AudioRoutingConfig = when (this) {
        UPSTREAM -> upstream
        E03 -> AudioRoutingConfig(
            mediaStreamType = E03_MUSIC_STREAM,
            navigationStreamType = E03_NAVIGATION_STREAM,
            legacyStreamPolicy = LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM,
            duckingOwner = AudioDuckingOwner.SYSTEM,
        )
    }

    companion object {
        /** Verified E03 Android 9 system volume objects, not AudioAttributes usage IDs. */
        const val E03_MUSIC_STREAM = 3
        const val E03_NAVIGATION_STREAM = 12

        /** Unknown or corrupt saved modes must remain visible rather than selecting another route. */
        fun fromPreference(value: String): EcarxAudioMode = entries.singleOrNull { it.preferenceValue == value }
            ?: throw IllegalArgumentException("Unknown ECARX audio mode: $value")
    }
}
