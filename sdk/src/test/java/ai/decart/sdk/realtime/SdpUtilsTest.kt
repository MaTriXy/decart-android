package ai.decart.sdk.realtime

import org.junit.Assert.*
import org.junit.Test

class SdpUtilsTest {

    private val sampleSdp = listOf(
        "v=0",
        "o=- 123456 2 IN IP4 127.0.0.1",
        "s=-",
        "t=0 0",
        "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100",
        "c=IN IP4 0.0.0.0",
        "a=rtpmap:96 VP8/90000",
        "a=rtcp-fb:96 nack",
        "a=rtcp-fb:96 nack pli",
        "a=rtcp-fb:96 goog-remb",
        "a=rtpmap:97 H264/90000",
        "a=fmtp:97 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
        "a=rtpmap:98 VP9/90000",
        "a=rtpmap:99 H264/90000",
        "a=fmtp:99 level-asymmetry-allowed=1;packetization-mode=0;profile-level-id=42e01f",
        "a=rtpmap:100 red/90000",
        ""
    ).joinToString("\r\n")

    @Test
    fun `preferVP8 puts VP8 payload type first in m-line`() {
        // VP8 is already payload 96 which is first, but let's test with a different order
        val sdpH264First = sampleSdp.replace(
            "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99 100",
            "m=video 9 UDP/TLS/RTP/SAVPF 97 98 99 96 100"
        )
        val result = SdpUtils.preferVP8(sdpH264First)
        val mLine = result.split("\r\n").first { it.startsWith("m=video") }
        // VP8 payload type 96 should be first after the protocol
        assertTrue("VP8 payload 96 should be first: $mLine", mLine.contains("SAVPF 96"))
    }

    @Test
    fun `preferVP8 no-ops when VP8 is already preferred`() {
        val result = SdpUtils.preferVP8(sampleSdp)
        assertEquals(sampleSdp, result)
    }

    @Test
    fun `preferVP8 handles SDP without VP8`() {
        val noVP8 = sampleSdp.replace("VP8/90000", "VP666/90000")
        val result = SdpUtils.preferVP8(noVP8)
        assertEquals(noVP8, result)
    }

    @Test
    fun `injectBitrate inserts new fmtp line when none exists`() {
        val result = SdpUtils.injectBitrate(sampleSdp, 300, 600)
        val lines = result.split("\r\n")
        val fmtpLine = lines.firstOrNull { it.startsWith("a=fmtp:96") }
        assertNotNull("Should have inserted fmtp line for VP8 payload 96", fmtpLine)
        assertTrue("Should contain min-bitrate", fmtpLine!!.contains("x-google-min-bitrate=300"))
        assertTrue("Should contain start-bitrate", fmtpLine.contains("x-google-start-bitrate=600"))
    }

    @Test
    fun `injectBitrate inserts fmtp after rtcp-fb lines`() {
        val result = SdpUtils.injectBitrate(sampleSdp, 300, 600)
        val lines = result.split("\r\n")
        val fmtpIndex = lines.indexOfFirst { it.startsWith("a=fmtp:96") }
        val lastRtcpFbIndex = lines.indexOfLast { it.startsWith("a=rtcp-fb:96") }
        assertTrue("fmtp should be after rtcp-fb lines", fmtpIndex > lastRtcpFbIndex)
    }

    @Test
    fun `injectBitrate appends to existing fmtp line`() {
        val sdpWithFmtp = sampleSdp.replace(
            "a=rtcp-fb:96 goog-remb",
            "a=rtcp-fb:96 goog-remb\r\na=fmtp:96 apt=97"
        )
        val result = SdpUtils.injectBitrate(sdpWithFmtp, 300, 600)
        val fmtpLine = result.split("\r\n").first { it.startsWith("a=fmtp:96") }
        assertTrue("Should append to existing fmtp", fmtpLine.contains("apt=97"))
        assertTrue("Should contain bitrate params", fmtpLine.contains("x-google-min-bitrate=300"))
    }

    @Test
    fun `injectBitrate does not duplicate bitrate params`() {
        val sdpWithBitrate = sampleSdp.replace(
            "a=rtcp-fb:96 goog-remb",
            "a=rtcp-fb:96 goog-remb\r\na=fmtp:96 x-google-min-bitrate=200;x-google-start-bitrate=400"
        )
        val result = SdpUtils.injectBitrate(sdpWithBitrate, 300, 600)
        val fmtpLine = result.split("\r\n").first { it.startsWith("a=fmtp:96") }
        // Should NOT have added another x-google-min-bitrate
        val count = Regex("x-google-min-bitrate").findAll(fmtpLine).count()
        assertEquals("Should not duplicate bitrate params", 1, count)
    }

    @Test
    fun `injectBitrate no-ops when both values are zero`() {
        val result = SdpUtils.injectBitrate(sampleSdp, 0, 0)
        assertEquals(sampleSdp, result)
    }

    @Test
    fun `injectBitrate preserves other codec fmtp lines`() {
        val result = SdpUtils.injectBitrate(sampleSdp, 300, 600)
        val lines = result.split("\r\n")
        // H264 fmtp lines should be unchanged
        val h264Fmtp = lines.filter { it.startsWith("a=fmtp:97") || it.startsWith("a=fmtp:99") }
        assertEquals("H264 fmtp lines should be preserved", 2, h264Fmtp.size)
    }
}
