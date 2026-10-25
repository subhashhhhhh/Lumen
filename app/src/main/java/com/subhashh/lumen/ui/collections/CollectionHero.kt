package com.subhashh.lumen.ui.collections

import android.content.Context
import androidx.compose.ui.Modifier
import com.subhashh.lumen.R
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
        alpha = transition.underlayAlpha()
        scaleX = transition.parentScale()
        scaleY = transition.parentScale()
    }

/** "1,204 Photos, 37 Videos", leaving out a kind there's none of. */
fun heroSubtitle(context: Context, items: List<MediaItem>): String {
    val videos = items.count { it.isVideo }
    val photos = items.size - videos
    val res = context.resources
    return listOfNotNull(
        photos.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.main_hero_photos, it, it) },
        videos.takeIf { it > 0 }?.let { res.getQuantityString(R.plurals.main_hero_videos, it, it) },
    ).joinToString(context.getString(R.string.main_list_separator))
}
