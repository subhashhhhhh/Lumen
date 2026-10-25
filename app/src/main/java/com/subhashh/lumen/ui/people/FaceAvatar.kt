package com.subhashh.lumen.ui.people

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.subhashh.lumen.data.GridThumbnail
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ai.FaceStore
import com.subhashh.lumen.ai.Person
import com.subhashh.lumen.ui.images.version
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A person's face in a circle, or a rounded square (People's filter menu): their photo at a size
 * where the face comes out sharp (cached on disk like grid thumbnails), cut to a square around
 * the face with some hair and chin.
 */
@Composable
fun FaceAvatar(person: Person, cover: MediaItem, modifier: Modifier = Modifier, square: Boolean = false) {
    if (person.coverInVideo) {
        // A video's thumbnail is its first frame, maybe without them: the crop saved while indexing.
        val context = LocalContext.current
        Box(modifier.aspectRatio(1f).clip(if (square) RoundedCornerShape(12.dp) else CircleShape)) {
            AsyncImage(
                model = remember(person.coverFaceId) { FaceStore.cropFile(context, person.coverFaceId) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        FaceAvatar(cover, person.coverBox, modifier, square)
    }
}

@Composable
fun FaceAvatar(item: MediaItem, box: List<Float>, modifier: Modifier = Modifier, square: Boolean = false) {
    val context = LocalContext.current
    val photoPx = remember(box) { photoSizeFor(box) }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(if (square) RoundedCornerShape(12.dp) else CircleShape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
    ) {
        AsyncImage(
            model = remember(item.version, box) {
                ImageRequest.Builder(context)
                    .data(GridThumbnail(item, photoPx))
                    .size(photoPx, photoPx)
                    .transformations(FaceCrop(box))
                    .memoryCacheKey("face:${item.id}@${item.modified}:${box.joinToString(",")}")
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Long side to load a photo at so its face crop is about [FacePx] across; a few sizes, for cache hits. */
private fun photoSizeFor(box: List<Float>): Int {
    val faceShare = maxOf(box[2] - box[0], box[3] - box[1]).coerceAtLeast(0.01f) * Margin
    val needed = ceil(FacePx / faceShare).toInt()
    return listOf(512, 1024, 2048).firstOrNull { it >= needed } ?: 2048
}

private const val FacePx = 256
/** The crop is this many times the face box: hair, ears and chin. */
private const val Margin = 1.7f

private class FaceCrop(private val box: List<Float>) : Transformation() {
    override val cacheKey = "faceCrop:${box.joinToString(",")}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val cx = (box[0] + box[2]) / 2 * input.width
        val cy = (box[1] + box[3]) / 2 * input.height
        val side = (maxOf((box[2] - box[0]) * input.width, (box[3] - box[1]) * input.height) * Margin)
            .coerceAtMost(minOf(input.width, input.height).toFloat())
        val s = side.roundToInt().coerceAtLeast(1)
        // Slide the square inside the photo rather than shrinking it at the edges.
        val left = (cx - side / 2).roundToInt().coerceIn(0, input.width - s)
        val top = (cy - side / 2).roundToInt().coerceIn(0, input.height - s)
        return Bitmap.createBitmap(input, left, top, s, s)
    }
}
