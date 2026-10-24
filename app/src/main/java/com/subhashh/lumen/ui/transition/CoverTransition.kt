package com.subhashh.lumen.ui.transition

import android.content.Context
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil3.SingletonImageLoader
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.subhashh.lumen.data.GridThumbnail
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.images.ImageKeys
import com.subhashh.lumen.ui.images.bestPlaceholderKey
import com.subhashh.lumen.ui.images.version
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * The photo an album or person shows at the top of its page, and on its card in Collections.
 * [face] (fractions of the photo: left, top, right, bottom) centres both on the person; [crop]
 * replaces the photo for people whose cover is a face found in a video.
 */
@Immutable
data class HeroCover(val key: String, val item: MediaItem, val face: List<Float>? = null, val crop: File? = null)

/**
 * Opening an album or person: its cover grows out of its card into the big photo across the top
 * of the page, while the page fades in over Collections; going back shrinks it into the card.
 * The flying cover is the whole photo, clipped to a window that moves from the card's crop
 * (square, or around the face) to the header's, so it opens up rather than jumping.
 */
@Stable
class CoverTransition {

    private class Cell(val coordinates: LayoutCoordinates, val circle: Boolean)

    /** Album cards and faces, by collection key; read only when a flight starts. */
    private val cells = HashMap<String, Cell>()

    /** The open page's header, for flying back from wherever it's scrolled to. */
    private var hero: LayoutCoordinates? = null

    /** The cover in flight, or null when nothing is animating. */
    var cover by mutableStateOf<HeroCover?>(null)
        private set

    val flying: Boolean get() = cover != null

    /** 0 at its card, 1 as the page's header. Read in layout/draw only. */
    private var progress by mutableFloatStateOf(1f)
    /** The card at full size; on screen it's scaled with the page under the flight ([parentScale]). */
    private var cellRect = Rect.Zero
    private var pivot = Offset.Zero
    private var heroRect = Rect.Zero
    private var circle = false

    /** No card to land in: the cover shrinks and fades where it is. */
    private var landless = false

    fun registerCell(key: String, coordinates: LayoutCoordinates, circle: Boolean) {
        cells[key] = Cell(coordinates, circle)
    }

    fun registerHero(coordinates: LayoutCoordinates) {
        hero = coordinates
    }

    private fun cell(key: String): Cell? = cells[key]?.takeIf { it.coordinates.isAttached }

    /**
     * Gets the header's image into memory (up to a moment), so the cover flies sharp from its
     * first frame, then pins it to its card. False when the card isn't on screen: open plainly.
     * Call right before the page opens, in the same frame.
     */
    suspend fun prepare(context: Context, cover: HeroCover, header: Rect): Boolean {
        val cell = cell(cover.key) ?: return false
        withTimeoutOrNull(PrepareTimeoutMs) { SingletonImageLoader.get(context).execute(heroRequest(context, cover)) }
        // Collections isn't scaled yet: it's still its own screen.
        cellRect = cell.coordinates.unscaledRect(1f)
        circle = cell.circle
        heroRect = header
        landless = false
        progress = 0f
        this.cover = cover
        return true
    }

    /** After [prepare] and opening the page: grows the cover into the header. */
    suspend fun open() {
        animate(progress, 1f, animationSpec = OpenSpring) { v, _ -> progress = v }
        cover = null
    }

    /**
     * Shrinks the open page's cover back into its card. [header] is where the header sits when
     * it's scrolled to the top; the page underneath is composed meanwhile (see [flying]), so the
     * card is found a couple of frames in. The caller closes the page, then calls [finish].
     */
    suspend fun close(cover: HeroCover, header: Rect) {
        val from = hero?.takeIf { it.isAttached }?.rect()
        // Scrolled away: it comes down from just above the screen.
        heroRect = from ?: Rect(header.left, -header.height, header.right, 0f)
        progress = 1f
        cellRect = heroRect
        landless = true
        this.cover = cover
        withFrameNanos { }
        withFrameNanos { }
        val cell = cell(cover.key)
        if (cell != null) {
            // Measured while the page under the flight is scaled down: undo that, then follow
            // the page as it grows back, so the cover lands where the card ends up.
            cellRect = cell.coordinates.unscaledRect(parentScale())
            circle = cell.circle
            landless = false
        } else {
            cellRect = shrunk(heroRect)
            pivot = heroRect.center
        }
        animate(progress, 0f, animationSpec = CloseSpring) { v, _ -> progress = v }
    }

    fun finish() {
        cover = null
        progress = 1f
    }

    /** The open page: invisible at the card, there well before the cover lands in the header. */
    fun pageAlpha(): Float = if (cover == null) 1f else ((progress - 0.15f) / 0.6f).coerceIn(0f, 1f)

    /** Collections (or People) under the page while the cover flies. */
    fun parentAlpha(): Float = 1f - (progress / 0.7f).coerceIn(0f, 1f)
    fun parentScale(): Float = 1f - 0.05f * progress

    internal fun hides(key: String) = cover?.key == key

    internal fun frame(): Frame {
        val p = progress.coerceIn(0f, 1.05f)
        val cell = if (landless) cellRect else cellRect.scaledAbout(pivot, parentScale())
        return Frame(
            cell = cell,
            hero = heroRect,
            clip = lerp(cell, heroRect, p),
            fraction = p,
            circle = circle,
            alpha = if (landless) progress.coerceIn(0f, 1f) else 1f,
        )
    }

    internal class Frame(val cell: Rect, val hero: Rect, val clip: Rect, val fraction: Float, val circle: Boolean, val alpha: Float)

    private fun LayoutCoordinates.rect() = Rect(positionInRoot(), size.toSize())

    /** Where this would be with the page under the flight at full size ([scale] about its centre). */
    private fun LayoutCoordinates.unscaledRect(scale: Float): Rect {
        pivot = findRootCoordinates().size.toSize().center
        val p = positionInRoot()
        val topLeft = pivot + (p - pivot) / scale
        return Rect(topLeft, size.toSize())
    }

    private fun Rect.scaledAbout(c: Offset, s: Float): Rect =
        Rect(c + (topLeft - c) * s, c + (bottomRight - c) * s)

    private fun shrunk(r: Rect): Rect {
        val w = r.width * 0.6f
        val h = r.height * 0.6f
        return Rect(r.center.x - w / 2f, r.center.y - h / 2f, r.center.x + w / 2f, r.center.y + h / 2f)
    }

    private companion object {
        /** Long enough for a cached header from disk; a slower decode flies on the placeholder. */
        const val PrepareTimeoutMs = 160L
        val OpenSpring = spring(dampingRatio = 0.86f, stiffness = 300f, visibilityThreshold = 0.002f)
        val CloseSpring = spring(dampingRatio = 0.9f, stiffness = 340f, visibilityThreshold = 0.002f)
    }
}

/** The open album's or person's page, faded in and out with its cover's flight. */
fun Modifier.coverPage(transition: CoverTransition): Modifier = graphicsLayer { alpha = transition.pageAlpha() }

/** Lets [transition] find this album card or face, and hides it while its cover is in flight. */
fun Modifier.coverCell(transition: CoverTransition, key: String, circle: Boolean = false): Modifier = this
    .onPlaced { transition.registerCell(key, it, circle) }
    .graphicsLayer { alpha = if (transition.hides(key)) 0f else 1f }

/** The header's image: the whole photo (or face crop), sharp at full width, first frames on video. */
fun heroRequest(context: Context, cover: HeroCover): ImageRequest {
    val builder = ImageRequest.Builder(context)
    if (cover.crop != null) return builder.data(cover.crop).memoryCacheKey("hero:crop:${cover.crop.name}").build()
    return builder
        .data(GridThumbnail(cover.item, HeroPx))
        .size(HeroPx, HeroPx)
        .memoryCacheKey("hero:${cover.item.version}")
        .placeholderMemoryCacheKey(bestPlaceholderKey(SingletonImageLoader.get(context), cover.item) ?: ImageKeys.cover(cover.item))
        .build()
}

/** Shorter side the header's photo is decoded at: the screen's width. */
private const val HeroPx = 1280

/** The flying cover. Swallows touches while it flies; draws nothing when idle. */
@Composable
fun CoverTransitionOverlay(transition: CoverTransition) {
    val cover = transition.cover ?: return
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(remember(cover) { heroRequest(context, cover) })
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        val f = transition.frame()
        val photo = photoSize(painter, cover)
        val atCell = photoRect(CoverWindows.cell(photo, cover.face), f.cell, photo)
        val atHero = photoRect(CoverWindows.hero(photo, f.hero.width / f.hero.height, cover.face), f.hero, photo)
        val cellRadius = if (f.circle) f.cell.width / 2f else 12.dp.toPx()
        drawCover(
            painter = painter,
            photo = lerp(atCell, atHero, f.fraction),
            clip = f.clip,
            radius = (cellRadius * (1f - f.fraction)).coerceAtLeast(0f),
            alpha = f.alpha,
        )
    }
}

/** The header's photo, cut the way the flight ends. Hidden while its cover is in flight. */
@Composable
fun HeroImage(cover: HeroCover, transition: CoverTransition, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(remember(cover) { heroRequest(context, cover) })
    Canvas(
        modifier
            .onPlaced { transition.registerHero(it) }
            .graphicsLayer { alpha = if (transition.hides(cover.key)) 0f else 1f },
    ) {
        val photo = photoSize(painter, cover)
        val bounds = Rect(Offset.Zero, size)
        drawCover(painter, photoRect(CoverWindows.hero(photo, size.width / size.height, cover.face), bounds, photo), bounds, 0f, 1f)
    }
}

/** The decoded photo's shape (EXIF-rotated); the file's own until it's loaded. */
private fun photoSize(painter: Painter, cover: HeroCover): Size {
    val intrinsic = painter.intrinsicSize
    if (intrinsic.isSpecified() && intrinsic.width > 0f && intrinsic.height > 0f) return intrinsic
    val item = cover.item
    return if (cover.crop == null && item.width > 0 && item.height > 0) Size(item.width.toFloat(), item.height.toFloat()) else Size(1f, 1f)
}

private fun Size.isSpecified() = this != Size.Unspecified

/** Where the whole photo goes so that [window] (fractions of it) fills [target]. */
private fun photoRect(window: Rect, target: Rect, photo: Size): Rect {
    val scale = target.width / (window.width * photo.width).coerceAtLeast(0.001f)
    val left = target.left - window.left * photo.width * scale
    val top = target.top - window.top * photo.height * scale
    return Rect(left, top, left + photo.width * scale, top + photo.height * scale)
}

private fun DrawScope.drawCover(painter: Painter, photo: Rect, clip: Rect, radius: Float, alpha: Float) {
    val path = Path().apply { addRoundRect(RoundRect(clip, CornerRadius(radius))) }
    clipPath(path) {
        translate(photo.left, photo.top) {
            with(painter) { draw(photo.size, alpha) }
        }
    }
}

private fun lerp(a: Rect, b: Rect, t: Float) = Rect(
    a.left + (b.left - a.left) * t,
    a.top + (b.top - a.top) * t,
    a.right + (b.right - a.right) * t,
    a.bottom + (b.bottom - a.bottom) * t,
)

/** Which part of a cover photo shows, as fractions of it. */
internal object CoverWindows {
    /** The card: a centred square, or a square around the face with hair and chin (as FaceAvatar cuts it). */
    fun cell(photo: Size, face: List<Float>?): Rect {
        val w = photo.width
        val h = photo.height
        if (face == null) {
            val side = minOf(w, h)
            return fraction(Rect((w - side) / 2f, (h - side) / 2f, (w + side) / 2f, (h + side) / 2f), photo)
        }
        val cx = (face[0] + face[2]) / 2f * w
        val cy = (face[1] + face[3]) / 2f * h
        val side = (maxOf((face[2] - face[0]) * w, (face[3] - face[1]) * h) * FaceMargin).coerceAtMost(minOf(w, h))
        val left = (cx - side / 2f).coerceIn(0f, w - side)
        val top = (cy - side / 2f).coerceIn(0f, h - side)
        return fraction(Rect(left, top, left + side, top + side), photo)
    }

    /**
     * The header: as much of the photo as fits its shape, centred; for a person, closer in, so
     * their face is a good share of the width, and placed a little above the middle.
     */
    fun hero(photo: Size, aspect: Float, face: List<Float>?): Rect {
        val w = photo.width
        val h = photo.height
        var ww = if (w / h > aspect) h * aspect else w
        var wh = ww / aspect
        var cx = w / 2f
        var cy = h / 2f
        if (face != null) {
            val faceSide = maxOf((face[2] - face[0]) * w, (face[3] - face[1]) * h)
            ww = minOf(ww, faceSide / HeroFaceShare)
            wh = ww / aspect
            cx = (face[0] + face[2]) / 2f * w
            cy = (face[1] + face[3]) / 2f * h + wh * 0.06f
        }
        val left = (cx - ww / 2f).coerceIn(0f, (w - ww).coerceAtLeast(0f))
        val top = (cy - wh / 2f).coerceIn(0f, (h - wh).coerceAtLeast(0f))
        return fraction(Rect(left, top, left + ww, top + wh), photo)
    }

    private fun fraction(r: Rect, photo: Size) = Rect(r.left / photo.width, r.top / photo.height, r.right / photo.width, r.bottom / photo.height)

    private const val FaceMargin = 1.7f

    /** The face's share of the header's width, at least. */
    private const val HeroFaceShare = 0.3f
}
