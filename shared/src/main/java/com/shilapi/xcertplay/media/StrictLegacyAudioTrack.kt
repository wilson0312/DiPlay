package com.shilapi.xcertplay.media

/** Platform operations injected into strict routing; this interface has no usage fallback. */
internal interface LegacyTrackFactory<T> {
    /** Creates the track with the requested legacy stream. */
    fun create(streamType: Int): T
    /** Returns the platform initialization state. */
    fun isInitialized(track: T): Boolean
    /** Returns AudioTrack's reported stream; native output still needs on-device verification. */
    fun reportedStreamType(track: T): Int
    /** Releases a rejected candidate exactly once. */
    fun release(track: T)
}

internal enum class LegacyRouteFailure { CONSTRUCTION, UNINITIALIZED, STREAM_MISMATCH, INSPECTION }

/** Only numeric route metadata enters the exported report, not arbitrary platform exception text. */
internal data class LegacyRouteError(
    /** Explicitly requested OEM stream. */
    val requestedStream: Int,
    /** Stage at which the requested route failed. */
    val reason: LegacyRouteFailure,
    /** Java-reported stream if it differs; this is not native routing evidence. */
    val reportedStream: Int? = null,
)

/** A requested route failed; callers must report this instead of creating another route. */
internal class AudioRouteException(
    /** Numeric metadata retained for session diagnostics. */
    val details: LegacyRouteError,
    cause: Throwable? = null,
) :
    IllegalStateException("Legacy route rejected: $details", cause)

/** Builds only the requested stream and keeps both routing and cleanup errors visible. */
internal object StrictLegacyAudioTrack {
    fun <T> build(streamType: Int, factory: LegacyTrackFactory<T>): T {
        val track = try {
            factory.create(streamType)
        } catch (error: RuntimeException) {
            throw AudioRouteException(LegacyRouteError(streamType, LegacyRouteFailure.CONSTRUCTION), error)
        }
        try {
            if (!factory.isInitialized(track)) {
                throw AudioRouteException(LegacyRouteError(streamType, LegacyRouteFailure.UNINITIALIZED))
            }
            val reported = factory.reportedStreamType(track)
            if (reported != streamType) {
                throw AudioRouteException(LegacyRouteError(streamType, LegacyRouteFailure.STREAM_MISMATCH, reported))
            }
            return track
        } catch (error: Throwable) {
            releaseRejected(track, factory, error)
            if (error is AudioRouteException) throw error
            if (error !is RuntimeException) throw error
            throw AudioRouteException(LegacyRouteError(streamType, LegacyRouteFailure.INSPECTION), error)
        }
    }

    /** Cleanup failure is attached to the routing error, never silently discarded. */
    private fun <T> releaseRejected(track: T, factory: LegacyTrackFactory<T>, error: Throwable) {
        try {
            factory.release(track)
        } catch (cleanup: Throwable) {
            if (cleanup !== error) error.addSuppressed(cleanup)
        }
    }
}
