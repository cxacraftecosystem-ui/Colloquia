package com.transcriptai.app.recording

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Pulls the audio track out of a video file into a standalone .m4a using MediaExtractor + MediaMuxer
 * (a stream copy — no re-encode, so it's fast and lossless). This is how a picked video is "stripped"
 * to audio on-device before upload, so only the audio is sent and transcribed.
 */
object AudioExtract {

    fun extractAudio(input: File, outDir: File): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(input.absolutePath)
        var audioTrack = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrack = i
                format = f
                break
            }
        }
        if (audioTrack < 0 || format == null) {
            extractor.release()
            throw IllegalStateException("No audio track found in the selected file.")
        }
        extractor.selectTrack(audioTrack)

        outDir.mkdirs()
        val out = File(outDir, "extracted_${System.currentTimeMillis()}.m4a")
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val dstTrack = muxer.addTrack(format)
        muxer.start()

        val maxChunk = format.let {
            if (it.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) it.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) else 1 shl 20
        }.coerceAtLeast(256 * 1024)
        val buffer = ByteBuffer.allocate(maxChunk)
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(dstTrack, buffer, info)
                extractor.advance()
            }
        } finally {
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
            extractor.release()
        }
        if (out.length() <= 0L) throw IllegalStateException("Could not extract audio from the file.")
        return out
    }
}
