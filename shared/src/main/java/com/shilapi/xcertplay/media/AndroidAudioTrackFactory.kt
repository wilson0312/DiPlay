package com.shilapi.xcertplay.media

import android.media.AudioAttributes
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.shilapi.xcertplay.airplay.AudioFormat

/** Parameters for one platform track, already classified by the existing protocol mapper. */
internal data class AudioTrackSpec(
    /** Incoming PCM format, not a new decoder or protocol implementation. */
    val format: AudioFormat,
    /** Semantic audio role and content type. */
    val selection: AudioChannelSelection,
    /** Requested legacy stream; zero requests usage routing. */
    val streamType: Int,
    /** Planned AudioTrack capacity in bytes. */
    val bufferBytes: Int,
    /** Whether a ROM rejection is allowed to use the upstream usage path. */
    val legacyPolicy: LegacyStreamPolicy,
) {
    /** Shared PCM channel-mask calculation for both platform construction paths. */
    fun channelMask(): Int = if (format.channels >= STEREO_CHANNELS) {
        AndroidAudioFormat.CHANNEL_OUT_STEREO
    } else AndroidAudioFormat.CHANNEL_OUT_MONO

    private companion object {
        const val STEREO_CHANNELS = 2
    }
}

/** Built route metadata. Reported Java attributes are not proof of native OEM routing. */
internal data class AudioTrackRoute(
    /** Owned by the renderer after a successful build. */
    val track: AudioTrack,
    /** Configured focus attributes; Android 9 cannot read AudioTrack attributes back. */
    val attributes: AudioAttributes,
    /** Present only when legacy construction actually succeeded. */
    val legacyStreamType: Int?,
    /** States the path actually used, including any upstream compatibility fallback. */
    val label: String,
)

/** Android adapter. Strict E03 routing and upstream ROM compatibility are separate choices. */
@Suppress("DEPRECATION")
internal class AndroidAudioTrackFactory(
    private val spec: AudioTrackSpec,
    private val report: (String) -> Unit,
    private val legacy: LegacyTrackFactory<AudioTrack>,
) {
    /** Creates one route; strict mode has no branch to the usage builder on rejection. */
    fun build(): AudioTrackRoute {
        if (spec.streamType == AudioRoutingConfig.USAGE_ROUTING) return usageRoute("usage")
        val attributes = legacyAttributes()
        if (spec.legacyPolicy == LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM) {
            return legacyRoute(StrictLegacyAudioTrack.build(spec.streamType, legacy), attributes)
        }
        return LegacyAudioFallback.build(
            createLegacy = { legacyRoute(legacy.create(spec.streamType), attributes) },
            isInitialized = { legacy.isInitialized(it.track) },
            release = { legacy.release(it.track) },
            createFallback = {
                report("Audio: streamType=${spec.streamType} rejected; upstream usage fallback selected")
                usageRoute("streamType=${spec.streamType}(fallback=usage)")
            },
        )
    }

    /** Captures a successful legacy route without changing the user's stream volume. */
    private fun legacyRoute(track: AudioTrack, attributes: AudioAttributes): AudioTrackRoute = AudioTrackRoute(
        track = track,
        attributes = attributes,
        legacyStreamType = spec.streamType,
        label = "streamType=${spec.streamType} policy=${spec.legacyPolicy}",
    )

    /** Used by explicit upstream usage routing or its existing compatibility policy only. */
    private fun usageRoute(label: String): AudioTrackRoute {
        val attributes = usageAttributes()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(AndroidAudioFormat.Builder()
                .setSampleRate(spec.format.sampleRate)
                .setChannelMask(spec.channelMask())
                .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(spec.bufferBytes)
            .build()
        return AudioTrackRoute(track, attributes, null, label)
    }

    /** Standard stream focus attributes can be represented publicly; extended streams use role attributes. */
    private fun legacyAttributes(): AudioAttributes {
        if (spec.streamType !in AudioManager.STREAM_SYSTEM..AudioManager.STREAM_ACCESSIBILITY) {
            return usageAttributes()
        }
        if (spec.legacyPolicy == LegacyStreamPolicy.REQUIRE_REQUESTED_STREAM) {
            return AudioAttributes.Builder().setLegacyStreamType(spec.streamType).build()
        }
        return try {
            AudioAttributes.Builder().setLegacyStreamType(spec.streamType).build()
        } catch (error: RuntimeException) {
            report("Audio: legacy focus attributes rejected stream=${spec.streamType} error=${error.javaClass.simpleName}")
            usageAttributes()
        }
    }

    /** These are Android usage values, deliberately distinct from E03 legacy stream numbers. */
    private fun usageAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(when (spec.selection.channel) {
            AudioChannel.MEDIA -> AudioAttributes.USAGE_MEDIA
            AudioChannel.PHONE -> AudioAttributes.USAGE_VOICE_COMMUNICATION
            AudioChannel.ASSISTANT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes.USAGE_ASSISTANT
            } else AudioAttributes.USAGE_MEDIA
            AudioChannel.NAVIGATION -> AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
        })
        .setContentType(when (spec.selection.contentType) {
            AudioContentType.MUSIC -> AudioAttributes.CONTENT_TYPE_MUSIC
            AudioContentType.SPEECH -> AudioAttributes.CONTENT_TYPE_SPEECH
        }).build()

}
