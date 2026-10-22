package com.subhashh.lumen.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.cos

/**
 * Offline reverse geocoding: nearest town of 5,000+ people from a bundled GeoNames table
 * (assets/places.tsv, see tools/places). No network, so photo locations never leave the device.
 */
object PlaceLookup {

    private class Table(
        val lat: FloatArray,
        val lon: FloatArray,
        val name: Array<String>,
        val region: IntArray,
        val country: Array<String>,
        val regions: Array<String>,
    )

    private val mutex = Mutex()
    private var table: Table? = null

    /** "Repalle, Andhra Pradesh, India"; just region and country when no town is close. */
    suspend fun describe(context: Context, point: GeoPoint): String? {
        val t = load(context)
        if (t.lat.isEmpty()) return null

        val cosLat = cos(Math.toRadians(point.latitude)).toFloat()
        val lat = point.latitude.toFloat()
        val lon = point.longitude.toFloat()
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in t.lat.indices) {
            val dLat = t.lat[i] - lat
            var dLon = t.lon[i] - lon
            if (dLon > 180f) dLon -= 360f else if (dLon < -180f) dLon += 360f
            dLon *= cosLat
            val d = dLat * dLat + dLon * dLon
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }

        val km = kotlin.math.sqrt(bestDistance) * KmPerDegree
        if (km > MaxDistanceKm) return null
        val country = Locale("", t.country[best]).displayCountry
        val region = t.regions[t.region[best]]
        val parts = if (km <= TownRadiusKm) listOf(t.name[best], region, country) else listOf(region, country)
        return parts.filter { it.isNotBlank() }.distinct().joinToString(", ")
    }

    private suspend fun load(context: Context): Table = mutex.withLock {
        table ?: withContext(Dispatchers.IO) { parse(context) }.also { table = it }
    }

    private fun parse(context: Context): Table =
        context.assets.open("places.tsv").bufferedReader().useLines { lines ->
            val it = lines.iterator()
            val (regionCount, placeCount) = it.next().split('\t').map(String::toInt)
            val regions = Array(regionCount) { _ -> it.next() }
            val lat = FloatArray(placeCount)
            val lon = FloatArray(placeCount)
            val name = arrayOfNulls<String>(placeCount)
            val region = IntArray(placeCount)
            val country = arrayOfNulls<String>(placeCount)
            for (i in 0 until placeCount) {
                val f = it.next().split('\t')
                lat[i] = f[0].toFloat()
                lon[i] = f[1].toFloat()
                name[i] = f[2]
                region[i] = f[3].toInt()
                country[i] = f[4]
            }
            @Suppress("UNCHECKED_CAST")
            Table(lat, lon, name as Array<String>, region, country as Array<String>, regions)
        }

    private const val KmPerDegree = 111.2f

    /** Beyond this, name the region rather than a town the photo wasn't really in. */
    private const val TownRadiusKm = 25f

    /** Open sea or remote areas: no name at all. */
    private const val MaxDistanceKm = 300f
}
