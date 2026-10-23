package com.subhashh.lumen.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The selection highlight of a glass segmented control, iOS 26-style. At rest it's a flat fill
 * under the selected item. Choosing another item lifts it off the bar as a clear glass lens a
 * little bigger than the bar, which bends the bar and the icons beneath it as it springs across,
 * stretched by its speed, then sets it back down as a fill. A finger can pick it up and drag it.
 *
 * Positions are in item steps: 0 is the first item, 1 the second.
 */
@Stable
class LiquidPillState internal constructor(initial: Int, private val scope: CoroutineScope) {
    private val travel = Animatable(initial.toFloat())

    /** 0 resting flat, 1 lifted; the springs overshoot both ends a little. */
    internal val lift = Animatable(0f)

    private var dragPosition by mutableFloatStateOf(Float.NaN)
    private var job: Job? = null

    /** Where the pill is drawn, in item steps. */
    val position: Float get() = if (dragPosition.isNaN()) travel.value else dragPosition

    /** Speed in item steps per second, for the stretch. */
    internal val velocity: Float get() = if (dragPosition.isNaN()) travel.velocity else 0f

    /** False where the lens can't render (before Android 13): the pill just slides, flat. */
    internal var lifts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var target by mutableStateOf(initial)
        private set

    /** Travel to [index], lifted. Retargeting mid-flight keeps the current speed. */
    fun moveTo(index: Int) {
        if (index == target && dragPosition.isNaN()) return
        target = index
        val from = position
        dragPosition = Float.NaN
        job?.cancel()
        job = scope.launch {
            travel.snapTo(from)
            val up = if (lifts) launch { lift.animateTo(1f, LiftSpring) } else null
            var landing = false
            travel.animateTo(index.toFloat(), TravelSpring) {
                // Set it down just before it arrives, so the landing and the settle overlap.
                if (!landing && abs(value - index) < LandingDistance) {
                    landing = true
                    up?.cancel()
                    scope.launch { lift.animateTo(0f, SettleSpring) }
                }
            }
            if (!landing) lift.animateTo(0f, SettleSpring)
        }
    }

    internal fun startDrag() {
        dragPosition = travel.value
        job?.cancel()
        if (lifts) job = scope.launch { lift.animateTo(1f, LiftSpring) }
    }

    internal fun dragBy(steps: Float, count: Int) {
        if (dragPosition.isNaN()) return
        // Resists past the ends rather than stopping dead.
        val outside = dragPosition < 0f || dragPosition > count - 1f
        dragPosition = (dragPosition + steps * if (outside) 0.3f else 1f).coerceIn(-0.3f, count - 0.7f)
    }

    /** The finger let go: settle on the nearest item, which is returned. */
    internal fun endDrag(count: Int): Int = position.roundToInt().coerceIn(0, count - 1).also(::settle)

    /** Travel to [index] even if it's already the target (after a drag left the pill elsewhere). */
    internal fun settle(index: Int) {
        target = -1
        moveTo(index)
    }

    companion object {
        private val TravelSpring = spring<Float>(dampingRatio = 0.78f, stiffness = 260f)
        /** Under-damped: it swells past its lifted size and comes back. */
        private val LiftSpring = spring<Float>(dampingRatio = 0.55f, stiffness = 420f)
        /** Dips a touch under its resting size as it lands. */
        private val SettleSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 320f)
        private const val LandingDistance = 0.12f
    }
}

@Composable
fun rememberLiquidPillState(initial: Int): LiquidPillState {
    val scope = rememberCoroutineScope()
    return remember { LiquidPillState(initial.coerceAtLeast(0), scope) }
}

/**
 * Geometry shared by the flat fill and the lens.
 *
 * @param restSize the pill at rest, filling one item
 * @param liftedSize the pill lifted: taller than the bar, same proportions
 * @param center where the pill's centre is for a position in item steps, in the box it's placed in
 * @param stepPx one item step, px, to turn speed into stretch
 */
@Stable
class LiquidPillGeometry(
    val restSize: Size,
    val liftedSize: Size,
    val center: (Float) -> Offset,
    val stepPx: Float,
)

/** Size this frame: between rest and lifted by the lift, stretched along the travel with speed. */
private fun LiquidPillState.liveSize(geometry: LiquidPillGeometry): Size {
    val l = lift.value
    val w = geometry.restSize.width + (geometry.liftedSize.width - geometry.restSize.width) * l
    val h = geometry.restSize.height + (geometry.liftedSize.height - geometry.restSize.height) * l
    val stretch = (abs(velocity) * geometry.stepPx / StretchSpeed).coerceAtMost(MaxStretch)
    return Size(w * (1f + stretch), h * (1f - stretch * 0.3f))
}

/** Lays the pill out at its live size and position inside a box it matches the size of. */
private fun Modifier.pillPlacement(state: LiquidPillState, geometry: LiquidPillGeometry, offset: Offset): Modifier =
    layout { measurable, constraints ->
        val size = state.liveSize(geometry)
        val w = size.width.roundToInt().coerceAtLeast(1)
        val h = size.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(w, h))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val c = geometry.center(state.position) + offset
            placeable.place((c.x - w / 2f).roundToInt(), (c.y - h / 2f).roundToInt())
        }
    }

/** The resting fill, drawn under the items. It fades as the lens lifts off it. */
@Composable
fun LiquidPillFill(
    state: LiquidPillState,
    geometry: LiquidPillGeometry,
    color: Color,
    /** 0 hides it, e.g. while nothing is selected. */
    visible: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .pillPlacement(state, geometry, Offset.Zero)
            .graphicsLayer { alpha = visible() * (1f - state.lift.value).coerceIn(0f, 1f) }
            .background(color, CircleShape),
    )
}

/**
 * The lifted lens, drawn above the bar (outside its clip, so it can stand past its edges).
 * [backdrop] is what it bends: the page, the bar's exported glass and the recorded items.
 * Only composed while lifted, so at rest it costs nothing.
 */
@Composable
fun LiquidPillLens(
    state: LiquidPillState,
    geometry: LiquidPillGeometry,
    backdrop: Backdrop,
    /** Where the item row starts inside the box this is placed in. */
    offset: Offset,
    modifier: Modifier = Modifier,
) {
    val showing by remember(state) { derivedStateOf { state.lift.value > 0.002f } }
    if (!showing) return
    Box(
        modifier
            .pillPlacement(state, geometry, offset)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    val l = state.lift.value.coerceIn(0f, 1f)
                    lens(LensHeight.toPx() * l, LensAmount.toPx() * l, chromaticAberration = true)
                },
                highlight = { Highlight.Default.copy(alpha = state.lift.value.coerceIn(0f, 1f)) },
                shadow = { LensShadow.copy(alpha = state.lift.value.coerceIn(0f, 1f)) },
            ),
    )
}

/**
 * Horizontal drags over the items pick the pill up and carry it; on release it settles on the
 * nearest item and [onSelect] is called if that's a different one. Taps still reach the items.
 */
fun Modifier.liquidPillDrag(
    state: LiquidPillState,
    count: Int,
    stepPx: () -> Float,
    enabled: () -> Boolean,
    onCross: () -> Unit,
    onSelect: (Int) -> Unit,
): Modifier = pointerInput(state, count) {
    var lastNearest = 0
    var startIndex = 0
    detectHorizontalDragGestures(
        onDragStart = {
            if (!enabled()) return@detectHorizontalDragGestures
            startIndex = state.target
            lastNearest = startIndex
            state.startDrag()
        },
        onDragEnd = {
            if (!enabled()) return@detectHorizontalDragGestures
            val chosen = state.endDrag(count)
            // Dropped back where it started: not a re-tap of that item.
            if (chosen != startIndex) onSelect(chosen)
        },
        onDragCancel = { if (enabled()) state.settle(startIndex) },
        onHorizontalDrag = { change, amount ->
            if (!enabled()) return@detectHorizontalDragGestures
            change.consume()
            val step = stepPx()
            if (step > 0f) state.dragBy(amount / step, count)
            val nearest = state.position.roundToInt().coerceIn(0, count - 1)
            if (nearest != lastNearest) {
                lastNearest = nearest
                onCross()
            }
        },
    )
}

/** Speed (px/s) at which the stretch maxes out. */
private const val StretchSpeed = 9000f
private const val MaxStretch = 0.22f
private val LensHeight = 14.dp
private val LensAmount = 20.dp
private val LensShadow = Shadow(radius = 14.dp, offset = DpOffset(0.dp, 3.dp), color = Color.Black.copy(alpha = 0.28f))
