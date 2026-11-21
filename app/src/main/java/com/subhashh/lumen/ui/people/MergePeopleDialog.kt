package com.subhashh.lumen.ui.people

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.subhashh.lumen.R
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subhashh.lumen.data.MediaItem
import com.subhashh.lumen.ai.Person
import com.subhashh.lumen.ui.theme.Lumen
import com.subhashh.lumen.ui.components.DialogAction
import com.subhashh.lumen.ui.components.DialogActionList
import com.subhashh.lumen.ui.components.DialogScrim
import com.subhashh.lumen.ui.components.dialogCard
import com.subhashh.lumen.ui.components.dialogInset

/** Naming [person] [name], which [match] already has: offer to make them one person. */
@Immutable
data class MergeProposal(
    val person: Person,
    val cover: MediaItem,
    val match: Person,
    val matchCover: MediaItem,
    val name: String,
)

/**
 * iOS-style alert for a name that's taken: the two faces side by side, drawing together as it
 * opens, then Merge, Keep Separate (both keep the name) or Cancel (nothing is named).
 */
@Composable
fun MergePeopleDialog(
    proposal: MergeProposal?,
    onMerge: (MergeProposal) -> Unit,
    onKeepSeparate: (MergeProposal) -> Unit,
    onDismiss: () -> Unit,
) {
    val open = proposal != null
    BackHandler(enabled = open, onBack = onDismiss)
    DialogScrim(open, onDismiss)
    AnimatedVisibility(
        visible = open,
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 600f), initialScale = 0.9f) + fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        val p = proposal ?: return@AnimatedVisibility
        val card = Lumen.colors.dialog
        val material = Lumen.material
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(if (material) 320.dp else 300.dp)
                    .dialogCard()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Faces(p, ring = card)
                Spacer(Modifier.height(16.dp))
                // Centred in both styles: the faces above are.
                Text(
                    stringResource(R.string.main_merge_with_name, p.name),
                    color = Lumen.colors.text,
                    style = Lumen.type.dialogTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = dialogInset),
                )
                Spacer(Modifier.height(if (material) 12.dp else 6.dp))
                Text(
                    stringResource(R.string.main_merge_proposal_message, p.name, photos((p.person.mediaIds + p.match.mediaIds).size)),
                    color = Lumen.colors.secondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = dialogInset),
                )
                Spacer(Modifier.height(20.dp))
                DialogActionList(
                    listOf(
                        DialogAction(stringResource(R.string.main_merge), primary = true) { onMerge(p) },
                        DialogAction(stringResource(R.string.main_keep_separate)) { onKeepSeparate(p) },
                        DialogAction(stringResource(R.string.common_cancel), onClick = onDismiss),
                    ),
                )
            }
        }
    }
}

/** The new face on the left, the named one on the right and in front, sliding together. */
@Composable
private fun Faces(p: MergeProposal, ring: Color) {
    val together = remember(p) { Animatable(0f) }
    LaunchedEffect(p) { together.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 260f)) }
    Box(Modifier.width(220.dp).height(FaceSize + RingWidth * 2), contentAlignment = Alignment.Center) {
        Face(p.person, p.cover, ring, Modifier.graphicsLayer { translationX = -spread(together.value).toPx() })
        Face(p.match, p.matchCover, ring, Modifier.graphicsLayer { translationX = spread(together.value).toPx() })
    }
}

/** Each face's distance from the middle: apart, then overlapping by a third. */
private fun spread(t: Float): Dp = 54.dp + (28.dp - 54.dp) * t

@Composable
private fun Face(person: Person, cover: MediaItem, ring: Color, modifier: Modifier) {
    // A ring in the card's colour separates the overlapping faces.
    Box(
        modifier
            .size(FaceSize + RingWidth * 2)
            .clip(CircleShape)
            .background(ring)
            .padding(RingWidth),
    ) {
        FaceAvatar(person, cover, Modifier.fillMaxSize())
    }
}

@Composable
private fun photos(n: Int): String = pluralStringResource(R.plurals.main_merge_photo_count, n, n)

private val FaceSize = 84.dp
private val RingWidth = 3.dp
