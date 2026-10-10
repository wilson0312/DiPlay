package com.shilapi.xcertplay.vehicle.ecarx

import com.shilapi.xcertplay.media.AudioDuckingOwner
import com.shilapi.xcertplay.media.AudioRoutingConfig
import com.shilapi.xcertplay.media.LegacyStreamPolicy
import org.junit.Assert.*
import org.junit.Test

class EcarxAudioModeTest {
    /** Upstream mode preserves the exact caller configuration, not new fixed defaults. */
    @Test fun upstreamModeDoesNotRewriteCustomRoutes() {
        val original = AudioRoutingConfig(mediaStreamType = 7, navigationStreamType = 14)
        assertSame(original, EcarxAudioMode.UPSTREAM.routing(original))
    }

    /** E03 stream 12 is not the BYD stream 14, and there is one ducking owner. */
    @Test fun e03ProvidesExplicitStreamsAndStrictSystemDucking() {
        val original = AudioRoutingConfig(mediaStreamType = 7, navigationStreamType = 14)
        val config = EcarxAudioMode.E03.routing(original)
        assertEquals(EcarxAudioMode.E03_MUSIC_STREAM, config.mediaStreamType)
        assertEquals(EcarxAudioMode.E03_NAVIGATION_STREAM, config.navigationStreamType)
        assertEquals(LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM, config.legacyStreamPolicy)
        assertEquals(AudioDuckingOwner.SYSTEM, config.duckingOwner)
        assertEquals(7, original.mediaStreamType)
        assertEquals(14, original.navigationStreamType)
    }

    /** Preference names do not depend on labels or enum ordinals. */
    @Test fun allSavedModesRoundTrip() {
        EcarxAudioMode.entries.forEach { assertSame(it, EcarxAudioMode.fromPreference(it.preferenceValue)) }
    }

    /** Invalid persisted configuration must not quietly select a different route. */
    @Test fun unknownModeIsAnExplicitError() {
        for (value in listOf("", "E03", "future_mode")) {
            assertThrows(IllegalArgumentException::class.java) { EcarxAudioMode.fromPreference(value) }
        }
    }
}
