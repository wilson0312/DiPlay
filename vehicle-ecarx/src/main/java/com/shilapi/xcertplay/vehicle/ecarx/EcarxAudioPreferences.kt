package com.shilapi.xcertplay.vehicle.ecarx

import android.content.Context
import android.content.SharedPreferences

/** Android storage adapter. An absent setting means upstream; corrupt data is an error. */
class EcarxAudioPreferences(private val preferences: SharedPreferences) {
    /** Reads the user's choice without changing any vehicle or upstream settings. */
    fun load(): EcarxAudioMode = preferences.getString(KEY_MODE, null)
        ?.let(EcarxAudioMode::fromPreference) ?: EcarxAudioMode.UPSTREAM

    /** Commit must succeed before the UI marks a reconnect as pending. */
    fun save(mode: EcarxAudioMode) {
        check(preferences.edit().putString(KEY_MODE, mode.preferenceValue).commit()) {
            "ECARX audio mode could not be saved"
        }
    }

    companion object {
        internal const val FILE_NAME = "diplay_ecarx"
        internal const val KEY_MODE = "audio_mode"

        /** Composition-root helper: the adapter uses application-scoped preferences. */
        fun open(context: Context): EcarxAudioPreferences = EcarxAudioPreferences(
            context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE),
        )
    }
}
