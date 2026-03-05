package ai.decart.sdk.realtime

/**
 * WebRTC stats snapshot collected from PeerConnection.getStats().
 * Ported from JS SDK's WebRTCStats type.
 */
data class WebRTCStats(
    val timestamp: Long,
    val video: VideoStats?,
    val audio: AudioStats?,
    val outboundVideo: OutboundVideoStats?,
    val connection: ConnectionStats
)

data class VideoStats(
    val framesDecoded: Int,
    val framesDropped: Int,
    val framesPerSecond: Double,
    val frameWidth: Int,
    val frameHeight: Int,
    val bytesReceived: Long,
    val packetsReceived: Long,
    val packetsLost: Long,
    val jitter: Double,
    /** Estimated inbound bitrate in bits/sec, computed from bytesReceived delta. */
    val bitrate: Long,
    val freezeCount: Int,
    val totalFreezesDuration: Double,
    /** Delta: packets lost since previous sample. */
    val packetsLostDelta: Long,
    /** Delta: frames dropped since previous sample. */
    val framesDroppedDelta: Int,
    /** Delta: freeze count since previous sample. */
    val freezeCountDelta: Int,
    /** Delta: freeze duration (seconds) since previous sample. */
    val freezeDurationDelta: Double
)

data class AudioStats(
    val bytesReceived: Long,
    val packetsReceived: Long,
    val packetsLost: Long,
    val jitter: Double,
    /** Estimated inbound bitrate in bits/sec, computed from bytesReceived delta. */
    val bitrate: Long,
    /** Delta: packets lost since previous sample. */
    val packetsLostDelta: Long
)

data class OutboundVideoStats(
    /** Why the encoder is limiting quality: "none", "bandwidth", "cpu", or "other". */
    val qualityLimitationReason: String,
    val bytesSent: Long,
    val packetsSent: Long,
    val framesPerSecond: Double,
    val frameWidth: Int,
    val frameHeight: Int,
    /** Estimated outbound bitrate in bits/sec, computed from bytesSent delta. */
    val bitrate: Long
)

data class ConnectionStats(
    /** Current round-trip time in seconds, or null if unavailable. */
    val currentRoundTripTime: Double?,
    /** Available outgoing bitrate estimate in bits/sec, or null if unavailable. */
    val availableOutgoingBitrate: Double?
)
