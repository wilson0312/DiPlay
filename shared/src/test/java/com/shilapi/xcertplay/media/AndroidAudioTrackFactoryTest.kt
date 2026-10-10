package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioTrack
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAudioTrack

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AndroidAudioTrackFactoryTest {
    /** The E03 media path actually uses the legacy constructor, not usage 3. */
    @Test
    @Config(shadows = [MusicStreamMetadata::class])
    fun strictMusicUsesTheRequestedLegacyTrack() {
        val route = factory(MUSIC_STREAM, LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM).build()
        try {
            assertEquals(MUSIC_STREAM, route.legacyStreamType)
            assertEquals(MUSIC_STREAM, route.track.streamType)
            assertEquals(AudioTrack.STATE_INITIALIZED, route.track.state)
            assertEquals(AudioAttributes.USAGE_MEDIA, route.attributes.usage)
        } finally { route.track.release() }
    }

    /** A platform rejection is injected explicitly; the real strict policy must not call Builder. */
    @Test
    @Config(shadows = [RejectUsageBuilder::class])
    fun rejectedExtendedStreamNeverCallsTheUsageBuilder() {
        val spec = spec(NAVIGATION_STREAM, LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM)
        val error = assertThrows(AudioRouteException::class.java) {
            AndroidAudioTrackFactory(spec, {}, rejectingLegacy(spec)).build()
        }
        assertEquals(LegacyRouteFailure.CONSTRUCTION, error.details.reason)
        assertTrue(error.cause is IllegalArgumentException)
    }

    /** Upstream keeps its documented ROM compatibility route rather than inheriting E03 strictness. */
    @Test fun upstreamExtendedStreamRejectionReportsItsUsageFallback() {
        val messages = mutableListOf<String>()
        val spec = spec(NAVIGATION_STREAM, LegacyStreamPolicy.ALLOW_USAGE_FALLBACK)
        val route = AndroidAudioTrackFactory(spec, messages::add, rejectingLegacy(spec)).build()
        try {
            assertNull(route.legacyStreamType)
            assertTrue(route.label.contains("fallback=usage"))
            assertTrue(messages.single().contains("upstream usage fallback selected"))
        } finally { route.track.release() }
    }

    private fun factory(stream: Int, policy: LegacyStreamPolicy): AndroidAudioTrackFactory {
        val spec = spec(stream, policy)
        return AndroidAudioTrackFactory(spec, {}, AndroidLegacyTrackFactory(spec))
    }

    /** Only the rejected legacy operation is replaced; upstream Builder remains real Robolectric code. */
    private fun rejectingLegacy(spec: AudioTrackSpec) = object : LegacyTrackFactory<AudioTrack> by AndroidLegacyTrackFactory(spec) {
        override fun create(streamType: Int): AudioTrack = throw IllegalArgumentException("Injected OEM stream rejection")
    }

    private fun spec(stream: Int, policy: LegacyStreamPolicy) = AudioTrackSpec(
        format = AudioFormat(AudioCodecKind.LPCM, SAMPLE_RATE, 2, 96, "media"),
        selection = AudioChannelSelection(AudioChannel.MEDIA, AudioContentType.MUSIC),
        streamType = stream,
        bufferBytes = BUFFER_BYTES,
        legacyPolicy = policy,
    )

    /** A test-only sentinel: invoking the wrong branch fails the test immediately. */
    @Implements(AudioTrack.Builder::class)
    class RejectUsageBuilder {
        @Implementation fun build(): AudioTrack = throw AssertionError("Strict route called usage Builder")
    }

    /** Robolectric skips the JNI write to mStreamType; this fixture supplies music's native metadata only. */
    @Implements(AudioTrack::class)
    class MusicStreamMetadata : ShadowAudioTrack() {
        @Implementation fun getStreamType(): Int = MUSIC_STREAM
    }

    private companion object {
        const val MUSIC_STREAM = 3
        const val NAVIGATION_STREAM = 12
        const val SAMPLE_RATE = 48_000
        const val BUFFER_BYTES = 4096
    }
}
