package ai.decart.sdk

data class RealtimeModel(
    val name: String,
    val urlPath: String,
    val fps: Int,
    val width: Int,
    val height: Int
)

object RealtimeModels {
    val MIRAGE = RealtimeModel("mirage", "/v1/stream", 25, 1280, 704)
    val MIRAGE_V2 = RealtimeModel("mirage_v2", "/v1/stream", 22, 1280, 704)
    val LUCY_V2V_720P_RT = RealtimeModel("lucy_v2v_720p_rt", "/v1/stream", 25, 1280, 704)
    val LUCY_2_RT = RealtimeModel("lucy_2_rt", "/v1/stream", 20, 1280, 720)
    val LIVE_AVATAR = RealtimeModel("live_avatar", "/v1/stream", 25, 1280, 720)

    /** Get model by name, or null if not found */
    fun fromName(name: String): RealtimeModel? = when (name) {
        "mirage" -> MIRAGE
        "mirage_v2" -> MIRAGE_V2
        "lucy_v2v_720p_rt" -> LUCY_V2V_720P_RT
        "lucy_2_rt" -> LUCY_2_RT
        "live_avatar" -> LIVE_AVATAR
        else -> null
    }

    /** All available realtime models */
    val all: List<RealtimeModel> = listOf(MIRAGE, MIRAGE_V2, LUCY_V2V_720P_RT, LUCY_2_RT, LIVE_AVATAR)
}
