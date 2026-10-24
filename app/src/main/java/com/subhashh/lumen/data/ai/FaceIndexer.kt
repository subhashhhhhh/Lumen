package com.subhashh.lumen.data.ai

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * One faces pass: brings [FaceStore] in line with the library (photos only, newest first), then
 * groups new faces into people ([FaceGrouping]). Runs after [TextIndexer] in the same background
 * job, under the same rules.
 */
class FaceIndexer(private val context: Context) {

    /** Looks at most [limit] photos. */
    suspend fun run(limit: Int = Int.MAX_VALUE): Indexer.Outcome {
        if (!FaceModel.installed(context)) return Indexer.Outcome.Done
        val store = FaceStore.of(context)
        val library = Indexer.eligible(context).filterNot { it.isVideo }
        val known = store.indexed()
        store.remove(known.keys - library.mapTo(HashSet()) { it.id })
        val todo = library.filter { known[it.id] != it.modified }.sortedByDescending { it.takenAt }
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
                val faces = runCatching {
                    val bitmap = MediaFrames.forReading(context, item, FaceReader.ReadSide)
                    try { reader.read(bitmap) } finally { bitmap.recycle() }
                }.onFailure { Log.w(Tag, "Couldn't read ${item.name}", it) }.getOrDefault(emptyList())
                store.put(item.id, item.modified, faces.map { it.box to it.vector })
                SmartIndex.reportFaces(context, ++finished, library.size)
                Log.d(Tag, "${item.name.take(40)}: ${faces.size} faces, ${SystemClock.elapsedRealtime() - started} ms")
                // Group as we go, so people appear during a long first pass.
                if (finished % GroupEvery == 0) FaceGrouping.run(store)
            }
        }
        FaceGrouping.run(store)
        return outcome
    }

    private companion object {
        const val Tag = "LumenFaces"
        val Threads = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        const val GroupEvery = 200
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
