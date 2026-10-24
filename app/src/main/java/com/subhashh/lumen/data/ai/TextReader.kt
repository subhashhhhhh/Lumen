package com.subhashh.lumen.data.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Reads the words in a photo with PP-OCRv6 ([OcrModel]), in two steps:
 * 1. **Detection** marks, pixel by pixel, how likely each spot is text. Connected blobs of
 *    likely text become boxes (the smallest rotated rectangle around each, grown back out,
 *    since the model marks a shrunken core of each line).
 * 2. **Recognition** reads each box, cut out and straightened, as one line of characters.
 *
 * Same steps as tools/ocr/ocr_reference.py, which checks them on the Mac. No OpenCV: every step
 * is a few lines here.
 */
class TextReader(dir: File, threads: Int, gpu: Boolean = false, recGpuWidths: List<Int> = emptyList()) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(threads)
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    /** Detection on the GPU ([OcrModel.DetGpu], only after [GpuCheck] passed), else the CPU. */
    private val gpuDet = if (gpu) GpuDetector(File(dir, OcrModel.DetGpu)) else null
    private val det = if (gpuDet == null) env.createSession(File(dir, "det.onnx").path, options) else null
    private val rec = env.createSession(File(dir, "rec.onnx").path, options)
    /**
     * Line reading on the GPU, one model per width that passed [GpuCheck], loaded when first
     * needed. A batch goes to the narrowest that fits its widest line; wider ones to the CPU.
     */
    private val gpuRec = recGpuWidths.sorted().associateWith { w -> lazy { GpuRecognizer(File(dir, OcrModel.recGpu(w)), w) } }
    /** Index 0 is CTC's "no character"; the model adds a space after the list. */
    private val chars: Array<String> = loadChars(File(dir, "rec.yml"))

    /** Lines of text, top to bottom; empty when there are none. */
    fun read(original: Bitmap): List<String> {
        // Thumbnails can come back as hardware bitmaps, which can't be read pixel by pixel.
        val image = if (original.config == Bitmap.Config.HARDWARE) original.copy(Bitmap.Config.ARGB_8888, false) else original
        try {
            val boxes = detect(image).sortedBy { box -> box.sumOf { it.y.toDouble() } }
            return readBoxes(image, boxes).filterNotNull()
        } finally {
            if (image !== original) image.recycle()
        }
    }

    /**
     * One video frame's text: its boxes, a small greyscale thumbnail of each, and each box's line
     * (null where it didn't read as text).
     */
    class FrameText internal constructor(
        internal val boxes: List<FloatArray>,
        internal val looks: List<IntArray>,
        val lines: List<String?>,
        /** How many boxes were actually read for this frame; the rest repeated the frame before. */
        val read: Int,
    )

    /**
     * Reads a video frame, but only the text that changed since [previous]: a box in about the
     * same place that also looks the same (a caption still on screen) keeps its earlier line.
     * A new subtitle in the same spot looks different, so it's read. Reading lines is most of
     * the cost; finding them is cheap.
     */
    fun readChanged(original: Bitmap, previous: FrameText?): FrameText {
        val image = if (original.config == Bitmap.Config.HARDWARE) original.copy(Bitmap.Config.ARGB_8888, false) else original
        try {
            val boxes = detect(image).sortedBy { box -> box.sumOf { it.y.toDouble() } }
            val bounds = boxes.map { box -> floatArrayOf(box.minOf { it.x }, box.minOf { it.y }, box.maxOf { it.x }, box.maxOf { it.y }) }
            val looks = boxes.map { look(image, it) }
            val lines = arrayOfNulls<String>(boxes.size)
            val unread = ArrayList<Int>()
            for (i in boxes.indices) {
                val same = previous?.let { prev ->
                    prev.boxes.indices.firstOrNull { j -> iou(prev.boxes[j], bounds[i]) >= SamePlace && difference(prev.looks[j], looks[i]) < SameLook }
                }
                if (same != null) lines[i] = previous.lines[same] else unread += i
            }
            if (unread.isNotEmpty()) {
                val read = readBoxes(image, unread.map { boxes[it] })
                for ((k, i) in unread.withIndex()) lines[i] = read[k]
            }
            return FrameText(bounds, looks, lines.toList(), unread.size)
        } finally {
            if (image !== original) image.recycle()
        }
    }

    /** Lines for [boxes] in their order; batched by width so short lines aren't padded to long ones. */
    private fun readBoxes(image: Bitmap, boxes: List<Array<Point>>): List<String?> {
        if (boxes.isEmpty()) return emptyList()
        // A line's width once cut to the reader's height: its long side over its short side.
        val order = boxes.indices.sortedBy { i ->
            val a = edge(boxes[i], 1)
            val b = edge(boxes[i], 3)
            maxOf(a, b) / minOf(a, b)
        }
        val lines = arrayOfNulls<String>(boxes.size)
        for (batch in order.chunked(Batch)) {
            val read = recognize(image, batch.map { boxes[it] })
            for ((k, i) in batch.withIndex()) lines[i] = read[k]
        }
        return lines.toList()
    }

    /** Length of the edge from the first corner to corner [k]. */
    private fun edge(box: Array<Point>, k: Int) = maxOf(hypot((box[k].x - box[0].x).toDouble(), (box[k].y - box[0].y).toDouble()), 1.0)

    /** A box's content as 32×8 greys: enough to tell a caption that's still there from a new one. */
    private fun look(image: Bitmap, box: Array<Point>): IntArray {
        val line = cut(image, box)
        val small = Bitmap.createScaledBitmap(line, 32, 8, true)
        line.recycle()
        val px = IntArray(256)
        small.getPixels(px, 0, 32, 0, 0, 32, 8)
        small.recycle()
        return IntArray(256) { i -> (px[i] shr 16 and 0xFF) * 299 / 1000 + (px[i] shr 8 and 0xFF) * 587 / 1000 + (px[i] and 0xFF) * 114 / 1000 }
    }

    private fun difference(a: IntArray, b: IntArray): Int {
        var sum = 0
        for (i in a.indices) sum += kotlin.math.abs(a[i] - b[i])
        return sum / a.size
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val w = maxOf(0f, minOf(a[2], b[2]) - maxOf(a[0], b[0]))
        val h = maxOf(0f, minOf(a[3], b[3]) - maxOf(a[1], b[1]))
        val inter = w * h
        return inter / ((a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter + 1e-9f)
    }

    private class Point(val x: Float, val y: Float)

    private fun detect(image: Bitmap): List<Array<Point>> {
        gpuDet?.let { gpu ->
            val (prob, scale) = gpu.run(image)
            return boxes(prob, GpuSide, GpuSide, 1 / scale, 1 / scale)
        }
        val scale = minOf(1f, DetMaxSide.toFloat() / maxOf(image.width, image.height))
        val w = maxOf(32, (image.width * scale / 32).roundToInt() * 32)
        val h = maxOf(32, (image.height * scale / 32).roundToInt() * 32)
        val scaled = Bitmap.createScaledBitmap(image, w, h, true)
        val argb = IntArray(w * h)
        scaled.getPixels(argb, 0, w, 0, 0, w, h)
        if (scaled !== image) scaled.recycle()
        val plane = w * h
        // Paddle models see BGR, normalized with ImageNet's mean and spread in that order.
        val input = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = argb[i]
            input[i] = ((p and 0xFF) / 255f - 0.485f) / 0.229f
            input[plane + i] = ((p shr 8 and 0xFF) / 255f - 0.456f) / 0.224f
            input[2 * plane + i] = ((p shr 16 and 0xFF) / 255f - 0.406f) / 0.225f
        }
        val prob = FloatArray(plane)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, h.toLong(), w.toLong())).use { x ->
            det!!.run(mapOf("x" to x)).use { (it[0] as OnnxTensor).floatBuffer.get(prob) }
        }
        return boxes(prob, w, h, image.width.toFloat() / w, image.height.toFloat() / h)
    }

    /** Each 8-connected blob above [BinThresh] whose mean is above [BoxThresh], as four corners in image pixels. */
    private fun boxes(prob: FloatArray, w: Int, h: Int, sx: Float, sy: Float): List<Array<Point>> {
        val seen = BooleanArray(prob.size)
        val stack = IntArray(prob.size)
        var pixels = IntArray(1024)
        val out = ArrayList<Array<Point>>()
        for (start in prob.indices) {
            if (seen[start] || prob[start] <= BinThresh) continue
            var n = 0
            var top = 0
            stack[top++] = start
            seen[start] = true
            var sum = 0.0
            while (top > 0) {
                val i = stack[--top]
                if (n == pixels.size) pixels = pixels.copyOf(n * 2)
                pixels[n++] = i
                sum += prob[i]
                val x = i % w
                val y = i / w
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (!seen[j] && prob[j] > BinThresh) {
                        seen[j] = true
                        stack[top++] = j
                    }
                }
            }
            if (n < 4 || sum / n < BoxThresh) continue
            box(pixels, n, w, sx, sy)?.let(out::add)
            if (out.size >= MaxBoxes) break
        }
        return out
    }

    private fun box(pixels: IntArray, n: Int, w: Int, sx: Float, sy: Float): Array<Point>? {
        // Each row's leftmost and rightmost pixel is all the convex hull needs; corners, not
        // centers, so a one-pixel-tall line still has area.
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (k in 0 until n) {
            val y = pixels[k] / w
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        val rows = maxY - minY + 1
        val left = IntArray(rows) { Int.MAX_VALUE }
        val right = IntArray(rows) { Int.MIN_VALUE }
        for (k in 0 until n) {
            val r = pixels[k] / w - minY
            val x = pixels[k] % w
            if (x < left[r]) left[r] = x
            if (x > right[r]) right[r] = x
        }
        val xs = ArrayList<Long>(rows * 4)
        for (r in 0 until rows) {
            if (left[r] == Int.MAX_VALUE) continue
            val y = minY + r
            for ((px, py) in arrayOf(left[r] to y, right[r] + 1 to y, left[r] to y + 1, right[r] + 1 to y + 1)) {
                xs += (px.toLong() shl 32) or py.toLong()
            }
        }
        val hull = convexHull(xs.distinct().sorted().map { (it shr 32).toDouble() to (it and 0xFFFFFFFF).toDouble() })
        if (hull.size < 3) return null
        val rect = minAreaRect(hull)
        if (minOf(rect.halfU, rect.halfV) * 2 < 3) return null
        // Unclip: grow by area × ratio / perimeter on every side.
        val d = (4 * rect.halfU * rect.halfV) * Unclip / (4 * (rect.halfU + rect.halfV))
        val hu = rect.halfU + d
        val hv = rect.halfV + d
        return arrayOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (a, b) ->
            Point(
                ((rect.cx + a * hu * rect.ux + b * hv * rect.vx) * sx).toFloat(),
                ((rect.cy + a * hu * rect.uy + b * hv * rect.vy) * sy).toFloat(),
            )
        }.toTypedArray()
    }

    private class Rect(
        val cx: Double, val cy: Double,
        val ux: Double, val uy: Double, val halfU: Double,
        val vx: Double, val vy: Double, val halfV: Double,
    )

    /** Rotating calipers: the smallest rectangle has a side along one of the hull's edges. */
    private fun minAreaRect(hull: List<Pair<Double, Double>>): Rect {
        var best: Rect? = null
        var bestArea = Double.MAX_VALUE
        for (i in hull.indices) {
            val (x0, y0) = hull[i]
            val (x1, y1) = hull[(i + 1) % hull.size]
            val len = hypot(x1 - x0, y1 - y0)
            if (len == 0.0) continue
            val ux = (x1 - x0) / len
            val uy = (y1 - y0) / len
            val vx = -uy
            val vy = ux
            var minA = Double.MAX_VALUE; var maxA = -Double.MAX_VALUE
            var minB = Double.MAX_VALUE; var maxB = -Double.MAX_VALUE
            for ((x, y) in hull) {
                val a = x * ux + y * uy
                val b = x * vx + y * vy
                if (a < minA) minA = a
                if (a > maxA) maxA = a
                if (b < minB) minB = b
                if (b > maxB) maxB = b
            }
            val area = (maxA - minA) * (maxB - minB)
            if (area < bestArea) {
                bestArea = area
                val ca = (maxA + minA) / 2
                val cb = (maxB + minB) / 2
                best = Rect(ux * ca + vx * cb, uy * ca + vy * cb, ux, uy, (maxA - minA) / 2, vx, vy, (maxB - minB) / 2)
            }
        }
        return best!!
    }

    /** Andrew's monotone chain over points sorted by x, then y. */
    private fun convexHull(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (points.size <= 2) return points
        fun cross(o: Pair<Double, Double>, a: Pair<Double, Double>, b: Pair<Double, Double>) =
            (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        val lower = ArrayList<Pair<Double, Double>>()
        for (p in points) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1)
            lower += p
        }
        val upper = ArrayList<Pair<Double, Double>>()
        for (p in points.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1)
            upper += p
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Cuts out a box, straightened and scaled to the recognizer's height, reading left to right. */
    private fun cut(image: Bitmap, box: Array<Point>): Bitmap {
        // Corners in reading order: along the long side (so vertical text is read turned on
        // its side), then across it.
        val cx = box.sumOf { it.x.toDouble() } / 4
        val cy = box.sumOf { it.y.toDouble() } / 4
        var ax = (box[1].x - box[0].x).toDouble(); var ay = (box[1].y - box[0].y).toDouble()
        var bx = (box[3].x - box[0].x).toDouble(); var by = (box[3].y - box[0].y).toDouble()
        if (hypot(ax, ay) < hypot(bx, by)) { ax = bx.also { bx = ax }; ay = by.also { by = ay } }
        if (ax < 0 || (ax == 0.0 && ay < 0)) { ax = -ax; ay = -ay }
        if (ax * by - ay * bx < 0) { bx = -bx; by = -by }
        val tlx = cx - ax / 2 - bx / 2; val tly = cy - ay / 2 - by / 2
        val outW = ceil(RecHeight * hypot(ax, ay) / hypot(bx, by)).toInt().coerceIn(8, RecMaxWidth)
        val src = floatArrayOf(
            tlx.toFloat(), tly.toFloat(),
            (tlx + ax).toFloat(), (tly + ay).toFloat(),
            (tlx + bx).toFloat(), (tly + by).toFloat(),
        )
        val dst = floatArrayOf(0f, 0f, outW.toFloat(), 0f, 0f, RecHeight.toFloat())
        val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 3) }
        return Bitmap.createBitmap(outW, RecHeight, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(image, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    /** Lines for one batch of boxes, in order; null where a box didn't read as text. */
    private fun recognize(image: Bitmap, boxes: List<Array<Point>>): List<String?> {
        val lines = boxes.map { cut(image, it) }
        try {
            val widest = lines.maxOf { it.width }
            val gpu = gpuRec.entries.firstOrNull { it.key >= widest }?.value?.value
            return gpu?.read(lines) ?: recognizeCpu(lines, widest)
        } finally {
            lines.forEach { it.recycle() }
        }
    }

    /** One run of ONNX Runtime, each line padded on the right to the widest. */
    private fun recognizeCpu(lines: List<Bitmap>, width: Int): List<String?> {
        val plane = RecHeight * width
        val input = FloatArray(lines.size * 3 * plane)
        val argb = IntArray(plane)
        for ((b, line) in lines.withIndex()) {
            line.getPixels(argb, 0, line.width, 0, 0, line.width, RecHeight)
            val base = b * 3 * plane
            for (y in 0 until RecHeight) for (x in 0 until line.width) {
                val p = argb[y * line.width + x]
                val i = y * width + x
                input[base + i] = (p and 0xFF) / 127.5f - 1f
                input[base + plane + i] = (p shr 8 and 0xFF) / 127.5f - 1f
                input[base + 2 * plane + i] = (p shr 16 and 0xFF) / 127.5f - 1f
            }
        }
        val shape = longArrayOf(lines.size.toLong(), 3, RecHeight.toLong(), width.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(input), shape).use { x ->
            rec.run(mapOf("x" to x)).use { result ->
                val out = (result[0] as OnnxTensor).floatBuffer
                val classes = chars.size + 1
                val steps = out.remaining() / (lines.size * classes)
                val row = FloatArray(classes)
                List(lines.size) { b ->
                    decode(
                        steps,
                        best = { t ->
                            out.position((b * steps + t) * classes)
                            out.get(row)
                            var best = 0
                            for (c in 1 until classes) if (row[c] > row[best]) best = c
                            best
                        },
                        // The model ends in a Softmax: scores are probabilities already.
                        probability = { _, c -> row[c].toDouble() },
                    )
                }
            }
        }
    }

    /**
     * Greedy CTC: each step's most likely class ([best]), repeats and "no character" dropped.
     * [probability] is asked right after [best] for the same step, only for kept characters.
     */
    private inline fun decode(steps: Int, best: (t: Int) -> Int, probability: (t: Int, c: Int) -> Double): String? {
        val text = StringBuilder()
        var confidence = 0.0
        var kept = 0
        var previous = 0
        for (t in 0 until steps) {
            val c = best(t)
            if (c != 0 && c != previous) {
                text.append(if (c < chars.size) chars[c] else " ")
                confidence += probability(t, c)
                kept++
            }
            previous = c
        }
        val line = text.toString().trim()
        // Icons read as a stray letter or two; keep lines with some substance.
        return if (kept > 0 && confidence / kept >= MinLineScore && line.count { it.isLetterOrDigit() } >= 2) line else null
    }

    override fun close() {
        gpuDet?.close()
        gpuRec.values.filter { it.isInitialized() }.forEach { it.value.close() }
        det?.close()
        rec.close()
        options.close()
    }

    /**
     * The GPU takes one fixed input size, so every photo goes into a [GpuSide] square: scaled
     * to fit (never up), top-left, the rest left at 0 (the mean colour after normalizing), where
     * nothing is ever found.
     */
    private class GpuDetector(file: File) : AutoCloseable {
        private val environment = Environment.create()
        private val model = CompiledModel.create(
            file.path,
            CompiledModel.Options(Accelerator.GPU).apply {
                gpuOptions = CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP32)
            },
            environment,
        )
        private val inputs = model.createInputBuffers(0)
        private val outputs = model.createOutputBuffers(0)
        private val input = FloatArray(GpuSide * GpuSide * 3)

        /** The text map and the scale the image went in at. */
        fun run(image: Bitmap): Pair<FloatArray, Float> {
            val scale = minOf(1f, GpuSide.toFloat() / maxOf(image.width, image.height))
            val w = maxOf(1, (image.width * scale).roundToInt())
            val h = maxOf(1, (image.height * scale).roundToInt())
            val scaled = Bitmap.createScaledBitmap(image, w, h, true)
            val argb = IntArray(w * h)
            scaled.getPixels(argb, 0, w, 0, 0, w, h)
            if (scaled !== image) scaled.recycle()
            input.fill(0f)
            // NHWC, BGR, ImageNet's mean and spread, as on the CPU.
            for (y in 0 until h) for (x in 0 until w) {
                val p = argb[y * w + x]
                val o = (y * GpuSide + x) * 3
                input[o] = ((p and 0xFF) / 255f - 0.485f) / 0.229f
                input[o + 1] = ((p shr 8 and 0xFF) / 255f - 0.456f) / 0.224f
                input[o + 2] = ((p shr 16 and 0xFF) / 255f - 0.406f) / 0.225f
            }
            inputs[0].writeFloat(input)
            model.run(inputs, outputs, 0)
            return outputs[0].readFloat() to scale
        }

        override fun close() {
            inputs.forEach { it.close() }
            outputs.forEach { it.close() }
            model.close()
            environment.close()
        }
    }

    /**
     * The line reader at one fixed [width] for LiteRT's GPU: [Batch] lines, NHWC, unused lines
     * and the right of short ones left at 0, as ONNX Runtime pads. Its character classifier
     * comes in two halves (`logits_0`, `logits_1`: 18,710 classes are wider than the GPU's
     * textures) of raw scores, so the best character and its probability are worked out here.
     */
    private inner class GpuRecognizer(file: File, private val width: Int) : AutoCloseable {
        private val environment = Environment.create()
        private val model = CompiledModel.create(
            file.path,
            CompiledModel.Options(Accelerator.GPU).apply {
                gpuOptions = CompiledModel.GpuOptions(precision = CompiledModel.GpuOptions.Precision.FP32)
            },
            environment,
        )
        private val inputs = model.createInputBuffers(0)
        private val outputs = listOf(model.createOutputBuffer("logits_0"), model.createOutputBuffer("logits_1"))
        private val input = FloatArray(Batch * RecHeight * width * 3)

        fun read(lines: List<Bitmap>): List<String?> {
            input.fill(0f)
            val argb = IntArray(RecHeight * width)
            for ((b, line) in lines.withIndex()) {
                line.getPixels(argb, 0, line.width, 0, 0, line.width, RecHeight)
                for (y in 0 until RecHeight) for (x in 0 until line.width) {
                    val p = argb[y * line.width + x]
                    val o = ((b * RecHeight + y) * width + x) * 3
                    input[o] = (p and 0xFF) / 127.5f - 1f
                    input[o + 1] = (p shr 8 and 0xFF) / 127.5f - 1f
                    input[o + 2] = (p shr 16 and 0xFF) / 127.5f - 1f
                }
            }
            inputs[0].writeFloat(input)
            model.run(inputs, outputs, 0)
            val low = outputs[0].readFloat()
            val high = outputs[1].readFloat()
            val half = low.size / (Batch * (width / 8))
            val steps = width / 8
            return List(lines.size) { b ->
                decode(
                    steps,
                    best = { t ->
                        val at = (b * steps + t) * half
                        var best = 0
                        var top = low[at]
                        for (c in 1 until half) if (low[at + c] > top) { top = low[at + c]; best = c }
                        for (c in 0 until half) if (high[at + c] > top) { top = high[at + c]; best = half + c }
                        best
                    },
                    // Softmax over both halves, for the winner only.
                    probability = { t, c ->
                        val at = (b * steps + t) * half
                        val top = if (c < half) low[at + c] else high[at + c - half]
                        var sum = 0.0
                        for (k in 0 until half) sum += exp((low[at + k] - top).toDouble()) + exp((high[at + k] - top).toDouble())
                        1 / sum
                    },
                )
            }
        }

        override fun close() {
            inputs.forEach { it.close() }
            outputs.forEach { it.close() }
            model.close()
            environment.close()
        }
    }

    private companion object {
        /** Long side for detection: chat text reads the same at 960 as at full size, ~7× faster. */
        const val DetMaxSide = 960
        /** The GPU model's fixed input: [DetMaxSide] square. */
        const val GpuSide = 960
        const val BinThresh = 0.2f
        const val BoxThresh = 0.45
        const val Unclip = 1.4
        const val MaxBoxes = 300
        const val RecHeight = 48
        const val RecMaxWidth = 3200
        const val Batch = 8
        const val MinLineScore = 0.5
        /** A box in about the same place as one in the frame before… */
        const val SamePlace = 0.6f
        /** …that also looks the same (mean grey difference, 0–255) is the same text. */
        const val SameLook = 12

        /** The `character_dict` list in rec.yml, plain or single-quoted YAML scalars. */
        fun loadChars(yml: File): Array<String> {
            val out = arrayListOf("")
            var inList = false
            yml.forEachLine { line ->
                if (!inList) {
                    inList = line.trim() == "character_dict:"
                } else if (line.startsWith("  - ")) {
                    val v = line.substring(4)
                    out += if (v.length >= 2 && v.startsWith("'") && v.endsWith("'")) v.substring(1, v.length - 1).replace("''", "'") else v
                } else if (line.isNotEmpty() && !line.startsWith(" ")) {
                    inList = false
                }
            }
            return out.toTypedArray()
        }
    }
}
