package com.subhashh.lumen.ui.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ui.transition.CoverTransition
import com.subhashh.lumen.ui.transition.HeroCover
import com.subhashh.lumen.ui.transition.HeroImage

/** An album's or person's cover across the top of its page, with its name and count over it. */
@Composable
fun CollectionHero(
    cover: HeroCover,
    title: String,
    subtitle: String,
    transition: CoverTransition,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        HeroImage(cover, transition, Modifier.fillMaxSize())
        // Keeps the status bar and the back button readable on a bright photo.
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
        )
        // The name sits on a darkened bottom edge that runs into the grid's black.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(BottomScrim)),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        ) {
            Text(title, style = Title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = Subtitle)
        }
    }
}

/** [CollectionHero] for [LibraryScreen]'s `hero` slot. */
fun heroSlot(cover: HeroCover, title: String, items: List<MediaItem>, transition: CoverTransition): @Composable (Modifier) -> Unit =
    { modifier -> CollectionHero(cover, title, heroSubtitle(items), transition, modifier) }

/** The page an album or person was opened from, drawn under it while its cover flies. */
@Composable
fun CoverUnderlay(transition: CoverTransition, page: @Composable (Modifier) -> Unit) {
    if (!transition.flying) return
    page(
        Modifier.graphicsLayer {
            alpha = transition.parentAlpha()
            scaleX = transition.parentScale()
            scaleY = transition.parentScale()
        },
    )
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

private val BottomScrim = listOf(
    Color.Transparent,
    Color.Black.copy(alpha = 0.18f),
    Color.Black.copy(alpha = 0.55f),
    Color.Black.copy(alpha = 0.85f),
)

private val TextShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 6f)
private val Title = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, shadow = TextShadow)
private val Subtitle = TextStyle(color = Color(0xD9FFFFFF), fontSize = 15.sp, fontWeight = FontWeight.Medium, shadow = TextShadow)
