package ai.decart.sdk.realtime

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages an audio stream for live_avatar mode.
 * Creates a WebRTC audio track that sends audio to the server.
 * Supports playing audio files through the device speaker.
 *
 * Ported from JS SDK's AudioStreamManager.
 */
class AudioStreamManager(
    private val context: Context,
    private val factory: PeerConnectionFactory
) {
    private val audioConstraints = MediaConstraints()
    private val audioSource: AudioSource = factory.createAudioSource(audioConstraints)
    private val _audioTrack: AudioTrack = factory.createAudioTrack("audio_stream", audioSource)

    private var mediaPlayer: MediaPlayer? = null
    private var _isPlaying = false

    init {
        // Enable the audio track
        _audioTrack.setEnabled(true)
    }

    /**
     * Get the WebRTC AudioTrack to add to the PeerConnection.
     */
    fun getAudioTrack(): AudioTrack = _audioTrack

    /**
     * Check if audio is currently playing.
     */
    fun isPlaying(): Boolean = _isPlaying

    /**
     * Play audio through the device speaker.
     * When the audio ends, playback stops automatically.
     *
     * @param audioData Raw audio data (WAV, MP3, etc.)
     * @return Completes when audio finishes playing
     */
    suspend fun playAudio(audioData: ByteArray) {
        // Stop any currently playing audio
        if (_isPlaying) {
            stopAudio()
        }

        // Write audio data to a temp file for MediaPlayer
        val tempFile = File.createTempFile("decart_audio_", ".wav", context.cacheDir)
        tempFile.deleteOnExit()

        FileOutputStream(tempFile).use { fos ->
            fos.write(audioData)
        }

        suspendCancellableCoroutine { cont ->
            val player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build())
                setDataSource(tempFile.absolutePath)
                prepare()
            }

            mediaPlayer = player
            _isPlaying = true

            player.setOnCompletionListener {
                _isPlaying = false
                mediaPlayer = null
                player.release()
                tempFile.delete()
                if (cont.isActive) cont.resume(Unit)
            }

            player.setOnErrorListener { _, what, extra ->
                _isPlaying = false
                mediaPlayer = null
                player.release()
                tempFile.delete()
                if (cont.isActive) cont.resumeWithException(
                    Exception("MediaPlayer error: what=$what extra=$extra")
                )
                true
            }

            player.start()

            cont.invokeOnCancellation {
                stopAudio()
                tempFile.delete()
            }
        }
    }

    /**
     * Stop currently playing audio immediately.
     */
    fun stopAudio() {
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (_: Exception) {
                // Ignore if already stopped/released
            }
            mediaPlayer = null
        }
        _isPlaying = false
    }

    /**
     * Clean up all resources.
     */
    fun cleanup() {
        stopAudio()
        _audioTrack.setEnabled(false)
        _audioTrack.dispose()
        audioSource.dispose()
    }
}
