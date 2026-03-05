package ai.decart.sdk

import org.junit.Assert.*
import org.junit.Test

class ErrorClassifierTest {

    @Test
    fun `classifies websocket error`() {
        val error = ErrorClassifier.classifyWebrtcError(Exception("WebSocket error: connection refused"))
        assertEquals(ErrorCodes.WEBRTC_WEBSOCKET_ERROR, error.code)
    }

    @Test
    fun `classifies ICE error`() {
        val error = ErrorClassifier.classifyWebrtcError(Exception("ICE connection failed"))
        assertEquals(ErrorCodes.WEBRTC_ICE_ERROR, error.code)
    }

    @Test
    fun `classifies timeout error with ms`() {
        val error = ErrorClassifier.classifyWebrtcError(Exception("Connection timed out after 30000ms"))
        assertEquals(ErrorCodes.WEBRTC_TIMEOUT_ERROR, error.code)
        assertEquals(30000, error.data?.get("timeoutMs"))
    }

    @Test
    fun `classifies timeout error without ms`() {
        val error = ErrorClassifier.classifyWebrtcError(Exception("Connection timeout"))
        assertEquals(ErrorCodes.WEBRTC_TIMEOUT_ERROR, error.code)
    }

    @Test
    fun `classifies server error`() {
        val error = ErrorClassifier.classifyWebrtcError(Exception("insufficient credits"), source = "server")
        assertEquals(ErrorCodes.WEBRTC_SERVER_ERROR, error.code)
        assertEquals("insufficient credits", error.message)
    }

    @Test
    fun `defaults to signaling error`() {
        val error = ErrorClassifier.classifyWebrtcError(Exception("something unknown happened"))
        assertEquals(ErrorCodes.WEBRTC_SIGNALING_ERROR, error.code)
    }

    @Test
    fun `handles null message`() {
        val error = ErrorClassifier.classifyWebrtcError(Exception())
        assertEquals(ErrorCodes.WEBRTC_SIGNALING_ERROR, error.code)
    }
}
