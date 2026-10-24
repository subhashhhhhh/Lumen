package com.subhashh.lumen.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.subhashh.lumen.data.GeoPoint
import com.subhashh.lumen.ui.components.GlassIconButton
import com.subhashh.lumen.ui.components.glass
import com.subhashh.lumen.ui.components.pressable
import com.subhashh.lumen.ui.icons.LumenIcons
import com.subhashh.lumen.ui.theme.LocalDarkTheme
import com.subhashh.lumen.ui.theme.LocalGlassColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import kotlin.coroutines.resume

/**
 * Maps for where photos were taken: MapLibre Native (open source, no telemetry) drawing
 * OpenFreeMap's vector tiles (OpenStreetMap data, free, no key, no account). Only used when
 * Settings → Photos & Videos → Maps is on; the tile server sees which area is shown.
 */
object PhotoMaps {
    private fun styleUrl(dark: Boolean) =
        "https://tiles.openfreemap.org/styles/" + if (dark) "dark" else "liberty"

    /** How close the map starts: a few streets around the spot. */
    private const val Zoom = 14.5

    /** MapLibre needs setting up once per process before any map or snapshot. */
    private fun ready(context: Context) {
        MapLibre.getInstance(context.applicationContext)
    }

    /** Rendered previews, so scrolling back to a photo doesn't fetch and draw again. */
    private val previews = LruCache<String, Bitmap>(12)

    private fun key(p: GeoPoint, dark: Boolean, w: Int, h: Int) = "${p.latitude},${p.longitude},$dark,$w,$h"

    fun cached(p: GeoPoint, dark: Boolean, w: Int, h: Int): Bitmap? = previews.get(key(p, dark, w, h))

    /** A still map of [p], [w]×[h] pixels; null when it can't be drawn (offline). */
    suspend fun preview(context: Context, p: GeoPoint, dark: Boolean, w: Int, h: Int, density: Float): Bitmap? =
        withContext(Dispatchers.Main) {
            ready(context)
            val options = MapSnapshotter.Options((w / density).toInt().coerceAtLeast(1), (h / density).toInt().coerceAtLeast(1))
                .withStyleBuilder(Style.Builder().fromUri(styleUrl(dark)))
                .withCameraPosition(CameraPosition.Builder().target(LatLng(p.latitude, p.longitude)).zoom(Zoom - 1).build())
                .withPixelRatio(density)
                .withLogo(false)
            val snapshotter = MapSnapshotter(context, options)
            try {
                suspendCancellableCoroutine { cont ->
                    cont.invokeOnCancellation { snapshotter.cancel() }
                    snapshotter.start(
                        { snapshot -> if (cont.isActive) cont.resume(snapshot.bitmap) },
                        { _ -> if (cont.isActive) cont.resume(null) },
                    )
                }
            } finally {
                snapshotter.cancel()
            }?.also { previews.put(key(p, dark, w, h), it) }
        }

    /** An interactive map of [p] with a pin; it follows Lumen's lifecycle. */
    @Composable
    fun Interactive(p: GeoPoint, dark: Boolean, pin: Color, modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val map = remember {
            ready(context)
            // Texture mode: the map draws into the view hierarchy, so the glass buttons above it
            // can refract it.
            MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
                onCreate(null)
                getMapAsync { m ->
                    m.uiSettings.isLogoEnabled = false
                    m.cameraPosition = CameraPosition.Builder().target(LatLng(p.latitude, p.longitude)).zoom(Zoom).build()
                    val here = Point.fromLngLat(p.longitude, p.latitude)
                    m.setStyle(
                        Style.Builder().fromUri(styleUrl(dark))
                            .withSource(GeoJsonSource("photo", here.toJson()))
                            .withLayer(
                                CircleLayer("photo-pin", "photo").withProperties(
                                    PropertyFactory.circleRadius(9f),
                                    PropertyFactory.circleColor(String.format("#%06X", pin.toArgb() and 0xFFFFFF)),
                                    PropertyFactory.circleStrokeWidth(3f),
                                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                                ),
                            ),
                    )
                }
            }
        }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> map.onStart()
                    Lifecycle.Event.ON_RESUME -> map.onResume()
                    Lifecycle.Event.ON_PAUSE -> map.onPause()
                    Lifecycle.Event.ON_STOP -> map.onStop()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
                map.onPause()
                map.onStop()
                map.onDestroy()
            }
        }
        AndroidView(factory = { map }, modifier = modifier)
    }
}

/**
 * The map in a photo's details: a still of the spot with a pin, tap to open the full map.
 * Hides itself when the map can't be drawn (offline), leaving the place name below it.
 */
@Composable
fun MapPreview(point: GeoPoint, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = LocalDarkTheme.current
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val bitmap by produceState(PhotoMaps.cached(point, dark, w, h), point, dark, w, h) {
            if (value == null) value = runCatching { PhotoMaps.preview(context, point, dark, w, h, density) }.getOrNull()
        }
        val image = bitmap
        if (image != null) {
            Box(Modifier.fillMaxSize().pressable(onOpen, pressedScale = 0.97f)) {
                Image(image.asImageBitmap(), contentDescription = "Map", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Pin(Modifier.align(Alignment.Center))
            }
        } else {
            Box(Modifier.fillMaxSize().background(LocalGlassColors.current.selection))
        }
    }
}

@Composable
private fun Pin(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(3.dp, Color.White, CircleShape),
    )
}

/** Full-screen map of where a photo was taken, with glass Back and Open in Maps. */
@Composable
fun PhotoMapScreen(point: GeoPoint, title: String?, onBack: () -> Unit, onOpenInMaps: () -> Unit) {
    BackHandler(onBack = onBack)
    val backdrop = rememberLayerBackdrop()
    val glass = LocalGlassColors.current
    Box(Modifier.fillMaxSize()) {
        PhotoMaps.Interactive(
            point,
            LocalDarkTheme.current,
            MaterialTheme.colorScheme.primary,
            Modifier.fillMaxSize().layerBackdrop(backdrop),
        )
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(backdrop, LumenIcons.Back, "Back", onClick = onBack)
            if (title != null) {
                Text(
                    title,
                    color = glass.content,
                    fontSize = 15.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .glass(backdrop, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        Text(
            "Open in Maps",
            color = glass.content,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .glass(backdrop, CircleShape)
                .pressable(onOpenInMaps, pressedScale = 0.94f)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
