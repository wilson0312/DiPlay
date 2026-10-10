package com.shilapi.xcertplay.media

/** Whether a requested legacy stream may be replaced by usage-based routing. */
enum class LegacyStreamPolicy {
    ALLOW_USAGE_FALLBACK,
    REQUIRE_REQUESTED_STREAM,
}

/** Selects the single owner of temporary music ducking, not of user stream volume. */
enum class AudioDuckingOwner {
    APPLICATION,
    SYSTEM,
}

/** Immutable routing captured when a CarPlay session starts. Zero means usage routing. */
data class AudioRoutingConfig(
    /** Legacy music stream, or zero for the upstream usage path. */
    val mediaStreamType: Int = USAGE_ROUTING,
    /** Legacy navigation stream, or zero for the upstream usage path. */
    val navigationStreamType: Int = USAGE_ROUTING,
    /** Failure behavior for an explicitly selected legacy stream. */
    val legacyStreamPolicy: LegacyStreamPolicy = LegacyStreamPolicy.ALLOW_USAGE_FALLBACK,
    /** Prevents applying application ducking on top of OEM ducking. */
    val duckingOwner: AudioDuckingOwner = AudioDuckingOwner.APPLICATION,
) {
    init {
        require(mediaStreamType >= USAGE_ROUTING) { "Negative media stream: $mediaStreamType" }
        require(navigationStreamType >= USAGE_ROUTING) { "Negative navigation stream: $navigationStreamType" }
        if (legacyStreamPolicy == LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM) {
            require(mediaStreamType != USAGE_ROUTING && navigationStreamType != USAGE_ROUTING) {
                "Strict routing requires explicit music and navigation streams"
            }
        }
    }

    /** Phone and assistant retain the upstream usage path. */
    internal fun streamTypeFor(channel: AudioChannel): Int = when (channel) {
        AudioChannel.MEDIA -> mediaStreamType
        AudioChannel.NAVIGATION -> navigationStreamType
        AudioChannel.PHONE, AudioChannel.ASSISTANT -> USAGE_ROUTING
    }

    companion object {
        const val USAGE_ROUTING = 0
    }
}
