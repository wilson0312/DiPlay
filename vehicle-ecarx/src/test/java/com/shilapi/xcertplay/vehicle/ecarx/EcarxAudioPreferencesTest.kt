package com.shilapi.xcertplay.vehicle.ecarx

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class EcarxAudioPreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences(EcarxAudioPreferences.FILE_NAME, Context.MODE_PRIVATE)

    @After fun clear() { prefs.edit().clear().commit() }

    /** A new installation is opt-out without writing anything. */
    @Test fun missingPreferenceUsesUpstreamWithoutWriting() {
        assertEquals(EcarxAudioMode.UPSTREAM, EcarxAudioPreferences.open(context).load())
        assertTrue(prefs.all.isEmpty())
    }

    /** A new adapter instance sees a successfully committed choice. */
    @Test fun bothModesSurviveReopeningTheStore() {
        EcarxAudioMode.entries.forEach { mode ->
            EcarxAudioPreferences.open(context).save(mode)
            assertEquals(mode, EcarxAudioPreferences.open(context).load())
        }
    }

    /** Corrupt content is different from an absent value. */
    @Test fun unknownSavedValueIsNotSilentlyReset() {
        prefs.edit().putString(EcarxAudioPreferences.KEY_MODE, "invalid").commit()
        assertThrows(IllegalArgumentException::class.java) { EcarxAudioPreferences.open(context).load() }
        assertEquals("invalid", prefs.getString(EcarxAudioPreferences.KEY_MODE, null))
    }

    @Test fun wrongSavedTypeIsNotSilentlyReset() {
        prefs.edit().putInt(EcarxAudioPreferences.KEY_MODE, 1).commit()
        assertThrows(ClassCastException::class.java) { EcarxAudioPreferences.open(context).load() }
    }

    /** A failed commit cannot be reported as a saved choice. */
    @Test fun failedCommitSurfacesAnError() {
        val failing = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val delegate = prefs.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        delegate.putString(key, value)
                        return this
                    }
                    override fun commit(): Boolean = false
                }
            }
        }
        assertThrows(IllegalStateException::class.java) { EcarxAudioPreferences(failing).save(EcarxAudioMode.E03) }
        assertTrue(prefs.all.isEmpty())
    }
}
