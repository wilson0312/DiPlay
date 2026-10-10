package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.shilapi.xcertplay.compat.AudioFocusRequestCompat

/** Session-scoped focus options; never writes the user's system stream volume. */
internal data class AudioFocusOptions(
    /** Whether to participate in Android audio focus. */
    val enabled: Boolean,
    /** Preserves the upstream explicit transient-loss option for calls and other focus owners. */
    val muteMediaOnTransientLoss: Boolean = true,
    /** OEM ducking and application ducking must not multiply. */
    val duckingOwner: AudioDuckingOwner = AudioDuckingOwner.APPLICATION,
    /** Session-owned diagnostic destination. */
    val report: (String) -> Unit = {},
)

/** Owns one focus request for all eligible tracks in a CarPlay sink. */
internal class AudioFocusCoordinator(context: Context?, private val options: AudioFocusOptions) {
    private data class Entry(
        /** Protocol-classified role of the track. */
        val channel: AudioChannel,
        /** Focus attributes for this route. */
        val attributes: AudioAttributes,
        /** Last application gain applied to avoid redundant platform calls. */
        var appliedVolume: Float = FULL_VOLUME,
    )

    private val manager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val active = LinkedHashMap<AudioTrack, Entry>()
    private var request: AudioFocusRequestCompat? = null
    private var requestedChannel: AudioChannel? = null
    private var mediaVolume = FULL_VOLUME
    private var closed = false
    private var focusGeneration = 0L
    private var currentListener = listenerFor(focusGeneration)
    internal val listener: AudioManager.OnAudioFocusChangeListener get() = currentListener

    /** Rejects callbacks from abandoned requests before they can affect a new session's tracks. */
    private fun listenerFor(generation: Long) = AudioManager.OnAudioFocusChangeListener { change ->
        synchronized(this) {
            if (generation != focusGeneration || request == null || active.isEmpty()) {
                options.report("Audio: focus change=$change dropped" +
                    " stale=${generation != focusGeneration} noRequest=${request == null}" +
                    " activeTracks=${active.size}")
                return@synchronized
            }
            options.report("Audio: focus change=$change activeTracks=${active.size} duckingOwner=${options.duckingOwner}")
            applyFocusChange(change)
        }
    }

    /** OEM ownership suppresses CAN_DUCK gain changes only; transient call yielding is preserved. */
    private fun applyFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (options.duckingOwner == AudioDuckingOwner.APPLICATION) setMediaVolume(DUCKED_VOLUME)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (options.muteMediaOnTransientLoss) {
                    Log.i(TAG, "Audio: muting media during transient focus loss")
                    setMediaVolume(MUTED_VOLUME)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> setMediaVolume(FULL_VOLUME)
            // Preserve upstream playback on permanent loss: some cars never send a later GAIN.
        }
    }

    /** Navigation overlays audio without acquiring or changing this focus request. */
    @Synchronized
    fun acquire(track: AudioTrack, channel: AudioChannel, attributes: AudioAttributes) {
        if (closed || !options.enabled || manager == null || channel == AudioChannel.NAVIGATION) return
        active[track] = Entry(channel, attributes)
        refreshRequest()
        applyMediaVolume(track, active.getValue(track))
    }

    /** Abandons focus only when its last eligible track has gone. */
    @Synchronized
    fun release(track: AudioTrack) {
        if (active.remove(track) != null) refreshRequest()
    }

    /** Accepts focus changes forwarded by the session's existing media-key handler. */
    fun onExternalFocusChange(change: Int) {
        val current = synchronized(this) { currentListener }
        current.onAudioFocusChange(change)
    }

    /** A closed coordinator rejects late workers and queued focus callbacks. */
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        active.clear()
        refreshRequest()
    }

    /** Replaces requests only when the primary role changes. */
    private fun refreshRequest() {
        val primary = active.values.maxByOrNull { it.channel.focusPriority() }
        if (primary == null) {
            abandonRequest()
            return
        }
        if (request != null && requestedChannel == primary.channel) return
        focusGeneration += 1
        manager?.let { request?.abandon(it) }
        val gain = focusGain(primary.channel) ?: return
        currentListener = listenerFor(focusGeneration)
        val next = AudioFocusRequestCompat(gain, primary.attributes, currentListener, Handler(Looper.getMainLooper()))
        request = next
        requestedChannel = primary.channel
        val result = manager?.let(next::request)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) setMediaVolume(FULL_VOLUME)
        val line = "Audio: focus requested channel=${primary.channel} gain=$gain granted=$result activeTracks=${active.size}"
        Log.i(TAG, line)
        options.report(line)
    }

    /** Clears local gain state without resetting any OEM volume index. */
    private fun abandonRequest() {
        focusGeneration += 1
        val abandoned = request
        request = null
        requestedChannel = null
        mediaVolume = FULL_VOLUME
        manager?.let { abandoned?.abandon(it) }
    }

    /** Only application gain is shared across current music tracks. */
    private fun setMediaVolume(volume: Float) {
        mediaVolume = volume
        active.forEach { (track, entry) -> applyMediaVolume(track, entry) }
    }

    /** Phone/assistant stay audible; platform gain failures surface rather than being discarded. */
    @Suppress("DEPRECATION")
    private fun applyMediaVolume(track: AudioTrack, entry: Entry) {
        if (entry.channel != AudioChannel.MEDIA || entry.appliedVolume == mediaVolume) return
        val result = track.setStereoVolume(mediaVolume, mediaVolume)
        check(result == AudioTrack.SUCCESS) { "Audio: music gain $mediaVolume rejected, result=$result" }
        entry.appliedVolume = mediaVolume
    }

    private fun focusGain(channel: AudioChannel): Int? = when (channel) {
        AudioChannel.MEDIA -> AudioManager.AUDIOFOCUS_GAIN
        AudioChannel.PHONE -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
        AudioChannel.ASSISTANT -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        AudioChannel.NAVIGATION -> null
    }

    private fun AudioChannel.focusPriority(): Int = when (this) {
        AudioChannel.MEDIA -> MEDIA_PRIORITY
        AudioChannel.PHONE -> PHONE_PRIORITY
        AudioChannel.ASSISTANT -> ASSISTANT_PRIORITY
        AudioChannel.NAVIGATION -> NAVIGATION_PRIORITY
    }

    private companion object {
        const val TAG = "DiPlay-AudioFocus"
        const val FULL_VOLUME = 1f
        const val MUTED_VOLUME = 0f
        const val DUCKED_VOLUME = 0.2f
        const val MEDIA_PRIORITY = 3
        const val PHONE_PRIORITY = 2
        const val ASSISTANT_PRIORITY = 1
        const val NAVIGATION_PRIORITY = 0
    }
}
