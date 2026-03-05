package ai.decart.sdk.realtime

/**
 * SDP manipulation utilities for WebRTC codec and bitrate configuration.
 * Ports the SDP logic from the JS SDK's WebRTCConnection.
 */
object SdpUtils {

    /**
     * Reorder codecs in the SDP to prefer VP8.
     * Modifies the m=video line to put VP8 payload types first.
     *
     * @param sdp The original SDP string
     * @return Modified SDP with VP8 preferred
     */
    fun preferVP8(sdp: String): String {
        val lines = sdp.split("\r\n").toMutableList()

        // Find VP8 payload type(s) from rtpmap lines
        val vp8PayloadTypes = mutableListOf<String>()
        for (line in lines) {
            val match = Regex("^a=rtpmap:(\\d+) VP8/90000").find(line)
            if (match != null) {
                vp8PayloadTypes.add(match.groupValues[1])
            }
        }

        if (vp8PayloadTypes.isEmpty()) return sdp

        // Reorder the m=video line
        for (i in lines.indices) {
            if (lines[i].startsWith("m=video")) {
                val parts = lines[i].split(" ").toMutableList()
                // m=video PORT PROTO PT1 PT2 PT3...
                // Parts: [m=video, port, proto, pt1, pt2, ...]
                if (parts.size > 3) {
                    val header = parts.subList(0, 3)
                    val payloadTypes = parts.subList(3, parts.size).toMutableList()

                    // Move VP8 payload types to the front
                    val reordered = mutableListOf<String>()
                    reordered.addAll(vp8PayloadTypes.filter { it in payloadTypes })
                    reordered.addAll(payloadTypes.filter { it !in vp8PayloadTypes })

                    lines[i] = (header + reordered).joinToString(" ")
                }
                break
            }
        }

        return lines.joinToString("\r\n")
    }

    /**
     * Inject VP8 bitrate parameters into the SDP.
     * Adds x-google-min-bitrate and x-google-start-bitrate to the VP8 fmtp line.
     *
     * Ported from JS SDK's modifyVP8Bitrate method.
     *
     * @param sdp The original SDP string
     * @param minBitrateKbps Minimum bitrate in kbps (e.g., 300)
     * @param startBitrateKbps Start bitrate in kbps (e.g., 600)
     * @return Modified SDP with bitrate parameters
     */
    fun injectBitrate(sdp: String, minBitrateKbps: Int, startBitrateKbps: Int): String {
        if (minBitrateKbps == 0 && startBitrateKbps == 0) return sdp

        val bitrateParams = "x-google-min-bitrate=$minBitrateKbps;x-google-start-bitrate=$startBitrateKbps"
        val sdpLines = sdp.split("\r\n")
        val modifiedLines = mutableListOf<String>()

        var i = 0
        while (i < sdpLines.size) {
            val line = sdpLines[i]

            // Look for VP8 codec line (e.g., "a=rtpmap:96 VP8/90000")
            if (line.contains("VP8/90000")) {
                val match = Regex("a=rtpmap:(\\d+) VP8").find(line)
                if (match != null) {
                    val payloadType = match.groupValues[1]

                    // Find the range of lines for this payload type and where to insert fmtp
                    var fmtpIndex = -1
                    var insertAfterIndex = i // Default: insert after rtpmap line

                    var j = i + 1
                    while (j < sdpLines.size && sdpLines[j].startsWith("a=")) {
                        // Check if fmtp already exists
                        if (sdpLines[j].startsWith("a=fmtp:$payloadType")) {
                            fmtpIndex = j
                            break
                        }
                        // Update insert position to after rtcp-fb lines for this payload
                        if (sdpLines[j].startsWith("a=rtcp-fb:$payloadType")) {
                            insertAfterIndex = j
                        }
                        // Stop at next rtpmap (different codec)
                        if (sdpLines[j].startsWith("a=rtpmap:")) {
                            break
                        }
                        j++
                    }

                    if (fmtpIndex != -1) {
                        // fmtp line exists, append bitrate params to it
                        val fmtpLine = sdpLines[fmtpIndex]
                        if (!fmtpLine.contains("x-google-min-bitrate")) {
                            modifiedLines.add(line) // add current rtpmap line
                            i++
                            while (i < fmtpIndex) {
                                modifiedLines.add(sdpLines[i])
                                i++
                            }
                            modifiedLines.add("$fmtpLine;$bitrateParams")
                            i++ // skip the original fmtp line
                            continue
                        }
                    } else {
                        // No fmtp line exists, insert new one after all rtcp-fb lines
                        while (i <= insertAfterIndex) {
                            modifiedLines.add(sdpLines[i])
                            i++
                        }
                        // Insert the new fmtp line
                        modifiedLines.add("a=fmtp:$payloadType $bitrateParams")
                        continue
                    }
                }
            }
            modifiedLines.add(line)
            i++
        }

        return modifiedLines.joinToString("\r\n")
    }
}
