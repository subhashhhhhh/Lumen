package com.subhashh.lumen.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.roundToInt

/**
 * Decodes chosen keyframes of a video fast: one hardware decoder for the whole video, fed only
 * those keyframes, its output drawn by the GPU at the size we want (colour conversion, scaling
 * and rotation in one pass) and read back as a bitmap.
 *
 * MediaMetadataRetriever.getScaledFrameAtTime did the same one frame at a time, setting up and
 * converting on the CPU for each: ~150 ms a frame on the edge 50, half the faces pass. Anything
 * this can't handle throws, and [MediaFrames] falls back to that for the video.
 *
 * Not thread-hopping: the GL context belongs to the thread that made it, so callers run this on
 * one thread throughout (see [MediaFrames]).
 */
internal class KeyframeDecoder(private val context: Context, private val uri: Uri) {

    /**
     * Calls [onFrame] with each decoded keyframe at or after the requested [timesMs] (as the
     * extractor finds them), at most [px] on its long side, the right way up. Stops early if it
     * returns false. Returns how many frames it delivered.
     */
    fun forEach(timesMs: List<Long>, px: Int, onFrame: (atMs: Long, frame: Bitmap) -> Boolean): Int {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var gl: GlFrames? = null
        var delivered = 0
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).first {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            val (shownW, shownH) = if (rotation % 180 == 0) width to height else height to width
            val scale = minOf(1f, px.toFloat() / maxOf(shownW, shownH))
            val outW = (shownW * scale).roundToInt().coerceAtLeast(2)
            val outH = (shownH * scale).roundToInt().coerceAtLeast(2)

            // HDR (camera videos, mostly): ask the decoder to map it to normal colour, as the
            // frame-by-frame path does; washed-out frames would skew Smart Search's colours.
            // The extractor often leaves the colour fields out (they're inside the stream), so a
            // 10-bit profile or BT.2020 colours count as HDR too; the decoder's output says for sure.
            val transfer = format.getIntegerOr(MediaFormat.KEY_COLOR_TRANSFER, 0)
            val hdr = transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG ||
                format.getIntegerOr(MediaFormat.KEY_COLOR_STANDARD, 0) == MediaFormat.COLOR_STANDARD_BT2020 ||
                format.getIntegerOr(MediaFormat.KEY_PROFILE, 0) in tenBitProfiles(mime)
            if (hdr) format.setInteger(MediaFormat.KEY_COLOR_TRANSFER_REQUEST, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
            gl = GlFrames(outW, outH)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, gl.surface, null, 0)
                start()
            }
            val info = MediaCodec.BufferInfo()
            val times = timesMs.iterator()
            var inputDone = false
            var lastOutput = SystemClock.elapsedRealtime()
            while (true) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TimeoutUs)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = if (times.hasNext()) {
                            extractor.seekTo(times.next() * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            extractor.readSampleData(buffer, 0)
                        } else -1
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TimeoutUs)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Still HDR coming out (the decoder can't tone-map): the slow path does it.
                    val now = codec.outputFormat.getIntegerOr(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                    if (now == MediaFormat.COLOR_TRANSFER_ST2084 || now == MediaFormat.COLOR_TRANSFER_HLG) error("Decoder gives HDR frames")
                } else if (out >= 0) {
                    lastOutput = SystemClock.elapsedRealtime()
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val render = info.size > 0
                    codec.releaseOutputBuffer(out, render)
                    if (render) {
                        if (!gl.awaitFrame(FrameTimeoutMs)) error("No frame from the decoder")
                        delivered++
                        if (!onFrame(info.presentationTimeUs / 1000, gl.read())) return delivered
                    }
                    if (end) return delivered
                } else if (SystemClock.elapsedRealtime() - lastOutput > StallMs) {
                    error("Decoder stalled")
                }
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            gl?.close()
            extractor.release()
        }
    }

    private companion object {
        /** 10-bit profiles: what phones record HDR in. */
        fun tenBitProfiles(mime: String): Set<Int> = when (mime) {
            MediaFormat.MIMETYPE_VIDEO_HEVC -> setOf(
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            )
            MediaFormat.MIMETYPE_VIDEO_AV1 -> setOf(
                android.media.MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10,
                android.media.MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10,
                android.media.MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus,
            )
            MediaFormat.MIMETYPE_VIDEO_VP9 -> setOf(
                android.media.MediaCodecInfo.CodecProfileLevel.VP9Profile2,
                android.media.MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR,
                android.media.MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus,
            )
            else -> emptySet()
        }

        const val TimeoutUs = 10_000L
        const val FrameTimeoutMs = 2_000L
        const val StallMs = 5_000L
    }
}

/**
 * An offscreen GL surface [width]×[height] (already sized for the video's rotation) that a
 * decoder renders into ([surface]); [read] draws the latest frame and returns it.
 */
private class GlFrames(private val width: Int, private val height: Int) : AutoCloseable {
    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private val eglContext: EGLContext
    private val eglSurface: EGLSurface
    private val program: Int
    private val texture: Int
    private val surfaceTexture: SurfaceTexture
    val surface: Surface
    private val callbacks = HandlerThread("lumen-frames").apply { start() }
    private val lock = Object()
    private var available = false
    private val matrix = FloatArray(16)
    private val pixels: ByteBuffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
    private val vertices: FloatBuffer
    private val texCoords: FloatBuffer

    init {
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        EGL14.eglChooseConfig(
            display,
            intArrayOf(
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE,
            ),
            0, configs, 0, 1, count, 0,
        )
        check(count[0] > 0) { "No EGL config" }
        eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        eglSurface = EGL14.eglCreatePbufferSurface(display, configs[0], intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent" }

        program = link(
            """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
            """,
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES sTexture;
            void main() { gl_FragColor = texture2D(sTexture, vTexCoord); }
            """,
        )
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texture = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        surfaceTexture = SurfaceTexture(texture).apply {
            setOnFrameAvailableListener({
                synchronized(lock) {
                    available = true
                    lock.notifyAll()
                }
            }, Handler(callbacks.looper))
        }
        surface = Surface(surfaceTexture)

        // A full-screen quad. glReadPixels returns the bottom row first, so the image is drawn
        // upside down: its top at the bottom of the framebuffer.
        val corners = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        vertices = floats(corners)
        // No turning here: a decoder drawing to a Surface already applies the video's rotation
        // (it's in the SurfaceTexture's transform); only the output size is swapped for it.
        texCoords = floats(FloatArray(8) { i ->
            // Texture coordinates count up from the bottom.
            if (i % 2 == 0) (corners[i] + 1) / 2 else 1 - (corners[i] + 1) / 2
        })
    }

    /** Waits for the decoder's next frame and latches it into the texture. */
    fun awaitFrame(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        synchronized(lock) {
            while (!available) {
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0) return false
                lock.wait(left)
            }
            available = false
        }
        surfaceTexture.updateTexImage()
        return true
    }

    fun read(): Bitmap {
        surfaceTexture.getTransformMatrix(matrix)
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val coord = GLES20.glGetAttribLocation(program, "aTexCoord")
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glEnableVertexAttribArray(coord)
        GLES20.glVertexAttribPointer(coord, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, matrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        pixels.rewind()
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
        pixels.rewind()
        // ARGB_8888 bitmaps hold their pixels as RGBA bytes: the buffer copies straight in.
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(pixels) }
    }

    override fun close() {
        surface.release()
        surfaceTexture.release()
        GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        GLES20.glDeleteProgram(program)
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(display, eglSurface)
        EGL14.eglDestroyContext(display, eglContext)
        EGL14.eglTerminate(display)
        callbacks.quitSafely()
    }

    private fun floats(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            rewind()
        }

    private fun link(vertex: String, fragment: String): Int {
        fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Shader: ${GLES20.glGetShaderInfoLog(it)}" }
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vertex))
            GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(it)
        }
    }
}

private fun MediaFormat.getIntegerOr(key: String, default: Int) = if (containsKey(key)) getInteger(key) else default
