package com.subhashh.lumen.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import com.subhashh.lumen.data.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * One faces pass: brings [FaceStore] in line with the library, then groups new faces into people
 * ([FaceGrouping]). Photos first, newest first; then videos, read like Smart Search reads them
 * (one frame a second up to two minutes, else 120 spread out, near-identical frames skipped) at
 * photo size. A video keeps one face per person in it, the biggest, with its moment and a saved
 * crop; otherwise one reel would outweigh a whole album. Runs after [TextIndexer] in the same
 * background job, under the same rules.
 */
class FaceIndexer(private val context: Context) {

    /** Looks at most [limit] photos and videos. */
    suspend fun run(limit: Int = Int.MAX_VALUE): Indexer.Outcome {
        if (!FaceModel.installed(context)) return Indexer.Outcome.Done
        val store = FaceStore.of(context)
        val library = Indexer.eligible(context)
        val known = store.indexed()
        store.remove(known.keys - library.mapTo(HashSet()) { it.id })
        val todo = library.filter { known[it.id] != it.modified }
            .sortedWith(compareBy<MediaItem> { it.isVideo }.thenByDescending { it.takenAt })
        var finished = library.size - todo.size
        SmartIndex.reportFaces(context, finished, library.size)
        if (todo.isEmpty()) {
            FaceGrouping.run(store)
            return Indexer.Outcome.Done
        }
        val power = context.getSystemService(PowerManager::class.java)
        var outcome = Indexer.Outcome.Done
        FaceReader(FaceModel.dir(context), Threads).use { reader ->
            for ((n, item) in todo.withIndex()) {
                if (n >= limit) { outcome = Indexer.Outcome.Partial; break }
                coroutineContext.ensureActive()
                if (power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) { outcome = Indexer.Outcome.TooHot; break }
                val started = SystemClock.elapsedRealtime()
                if (item.isVideo) {
                    var frames = 0
                    val kept = ArrayList<Kept>()
                    runCatching {
                        MediaFrames.forEach(context, item, FaceReader.ReadSide) { at, frame ->
                            frames++
                            for (face in reader.read(frame)) keep(kept, face, at, frame)
                            frame.recycle()
                            true
                        }
                    }.onFailure { if (it is CancellationException) throw it; Log.w(Tag, "Couldn't read ${item.name}", it) }
                    val ids = store.put(item.id, item.modified, kept.map { NewFace(it.face.box, it.face.vector, it.atMs) })
                    for ((id, k) in ids.zip(kept)) {
                        runCatching { store.crop(id).outputStream().use { k.crop.compress(Bitmap.CompressFormat.JPEG, 88, it) } }
                        k.crop.recycle()
                    }
                    Log.d(Tag, "${item.name.take(40)}: ${item.durationMs / 1000}s video, $frames frames, ${kept.size} people, " +
                        "${SystemClock.elapsedRealtime() - started} ms")
                } else {
                    val faces = runCatching {
                        val bitmap = MediaFrames.forReading(context, item, FaceReader.ReadSide)
                        try { reader.read(bitmap) } finally { bitmap.recycle() }
                    }.onFailure { Log.w(Tag, "Couldn't read ${item.name}", it) }.getOrDefault(emptyList())
                    store.put(item.id, item.modified, faces.map { NewFace(it.box, it.vector) })
                    Log.d(Tag, "${item.name.take(40)}: ${faces.size} faces, ${SystemClock.elapsedRealtime() - started} ms")
                }
                SmartIndex.reportFaces(context, ++finished, library.size)
                // Group as we go, so people appear during a long first pass.
                if (finished % GroupEvery == 0) FaceGrouping.run(store)
            }
        }
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
