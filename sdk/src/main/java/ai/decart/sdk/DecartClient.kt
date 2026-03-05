package ai.decart.sdk

import ai.decart.sdk.realtime.RealTimeClient
import ai.decart.sdk.realtime.RealTimeClientConfig
import android.content.Context

/**
 * Configuration for the Decart client.
 */
data class DecartClientConfig(
    val apiKey: String,
    val baseUrl: String = "wss://api.decart.ai",
    val logLevel: LogLevel = LogLevel.WARN
)

/**
 * Top-level entry point for the Decart SDK.
 *
 * Usage:
 * ```kotlin
 * val client = DecartClient(context, DecartClientConfig(apiKey = "your-api-key"))
 * val realtime = client.realtime
 * realtime.connect(...)
 * ```
 */
class DecartClient(
    context: Context,
    config: DecartClientConfig
) {
    init {
        require(config.apiKey.isNotBlank()) {
            "Missing API key. Pass apiKey to DecartClientConfig or set the DECART_API_KEY environment variable."
        }
    }

    private val logger: Logger = AndroidLogger(config.logLevel)

    /**
     * The realtime client for streaming camera video through AI models.
     */
    val realtime: RealTimeClient = RealTimeClient(
        context = context,
        config = RealTimeClientConfig(
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            logger = logger
        )
    )

    /**
     * Release all resources held by this client.
     */
    fun release() {
        realtime.release()
    }
}
