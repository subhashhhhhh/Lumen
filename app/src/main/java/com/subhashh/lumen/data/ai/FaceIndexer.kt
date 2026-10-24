package com.subhashh.lumen.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.CancellationException
import com.subhashh.lumen.data.LumenPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive

/**
 * One faces pass: brings [FaceStore] in line with the library, then groups new faces into people
 * ([FaceGrouping]). Photos first, newest first; then videos, read like Smart Search reads them
 * (one frame a second up to two minutes, else 120 spread out, near-identical frames skipped) at
 * the Video Detail size, decoded a few frames ahead on another thread. A video keeps one face per
 * person in it, the biggest, with its moment and a saved crop; otherwise one reel would outweigh
 * a whole album. The first pass of the background job: it's the fastest.
 *
 * Decoding is most of a video's cost, so while a video is open its frames are also saved small
 * ([FrameCache]) for Smart Search ([model]), if that video still needs its vectors.
 */
class FaceIndexer(private val context: Context, private val model: AiModel?) {

    private sealed interface Queued {
        class Start(val item: MediaItem) : Queued
        class Frame(val atMs: Long, val bitmap: Bitmap) : Queued
        class End(val item: MediaItem, val started: Long, val frames: Int) : Queued
    }

    /** Looks at most [limit] photos and videos. */
    suspend fun run(limit: Int = Int.MAX_VALUE): Indexer.Outcome = coroutineScope {
        if (!FaceModel.installed(context)) return@coroutineScope Indexer.Outcome.Done
        val store = FaceStore.of(context)
        val library = Indexer.eligible(context)
        val known = store.indexed()
        store.remove(known.keys - library.mapTo(HashSet()) { it.id })
        val todo = library.filter { known[it.id] != it.modified }.sortedByDescending { it.takenAt }
        val (videos, photos) = todo.partition { it.isVideo }
        var finished = library.size - todo.size
        SmartIndex.reportFaces(context, finished, library.size)
        if (todo.isEmpty()) {
            FaceGrouping.run(store)
            return@coroutineScope Indexer.Outcome.Done
        }
        val power = context.getSystemService(PowerManager::class.java)
        fun tooHot() = power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        var budget = limit
        FaceReader(FaceModel.dir(context), Threads).use { reader ->
            for (item in photos) {
                if (budget-- <= 0) return@coroutineScope done(store, Indexer.Outcome.Partial)
                ensureActive()
                if (tooHot()) return@coroutineScope done(store, Indexer.Outcome.TooHot)
                val started = SystemClock.elapsedRealtime()
                val faces = runCatching {
                    val bitmap = MediaFrames.forReading(context, item, FaceReader.ReadSide)
                    try { reader.read(bitmap) } finally { bitmap.recycle() }
                }.onFailure { Log.w(Tag, "Couldn't read ${item.name}", it) }.getOrDefault(emptyList())
                store.put(item.id, item.modified, faces.map { NewFace(it.box, it.vector) })
                SmartIndex.reportFaces(context, ++finished, library.size)
                Log.d(Tag, "${item.name.take(40)}: ${faces.size} faces, ${SystemClock.elapsedRealtime() - started} ms")
                if (finished % GroupEvery == 0) FaceGrouping.run(store)
            }

            val batch = videos.take(budget.coerceAtLeast(0))
            if (batch.isEmpty()) return@coroutineScope done(store, if (videos.isEmpty()) Indexer.Outcome.Done else Indexer.Outcome.Partial)
            val px = LumenPrefs(context).videoDetail.px
            // Videos Smart Search hasn't done yet get their frames kept for it.
            val vectorsDone = model?.let { EmbeddingStore.of(context, it).indexed() }
            val queue = Channel<Queued>(capacity = QueueFrames)
            val decoder = launch(Dispatchers.IO) {
                try {
                    for (item in batch) {
                        val started = SystemClock.elapsedRealtime()
                        var frames = 0
                        queue.send(Queued.Start(item))
                        try {
                            MediaFrames.forEach(context, item, px) { at, frame ->
                                frames++
                                queue.send(Queued.Frame(at, frame))
                                true
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(Tag, "Couldn't read ${item.name}", e)
                        }
                        queue.send(Queued.End(item, started, frames))
                    }
                } finally {
                    queue.close()
                }
            }
            try {
                var kept = ArrayList<Kept>()
                var cache: java.io.File? = null
                for (message in queue) {
                    when (message) {
                        is Queued.Start -> {
                            val item = message.item
                            // Keep its frames for Smart Search if that hasn't done this video yet.
                            cache = if (vectorsDone != null && vectorsDone[item.id] != item.modified) {
                                FrameCache.begin(context, item.id, item.modified)
                            } else null
                        }
                        is Queued.Frame -> {
                            if (tooHot()) {
                                message.bitmap.recycle()
                                return@coroutineScope done(store, Indexer.Outcome.TooHot)
                            }
                            cache?.let { FrameCache.save(it, message.atMs, message.bitmap) }
                            for (face in reader.read(message.bitmap)) keep(kept, face, message.atMs, message.bitmap)
                            message.bitmap.recycle()
                        }
                        is Queued.End -> {
                            val item = message.item
                            cache?.let(FrameCache::finish)
                            cache = null
                            val ids = store.put(item.id, item.modified, kept.map { NewFace(it.face.box, it.face.vector, it.atMs) })
                            for ((id, k) in ids.zip(kept)) {
                                runCatching { store.crop(id).outputStream().use { k.crop.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
                                k.crop.recycle()
                            }
                            SmartIndex.reportFaces(context, ++finished, library.size)
                            Log.d(Tag, "${item.name.take(40)}: ${item.durationMs / 1000}s video, ${message.frames} frames, " +
                                "${kept.size} people, ${SystemClock.elapsedRealtime() - message.started} ms")
                            kept = ArrayList()
                            if (finished % GroupEvery == 0) FaceGrouping.run(store)
                        }
                    }
                }
            } finally {
                decoder.cancel()
            }
            done(store, if (batch.size < videos.size) Indexer.Outcome.Partial else Indexer.Outcome.Done)
        }
    }

    private fun done(store: FaceStore, outcome: Indexer.Outcome): Indexer.Outcome {
        FaceGrouping.run(store)
        return outcome
    }

    private class Kept(var face: FaceReader.Found, var atMs: Long, var crop: Bitmap, var area: Float)

    /** A face joins whoever it matches among this video's faces (keeping the bigger one), or is someone new. */
    private fun keep(kept: MutableList<Kept>, face: FaceReader.Found, atMs: Long, frame: Bitmap) {
        val area = (face.box[2] - face.box[0]) * (face.box[3] - face.box[1])
        val same = kept.maxByOrNull { dot(it.face.vector, face.vector) }?.takeIf { dot(it.face.vector, face.vector) >= FaceGrouping.SamePerson }
        when {
            same == null -> kept += Kept(face, atMs, crop(frame, face.box), area)
            area > same.area -> {
                same.crop.recycle()
                same.face = face
                same.atMs = atMs
                same.crop = crop(frame, face.box)
                same.area = area
            }
        }
    }

    /** The face with hair and chin, square, [CropPx] across: what People shows for a video face. */
    private fun crop(frame: Bitmap, box: FloatArray): Bitmap {
        val cx = (box[0] + box[2]) / 2 * frame.width
        val cy = (box[1] + box[3]) / 2 * frame.height
        val side = (maxOf((box[2] - box[0]) * frame.width, (box[3] - box[1]) * frame.height) * 1.7f)
            .coerceAtMost(minOf(frame.width, frame.height).toFloat())
        val s = side.toInt().coerceAtLeast(1)
        val left = (cx - side / 2).toInt().coerceIn(0, frame.width - s)
        val top = (cy - side / 2).toInt().coerceIn(0, frame.height - s)
        val square = Bitmap.createBitmap(frame, left, top, s, s)
        return Bitmap.createScaledBitmap(square, CropPx, CropPx, true).also { if (it !== square) square.recycle() }
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    private companion object {
        const val Tag = "LumenFaces"
        val Threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        const val GroupEvery = 200
        const val CropPx = 256
        /** How far decoding may run ahead of the face models: a few frames, ~4 MB each at 1280. */
        const val QueueFrames = 6
    }
}

/**
 * Groups faces into people, Immich-style and incremental: a face that has no person joins the
 * person of its most similar face that has one, if that's at least [SamePerson] alike. Else, if
 * [MinFaces] − 1 other unassigned faces are that alike, they start a new person together.
 * Otherwise it waits for more faces of whoever it is. Faces with a person never move: that's the
 * user's to change (merge, split).
 *
 * The cutoff was tuned on the owner's photos (tools/faces/cluster_tune.py): 0.50 mixed different
 * people, 0.60 split real ones; 0.55 kept every right join and split every wrong one.
 */
object FaceGrouping {
    private const val Tag = "LumenFaces"
    const val SamePerson = 0.55f
    private const val MinFaces = 3

    @Synchronized
    fun run(store: FaceStore) {
        val started = SystemClock.elapsedRealtime()
        val faces = store.faces()
        val person = arrayOfNulls<Long>(faces.size)
        faces.forEachIndexed { i, f -> person[i] = f.person }
        val joins = HashMap<Long, Long>()
        val newPeople = ArrayList<List<Long>>()
        // Placeholder ids for people made in this run (negative), real ones after saving.
        var nextNew = -1L
        val newMembers = HashMap<Long, MutableList<Long>>()
        // Rows go in newest photo first: so newest faces first, then a second try for the waiting.
        val order = faces.indices
        repeat(2) {
            for (i in order) {
                if (person[i] != null) continue
                var best = -1
                var bestSim = SamePerson
                val close = ArrayList<Int>()
                val v = faces[i].vector
                for (j in faces.indices) {
                    if (j == i) continue
                    val s = dot(v, faces[j].vector)
                    if (s < SamePerson) continue
                    close += j
                    // Never back to a person the user took this face from.
                    if (person[j] != null && person[j] != faces[i].notPerson && s >= bestSim) {
                        bestSim = s
                        best = j
                    }
                }
                if (best >= 0) {
                    val p = person[best]!!
                    person[i] = p
                    if (p < 0) newMembers.getValue(p) += faces[i].id else joins[faces[i].id] = p
                } else if (close.size >= MinFaces - 1) {
                    val p = nextNew--
                    val members = mutableListOf(faces[i].id)
                    person[i] = p
                    for (j in close) if (person[j] == null) {
                        person[j] = p
                        members += faces[j].id
                    }
                    newMembers[p] = members
                }
            }
        }
        newPeople += newMembers.values
        store.assign(joins, newPeople)
        if (joins.isNotEmpty() || newPeople.isNotEmpty()) {
            Log.i(Tag, "Grouped ${faces.size} faces: ${joins.size} joined people, ${newPeople.size} new people, " +
                "${person.count { it == null }} waiting, ${SystemClock.elapsedRealtime() - started} ms")
        }
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (k in a.indices) s += a[k] * b[k]
        return s
    }
}
