package ai.decart.sdk.realtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.PeerConnection
import org.webrtc.RTCStatsCollectorCallback
import org.webrtc.RTCStatsReport
import kotlin.coroutines.resume

/**
 * Polls WebRTC stats from PeerConnection at regular intervals and computes
 * delta metrics (bitrate, packet loss rate, stall detection).
 *
 * Ported from JS SDK's WebRTCStatsCollector.
 */
class WebRTCStatsCollector(
    private val intervalMs: Long = DEFAULT_INTERVAL_MS
) {
    companion object {
        const val DEFAULT_INTERVAL_MS = 1000L
        const val MIN_INTERVAL_MS = 500L
    }

    private var pc: PeerConnection? = null
    private var onStats: ((WebRTCStats) -> Unit)? = null
    private var pollingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Previous values for delta computation
    private var prevBytesVideo: Long = 0
    private var prevBytesAudio: Long = 0
    private var prevBytesSentVideo: Long = 0
    private var prevTimestamp: Long = 0
    private var prevPacketsLostVideo: Long = 0
    private var prevFramesDropped: Int = 0
    private var prevFreezeCount: Int = 0
    private var prevFreezeDuration: Double = 0.0
    private var prevPacketsLostAudio: Long = 0

    /**
     * Attach to a peer connection and start polling.
     */
    fun start(pc: PeerConnection, onStats: (WebRTCStats) -> Unit) {
        stop()
        this.pc = pc
        this.onStats = onStats
        resetPreviousValues()

        val actualInterval = maxOf(intervalMs, MIN_INTERVAL_MS)
        pollingJob = scope.launch {
            while (isActive) {
                collect()
                delay(actualInterval)
            }
        }
    }

    /**
     * Stop polling and release resources.
     */
    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        pc = null
        onStats = null
    }

    fun isRunning(): Boolean = pollingJob?.isActive == true

    private fun resetPreviousValues() {
        prevBytesVideo = 0
        prevBytesAudio = 0
        prevBytesSentVideo = 0
        prevTimestamp = 0
        prevPacketsLostVideo = 0
        prevFramesDropped = 0
        prevFreezeCount = 0
        prevFreezeDuration = 0.0
        prevPacketsLostAudio = 0
    }

    private suspend fun collect() {
        val pc = this.pc ?: return
        val callback = this.onStats ?: return

        try {
            val report = getStats(pc)
            val stats = parse(report)
            callback(stats)
        } catch (_: Exception) {
            // PC might be closed; stop silently
            stop()
        }
    }

    private suspend fun getStats(pc: PeerConnection): RTCStatsReport =
        suspendCancellableCoroutine { cont ->
            pc.getStats(object : RTCStatsCollectorCallback {
                override fun onStatsDelivered(report: RTCStatsReport) {
                    if (cont.isActive) {
                        cont.resume(report)
                    }
                }
            })
        }

    private fun parse(report: RTCStatsReport): WebRTCStats {
        val now = System.nanoTime() / 1_000_000 // ms
        val elapsed = if (prevTimestamp > 0) (now - prevTimestamp) / 1000.0 else 0.0

        var video: VideoStats? = null
        var audio: AudioStats? = null
        var outboundVideo: OutboundVideoStats? = null
        var connection = ConnectionStats(
            currentRoundTripTime = null,
            availableOutgoingBitrate = null
        )

        for ((_, stats) in report.statsMap) {
            val type = stats.type
            val members = stats.members

            if (type == "inbound-rtp" && members["kind"] == "video") {
                val bytesReceived = (members["bytesReceived"] as? Number)?.toLong() ?: 0L
                val bitrate = if (elapsed > 0) ((bytesReceived - prevBytesVideo) * 8 / elapsed).toLong() else 0L
                prevBytesVideo = bytesReceived

                val packetsLost = (members["packetsLost"] as? Number)?.toLong() ?: 0L
                val framesDropped = (members["framesDropped"] as? Number)?.toInt() ?: 0
                val freezeCount = (members["freezeCount"] as? Number)?.toInt() ?: 0
                val freezeDuration = (members["totalFreezesDuration"] as? Number)?.toDouble() ?: 0.0

                video = VideoStats(
                    framesDecoded = (members["framesDecoded"] as? Number)?.toInt() ?: 0,
                    framesDropped = framesDropped,
                    framesPerSecond = (members["framesPerSecond"] as? Number)?.toDouble() ?: 0.0,
                    frameWidth = (members["frameWidth"] as? Number)?.toInt() ?: 0,
                    frameHeight = (members["frameHeight"] as? Number)?.toInt() ?: 0,
                    bytesReceived = bytesReceived,
                    packetsReceived = (members["packetsReceived"] as? Number)?.toLong() ?: 0L,
                    packetsLost = packetsLost,
                    jitter = (members["jitter"] as? Number)?.toDouble() ?: 0.0,
                    bitrate = bitrate,
                    freezeCount = freezeCount,
                    totalFreezesDuration = freezeDuration,
                    packetsLostDelta = maxOf(0L, packetsLost - prevPacketsLostVideo),
                    framesDroppedDelta = maxOf(0, framesDropped - prevFramesDropped),
                    freezeCountDelta = maxOf(0, freezeCount - prevFreezeCount),
                    freezeDurationDelta = maxOf(0.0, freezeDuration - prevFreezeDuration)
                )
                prevPacketsLostVideo = packetsLost
                prevFramesDropped = framesDropped
                prevFreezeCount = freezeCount
                prevFreezeDuration = freezeDuration
            }

            if (type == "outbound-rtp" && members["kind"] == "video") {
                val bytesSent = (members["bytesSent"] as? Number)?.toLong() ?: 0L
                val outBitrate = if (elapsed > 0) ((bytesSent - prevBytesSentVideo) * 8 / elapsed).toLong() else 0L
                prevBytesSentVideo = bytesSent

                outboundVideo = OutboundVideoStats(
                    qualityLimitationReason = (members["qualityLimitationReason"] as? String) ?: "none",
                    bytesSent = bytesSent,
                    packetsSent = (members["packetsSent"] as? Number)?.toLong() ?: 0L,
                    framesPerSecond = (members["framesPerSecond"] as? Number)?.toDouble() ?: 0.0,
                    frameWidth = (members["frameWidth"] as? Number)?.toInt() ?: 0,
                    frameHeight = (members["frameHeight"] as? Number)?.toInt() ?: 0,
                    bitrate = outBitrate
                )
            }

            if (type == "inbound-rtp" && members["kind"] == "audio") {
                val bytesReceived = (members["bytesReceived"] as? Number)?.toLong() ?: 0L
                val bitrate = if (elapsed > 0) ((bytesReceived - prevBytesAudio) * 8 / elapsed).toLong() else 0L
                prevBytesAudio = bytesReceived

                val audioPacketsLost = (members["packetsLost"] as? Number)?.toLong() ?: 0L
                audio = AudioStats(
                    bytesReceived = bytesReceived,
                    packetsReceived = (members["packetsReceived"] as? Number)?.toLong() ?: 0L,
                    packetsLost = audioPacketsLost,
                    jitter = (members["jitter"] as? Number)?.toDouble() ?: 0.0,
                    bitrate = bitrate,
                    packetsLostDelta = maxOf(0L, audioPacketsLost - prevPacketsLostAudio)
                )
                prevPacketsLostAudio = audioPacketsLost
            }

            if (type == "candidate-pair" && members["state"] == "succeeded") {
                connection = ConnectionStats(
                    currentRoundTripTime = (members["currentRoundTripTime"] as? Number)?.toDouble(),
                    availableOutgoingBitrate = (members["availableOutgoingBitrate"] as? Number)?.toDouble()
                )
            }
        }

        prevTimestamp = now

        return WebRTCStats(
            timestamp = System.currentTimeMillis(),
            video = video,
            audio = audio,
            outboundVideo = outboundVideo,
            connection = connection
        )
    }
}
