package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class AudioRoutingConfigTest {
    /** Ordinary installations retain the original usage routing and application ducking. */
    @Test fun defaultRetainsUpstreamBehavior() {
        val config = AudioRoutingConfig()
        AudioChannel.entries.forEach { assertEquals(AudioRoutingConfig.USAGE_ROUTING, config.streamTypeFor(it)) }
        assertEquals(LegacyStreamPolicy.ALLOW_USAGE_FALLBACK, config.legacyStreamPolicy)
        assertEquals(AudioDuckingOwner.APPLICATION, config.duckingOwner)
    }

    /** Never route calls or Siri to navigation just because an E03 profile is selected. */
    @Test fun explicitStreamsChangeOnlyMusicAndGuidance() {
        val config = AudioRoutingConfig(MUSIC_STREAM, NAVIGATION_STREAM, LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM)
        assertEquals(MUSIC_STREAM, config.streamTypeFor(AudioChannel.MEDIA))
        assertEquals(NAVIGATION_STREAM, config.streamTypeFor(AudioChannel.NAVIGATION))
        assertEquals(AudioRoutingConfig.USAGE_ROUTING, config.streamTypeFor(AudioChannel.PHONE))
        assertEquals(AudioRoutingConfig.USAGE_ROUTING, config.streamTypeFor(AudioChannel.ASSISTANT))
    }

    /** A strict profile must never secretly select usage mode for one of its promised streams. */
    @Test fun strictConfigurationRejectsMissingExplicitStreams() {
        for (config in listOf(MUSIC_STREAM to 0, 0 to NAVIGATION_STREAM)) {
            assertThrows(IllegalArgumentException::class.java) {
                AudioRoutingConfig(config.first, config.second, LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM)
            }
        }
    }

    /** Reject invalid external configuration at its entry point. */
    @Test fun rejectsNegativeStreams() {
        assertThrows(IllegalArgumentException::class.java) { AudioRoutingConfig(mediaStreamType = -1) }
        assertThrows(IllegalArgumentException::class.java) { AudioRoutingConfig(navigationStreamType = -1) }
    }

    private companion object {
        const val MUSIC_STREAM = 3
        const val NAVIGATION_STREAM = 12
    }
}
