package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test

class StrictLegacyAudioTrackTest {
    /** Both E03 streams must be built directly, not interpreted as usage values. */
    @Test fun acceptsOnlyTheRequestedInitializedStream() {
        for (stream in listOf(MUSIC_STREAM, NAVIGATION_STREAM)) {
            val factory = RecordingFactory(Behavior(reportedStream = stream))
            assertEquals("track", StrictLegacyAudioTrack.build(stream, factory))
            assertEquals(listOf("create:$stream", "state", "stream"), factory.events)
        }
    }

    /** A constructor rejection has no candidate to release and no usage branch. */
    @Test fun constructorRejectionPreservesTheCauseWithoutRetrying() {
        val rejection = IllegalArgumentException("OEM rejected stream")
        val factory = RecordingFactory(Behavior(createFailure = rejection))
        val error = rejected(factory)
        assertEquals(LegacyRouteFailure.CONSTRUCTION, error.details.reason)
        assertSame(rejection, error.cause)
        assertEquals(listOf("create:$NAVIGATION_STREAM"), factory.events)
    }

    /** Do not inspect or retain an uninitialized native track. */
    @Test fun releasesUninitializedCandidateExactlyOnce() {
        val factory = RecordingFactory(Behavior(initialized = false))
        val error = rejected(factory)
        assertEquals(LegacyRouteFailure.UNINITIALIZED, error.details.reason)
        assertEquals(listOf("create:$NAVIGATION_STREAM", "state", "release"), factory.events)
    }

    /** Java-level stream mismatch is a failure, not evidence that music and navigation are separate. */
    @Test fun releasesAStreamMismatchAndReportsBothNumbers() {
        val factory = RecordingFactory(Behavior(reportedStream = MUSIC_STREAM))
        val error = rejected(factory)
        assertEquals(LegacyRouteError(NAVIGATION_STREAM, LegacyRouteFailure.STREAM_MISMATCH, MUSIC_STREAM), error.details)
        assertEquals(listOf("create:$NAVIGATION_STREAM", "state", "stream", "release"), factory.events)
    }

    /** Inspection errors release the track and keep the original error. */
    @Test fun getterFailureIsNotMistakenForInitializationSuccess() {
        val failure = IllegalStateException("OEM getter failure")
        val factory = RecordingFactory(Behavior(inspectionFailure = failure))
        val error = rejected(factory)
        assertEquals(LegacyRouteFailure.INSPECTION, error.details.reason)
        assertSame(failure, error.cause)
        assertEquals("release", factory.events.last())
    }

    /** Keep the route error primary, with cleanup failure attached and no second release. */
    @Test fun releaseFailureRemainsVisibleOnTheRouteError() {
        val cleanup = IllegalStateException("release failure")
        val factory = RecordingFactory(Behavior(initialized = false, releaseFailure = cleanup))
        val error = rejected(factory)
        assertEquals(LegacyRouteFailure.UNINITIALIZED, error.details.reason)
        assertSame(cleanup, error.suppressed.single())
        assertEquals(1, factory.events.count { it == "release" })
    }

    /** Even an unsupported platform getter must release its candidate before retaining crash semantics. */
    @Test fun linkageFailureReleasesTheTrackAndPropagatesUnchanged() {
        val failure = NoSuchMethodError("Injected unsupported getter")
        val factory = RecordingFactory(Behavior(inspectionFailure = failure))
        val error = assertThrows(NoSuchMethodError::class.java) {
            StrictLegacyAudioTrack.build(NAVIGATION_STREAM, factory)
        }
        assertSame(failure, error)
        assertEquals("release", factory.events.last())
    }

    /** Linkage failures during cleanup remain attached instead of hiding the original route rejection. */
    @Test fun cleanupLinkageFailureDoesNotReplaceTheRouteError() {
        val cleanup = NoSuchMethodError("Injected unsupported release")
        val factory = RecordingFactory(Behavior(initialized = false, releaseFailure = cleanup))
        val error = rejected(factory)
        assertEquals(LegacyRouteFailure.UNINITIALIZED, error.details.reason)
        assertSame(cleanup, error.suppressed.single())
    }

    private fun rejected(factory: RecordingFactory): AudioRouteException =
        assertThrows(AudioRouteException::class.java) { StrictLegacyAudioTrack.build(NAVIGATION_STREAM, factory) }

    private data class Behavior(
        val initialized: Boolean = true,
        val reportedStream: Int = NAVIGATION_STREAM,
        val createFailure: RuntimeException? = null,
        val inspectionFailure: Throwable? = null,
        val releaseFailure: Throwable? = null,
    )

    /** Test-only platform adapter records the actual strict helper calls. */
    private class RecordingFactory(private val behavior: Behavior) : LegacyTrackFactory<String> {
        val events = mutableListOf<String>()

        override fun create(streamType: Int): String {
            events += "create:$streamType"
            behavior.createFailure?.let { throw it }
            return "track"
        }

        override fun isInitialized(track: String): Boolean {
            events += "state"
            return behavior.initialized
        }

        override fun reportedStreamType(track: String): Int {
            events += "stream"
            behavior.inspectionFailure?.let { throw it }
            return behavior.reportedStream
        }

        override fun release(track: String) {
            events += "release"
            behavior.releaseFailure?.let { throw it }
        }
    }

    private companion object {
        const val MUSIC_STREAM = 3
        const val NAVIGATION_STREAM = 12
    }
}
