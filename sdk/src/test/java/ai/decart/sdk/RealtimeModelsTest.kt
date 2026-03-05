package ai.decart.sdk

import org.junit.Assert.*
import org.junit.Test

class RealtimeModelsTest {

    @Test
    fun `all models have correct count`() {
        assertEquals(5, RealtimeModels.all.size)
    }

    @Test
    fun `fromName returns correct model`() {
        assertEquals(RealtimeModels.MIRAGE, RealtimeModels.fromName("mirage"))
        assertEquals(RealtimeModels.LIVE_AVATAR, RealtimeModels.fromName("live_avatar"))
    }

    @Test
    fun `fromName returns null for unknown`() {
        assertNull(RealtimeModels.fromName("nonexistent_model"))
    }

    @Test
    fun `all models use v1 stream urlPath`() {
        RealtimeModels.all.forEach { model ->
            assertEquals("/v1/stream", model.urlPath)
        }
    }

    @Test
    fun `model dimensions are valid`() {
        RealtimeModels.all.forEach { model ->
            assertTrue("${model.name} width should be > 0", model.width > 0)
            assertTrue("${model.name} height should be > 0", model.height > 0)
            assertTrue("${model.name} fps should be > 0", model.fps > 0)
        }
    }
}
