package com.subhashh.lumen.ui.collections

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.transition.CoverTransition

/**
 * Collections (or People) under an open album or person: kept composed, so its images stay in
 * memory and nothing is rebuilt when the cover flies back. Invisible and untouchable until a
 * cover flies, then fading and scaling with it.
 */
fun Modifier.coverUnderlay(transition: CoverTransition): Modifier = this
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
    .graphicsLayer {
        alpha = transition.parentAlpha()
        scaleX = transition.parentScale()
        scaleY = transition.parentScale()
    }

/** "1,204 Photos, 37 Videos", leaving out a kind there's none of. */
fun heroSubtitle(items: List<MediaItem>): String {
    val videos = items.count { it.isVideo }
    val photos = items.size - videos
    return listOfNotNull(
        photos.takeIf { it > 0 }?.let { "%,d %s".format(it, if (it == 1) "Photo" else "Photos") },
        videos.takeIf { it > 0 }?.let { "%,d %s".format(it, if (it == 1) "Video" else "Videos") },
    ).joinToString(", ")
}
