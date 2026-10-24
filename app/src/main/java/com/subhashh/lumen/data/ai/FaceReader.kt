package com.subhashh.lumen.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Finds the faces in a photo and gives each a fingerprint ([FaceModel]), in three steps:
 * 1. **YuNet** marks faces: a box, a confidence and 5 landmarks (eyes, nose tip, mouth corners).
 * 2. Each face is **straightened** onto a 112×112 template from its landmarks (the rotation, scale
 *    and shift that fit them best), the way the recognizer was trained.
 * 3. **SFace** turns it into 128 numbers; the same person's faces point the same way.
 *
 * Same steps as tools/faces/face_compare.py, which checks them on the Mac.
 */
class FaceReader(dir: File, threads: Int) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val det = env.createSession(File(dir, "yunet.onnx").path, options)
    private val rec = env.createSession(File(dir, "sface.onnx").path, options)

    /** A face: its box as fractions of the photo ([left, top, right, bottom]) and fingerprint. */
    class Found(val box: FloatArray, val vector: FloatArray)

    fun read(original: Bitmap): List<Found> {
        val image = if (original.config == Bitmap.Config.HARDWARE) original.copy(Bitmap.Config.ARGB_8888, false) else original
        try {
            return detect(image).map { (box, landmarks) ->
                val face = straighten(image, landmarks)
                val vector = embed(face)
                face.recycle()
                Found(
                    floatArrayOf(box[0] / image.width, box[1] / image.height, box[2] / image.width, box[3] / image.height),
                    vector,
                )
            }
        } finally {
            if (image !== original) image.recycle()
        }
    }

    /** Boxes in pixels and their 5 landmarks (x0, y0, … x4, y4). */
    private fun detect(image: Bitmap): List<Pair<FloatArray, FloatArray>> {
        val w = image.width
        val h = image.height
        val pw = (w + 31) / 32 * 32
        val ph = (h + 31) / 32 * 32
        val argb = IntArray(w * h)
        image.getPixels(argb, 0, w, 0, 0, w, h)
        // BGR, raw 0–255, padded right and bottom with black.
        val plane = pw * ph
        val input = FloatArray(3 * plane)
        for (y in 0 until h) for (x in 0 until w) {
            val p = argb[y * w + x]
            val i = y * pw + x
            input[i] = (p and 0xFF).toFloat()
            input[plane + i] = (p shr 8 and 0xFF).toFloat()
            input[2 * plane + i] = (p shr 16 and 0xFF).toFloat()
        }
        val candidates = ArrayList<Candidate>()
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, ph.toLong(), pw.toLong())).use { x ->
            det.run(mapOf("input" to x)).use { result ->
                fun out(name: String): FloatBuffer = (result.get(name).get() as OnnxTensor).floatBuffer
                for (stride in intArrayOf(8, 16, 32)) {
                    val cols = pw / stride
                    val cells = cols * (ph / stride)
                    val cls = out("cls_$stride")
                    val obj = out("obj_$stride")
                    val bbox = out("bbox_$stride")
                    val kps = out("kps_$stride")
                    for (i in 0 until cells) {
                        val score = sqrt(cls[i].coerceIn(0f, 1f) * obj[i].coerceIn(0f, 1f))
                        if (score < MinScore) continue
                        val r = i / cols
                        val c = i % cols
                        val cx = (c + bbox[4 * i]) * stride
                        val cy = (r + bbox[4 * i + 1]) * stride
                        val bw = exp(bbox[4 * i + 2]) * stride
                        val bh = exp(bbox[4 * i + 3]) * stride
                        val landmarks = FloatArray(10) { k ->
                            if (k % 2 == 0) (kps[10 * i + k] + c) * stride else (kps[10 * i + k] + r) * stride
                        }
                        candidates += Candidate(floatArrayOf(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2), score, landmarks)
                    }
                }
            }
        }
        // Overlapping boxes for one face: keep the most confident.
        val kept = ArrayList<Candidate>()
        for (c in candidates.sortedByDescending { it.score }) {
            if (kept.none { iou(it.box, c.box) > MaxOverlap }) kept += c
        }
        return kept.filter { min(it.box[2] - it.box[0], it.box[3] - it.box[1]) >= MinFacePx }.map { it.box to it.landmarks }
    }

    private class Candidate(val box: FloatArray, val score: Float, val landmarks: FloatArray)

    /**
     * The rotation, uniform scale and shift that best map the landmarks onto [Template] (least
     * squares), applied with a canvas: a 112×112 face, eyes level.
     */
    private fun straighten(image: Bitmap, landmarks: FloatArray): Bitmap {
        var mx = 0f; var my = 0f; var tx = 0f; var ty = 0f
        for (k in 0 until 5) {
            mx += landmarks[2 * k]; my += landmarks[2 * k + 1]
            tx += Template[2 * k]; ty += Template[2 * k + 1]
        }
        mx /= 5; my /= 5; tx /= 5; ty /= 5
        var dot = 0f; var cross = 0f; var norm = 0f
        for (k in 0 until 5) {
            val px = landmarks[2 * k] - mx
            val py = landmarks[2 * k + 1] - my
            val qx = Template[2 * k] - tx
            val qy = Template[2 * k + 1] - ty
            dot += px * qx + py * qy
            cross += px * qy - py * qx
            norm += px * px + py * py
        }
        // [a −b; b a] is rotation × scale.
        val a = dot / norm
        val b = cross / norm
        val matrix = Matrix().apply {
            setValues(floatArrayOf(a, -b, tx - (a * mx - b * my), b, a, ty - (b * mx + a * my), 0f, 0f, 1f))
        }
        return Bitmap.createBitmap(FaceSize, FaceSize, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(image, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    /** RGB, raw 0–255; unit length out. */
    private fun embed(face: Bitmap): FloatArray {
        val plane = FaceSize * FaceSize
        val argb = IntArray(plane)
        face.getPixels(argb, 0, FaceSize, 0, 0, FaceSize, FaceSize)
        val input = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = argb[i]
            input[i] = (p shr 16 and 0xFF).toFloat()
            input[plane + i] = (p shr 8 and 0xFF).toFloat()
            input[2 * plane + i] = (p and 0xFF).toFloat()
        }
        val shape = longArrayOf(1, 3, FaceSize.toLong(), FaceSize.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { x ->
            rec.run(mapOf("data" to x)).use { result ->
                val out = (result[0] as OnnxTensor).floatBuffer
                normalize(FloatArray(out.remaining()).also { out.get(it) })
            }
        }
    }

    override fun close() {
        det.close()
        rec.close()
        options.close()
    }

    companion object {
        /** Long side photos are looked at: faces of [MinFacePx] still show up in group shots. */
        const val ReadSide = 1280
        private const val MinScore = 0.8f
        private const val MaxOverlap = 0.3f
        /** Smaller faces are too blurry to tell apart reliably. */
        private const val MinFacePx = 40f
        private const val FaceSize = 112
        /** Where eyes, nose tip and mouth corners sit on a 112×112 face (ArcFace's template). */
        private val Template = floatArrayOf(
            38.2946f, 51.6963f, 73.5318f, 51.5014f, 56.0252f, 71.7366f, 41.5493f, 92.3655f, 70.7299f, 92.2041f,
        )

        private fun iou(a: FloatArray, b: FloatArray): Float {
            val w = max(0f, min(a[2], b[2]) - max(a[0], b[0]))
            val h = max(0f, min(a[3], b[3]) - max(a[1], b[1]))
            val inter = w * h
            return inter / ((a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter + 1e-9f)
        }
    }
}
