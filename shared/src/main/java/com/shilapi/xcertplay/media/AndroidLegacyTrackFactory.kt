package com.shilapi.xcertplay.media

import android.media.AudioFormat
import android.media.AudioTrack

/** Real platform adapter injected at the renderer's composition root, not inside routing policy. */
@Suppress("DEPRECATION")
internal class AndroidLegacyTrackFactory(private val spec: AudioTrackSpec) : LegacyTrackFactory<AudioTrack> {
    /** The OEM constructor is needed for extended streams such as E03 navigation 12. */
    override fun create(streamType: Int): AudioTrack = AudioTrack(
        streamType, spec.format.sampleRate, spec.channelMask(), AudioFormat.ENCODING_PCM_16BIT,
        spec.bufferBytes, AudioTrack.MODE_STREAM,
    )

    /** Initialization must be confirmed before a renderer owns the candidate. */
    override fun isInitialized(track: AudioTrack): Boolean = track.state == AudioTrack.STATE_INITIALIZED

    /** Android JNI fills the Java stream field; native track/output verification is separate. */
    override fun reportedStreamType(track: AudioTrack): Int = track.streamType

    /** Strict routing releases a rejected candidate instead of reusing it on another stream. */
    override fun release(track: AudioTrack) = track.release()

}
