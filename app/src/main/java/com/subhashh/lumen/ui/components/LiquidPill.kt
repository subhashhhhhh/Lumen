/*
 * The selection pill's motion (the travel spring, the two lift springs and the fast material
 * spring, the acceleration squash and the glass-to-flat hand-off on landing, with their tuned
 * constants) is ported from liquid_glass_easy 4.3.4: LiquidGlassAnimatedNavBar,
 * LiquidGlassLensMotion and liquidGlassSpringStep. https://pub.dev/packages/liquid_glass_easy
 *
 * Copyright (c) 2025 Ahmed Gamil. MIT License: permission is granted, free of charge, to use,
 * copy, modify, merge, publish, distribute, sublicense and/or sell copies, provided this
 * notice is included. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 *
 * The drawing is Lumen's own, on Kyant0's backdrop library.
 */
package com.subhashh.lumen.ui.components

import com.subhashh.lumen.ui.theme.LocalMotion
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The selection highlight of a glass segmented control, iOS 26-style.
 *
 * At rest it's a flat fill under the selected item. Choosing another item lifts it at once: it
 * inflates past the bar on two under-damped springs (one per axis, so width and height overshoot
 * by slightly different amounts) and stays up for the whole trip, which is a spring of its own.
 * Only once it has nearly landed is the lift released, so it deflates where it stands, dipping a
 * little under its resting size. On the way it stretches wide as it launches and squashes narrow
 * as it brakes, from its measured acceleration. The glass is a third, much faster spring, and
 * fades out on landing; once nothing is left to see, [isFlat] flips and the plain fill takes over.
 *
 * A finger can pick it up and drag it; it follows smoothly and settles on release like a tap.
 * Positions are in item steps: 0 is the first item, 1 the second.
 */
@Stable
class LiquidPillState internal constructor(initial: Int, private val scope: CoroutineScope) {

    /** Where the pill is drawn, in item steps. */
    var position by mutableFloatStateOf(initial.toFloat())
        private set

    /** Width lift: 0 at rest, 1 at the lifted size; overshoots both. */
    var liftX by mutableFloatStateOf(0f)
        private set

    /** Height lift; see [liftX]. */
    var liftY by mutableFloatStateOf(0f)
        private set

    /** How much of the glass material is on, 0..1. */
    var material by mutableFloatStateOf(0f)
        private set

    /** Acceleration squash: width scales by 1 + this, height by 1 − this. */
    var deviation by mutableFloatStateOf(0f)
        private set

    /** How much still reads as glass: 1 lifted, 0 handed back to the flat fill. */
    var glassPresence by mutableFloatStateOf(0f)
        private set

    /** Nothing left to see but a flat fill at rest size, which the plain fill draws identically. */
    var isFlat by mutableStateOf(true)
        private set

    /** The item the pill is headed for. */
    var target by mutableIntStateOf(initial)
        private set

    /** One item step in px; the squash model samples the travel in pixels. */
    internal var stepPx = 0f

    /** False where the lens can't render (before Android 13): it travels without lifting. */
    internal val lifts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private var travelPos = initial.toDouble()
    private var travelVel = 0.0
    private var travelTarget = travelPos
    private var travelFrom = travelPos
    private var travelActive = false

    private var lifted = false
    private var liftXPos = 0.0
    private var liftXVel = 0.0
    private var liftYPos = 0.0
    private var liftYVel = 0.0
    private var materialPos = 0.0
    private var materialVel = 0.0

    private var dragging = false
    private var dragTarget = 0.0
    private var dragFollow = 0.0

    private var travelSign = 0.0
    private var travelSignEased = 0.0
    private var handover = 1.0
    private var squash = 0.0
    private val squashModel = SquashModel()
    private var running = false

    /** Reduce Motion: every move lands at once, with no lift. */
    internal var reduceMotion = false

    /** Travel to [index], lifted. Retargeting mid-flight keeps the current speed. */
    fun moveTo(index: Int) {
        if (index == target && !dragging) return
        if (reduceMotion) {
            snapTo(index)
            return
        }
        target = index
        dragging = false
        travelActive = true
        lifted = lifts
        travelFrom = travelPos
        travelTarget = index.toDouble()
        travelSign = signOf(travelTarget - travelFrom)
        isFlat = false
        start()
    }

    /** On [index] at rest, no travel and no lift. */
    private fun snapTo(index: Int) {
        target = index
        dragging = false
        travelActive = false
        lifted = false
        travelPos = index.toDouble()
        travelFrom = travelPos
        travelTarget = travelPos
        travelVel = 0.0
        liftXPos = 0.0; liftXVel = 0.0
        liftYPos = 0.0; liftYVel = 0.0
        materialPos = 0.0; materialVel = 0.0
        handover = 1.0
        squash = 0.0
        squashModel.stop()
        travelSign = 0.0
        travelSignEased = 0.0
        position = index.toFloat()
        liftX = 0f
        liftY = 0f
        material = 0f
        deviation = 0f
        glassPresence = 0f
        isFlat = true
    }

    internal fun startDrag() {
        dragging = true
        travelActive = false
        lifted = lifts
        // The hand takes the deformation back off the travel.
        travelSign = 0.0
        dragFollow = travelPos
        dragTarget = travelPos
        travelVel = 0.0
        isFlat = false
        start()
    }

    /** Moves the finger's target by [steps]; returns the item now under it. */
    internal fun dragBy(steps: Float, count: Int): Int {
        if (dragging) dragTarget = (dragTarget + steps).coerceIn(0.0, count - 1.0)
        return dragTarget.roundToInt()
    }

    /** The finger let go: settle on the nearest item, which is returned. */
    internal fun endDrag(count: Int): Int {
        val next = dragFollow.roundToInt().coerceIn(0, count - 1)
        settle(next)
        return next
    }

    /** Travel to [index] from wherever the finger left the pill, still lifted; comes down on landing. */
    internal fun settle(index: Int) {
        if (reduceMotion) {
            snapTo(index)
            return
        }
        if (!dragging) {
            target = -1
            moveTo(index)
            return
        }
        dragging = false
        target = index
        travelActive = true
        travelPos = dragFollow
        travelVel = 0.0
        travelFrom = dragFollow
        travelTarget = index.toDouble()
        travelSign = signOf(travelTarget - travelFrom)
        start()
    }

    private fun start() {
        if (running) return
        running = true
        scope.launch {
            var last = -1L
            try {
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = if (last < 0) 0.0 else (now - last) / 1e9
                    last = now
                    if (!tick(dt, now / 1e9)) break
                }
            } finally {
                running = false
            }
        }
    }

    /** One frame of everything. False once it has all settled. */
    private fun tick(dt: Double, now: Double): Boolean {
        // 1) Travel.
        var travelSettled = true
        if (travelActive) {
            val (x, v) = springStep(travelPos, travelVel, travelTarget, dt, TravelStiffness, TravelDamping)
            travelPos = x
            travelVel = v
            travelSettled = abs(travelPos - travelTarget) < 0.003 && abs(travelVel) < 0.05
            if (travelSettled) {
                travelPos = travelTarget
                travelVel = 0.0
            }
        }

        // 2) Under a finger, chase it smoothly.
        if (dragging) dragFollow += (dragTarget - dragFollow) * (1 - exp(-dt / FollowTau))

        // 3) The lift: held for the whole trip, released on landing.
        if (!dragging && (travelSettled || travelProgress() >= HandoverStart)) lifted = false
        val liftTarget = if (lifted) 1.0 else 0.0
        stepLift(liftXPos, liftXVel, liftTarget, dt, LiftStiffness, LiftDampingX).let { liftXPos = it.first; liftXVel = it.second }
        stepLift(liftYPos, liftYVel, liftTarget, dt, LiftStiffness, LiftDampingY).let { liftYPos = it.first; liftYVel = it.second }
        stepLift(materialPos, materialVel, liftTarget, dt, MaterialStiffness, MaterialDamping).let {
            materialPos = it.first; materialVel = it.second
        }
        val liftSettled = !lifted && liftXPos == 0.0 && liftYPos == 0.0 && materialPos == 0.0

        // 4) The deflation outlives the spring that carried the pill there.
        if (travelActive && travelSettled && liftSettled && !dragging) travelActive = false

        // 5) Squash from the acceleration of where the pill is drawn this frame.
        val drawn = if (dragging) dragFollow else travelPos
        if (stepPx > 0f) {
            if (!squashModel.isTracking) squashModel.start()
            squash = squashModel.track(drawn * stepPx, now, dt)
            if (travelSignEased == 0.0) {
                travelSignEased = travelSign
            } else if (travelSignEased != travelSign) {
                travelSignEased += (travelSign - travelSignEased) * (1 - exp(-dt / SignTau))
                if (abs(travelSign - travelSignEased) < 0.01) travelSignEased = travelSign
            }
            // Keep the force's size but take its sign from the direction of travel, so a trip
            // deforms one way the whole way instead of turning inside out halfway.
            val key = travelSignEased
            if (key != 0.0) squash = squash * (1 - abs(key)) - key * abs(squash)
        }

        // 6) The glass leaves on the same landing the deflation runs off.
        val handoverTarget = if (lifted) 0.0 else 1.0
        val tau = if (handoverTarget > handover) HandoverTau else GlassReturnTau
        handover += (handoverTarget - handover) * (1 - exp(-dt / tau))
        if (abs(handoverTarget - handover) < 0.002) handover = handoverTarget

        val settled = !travelActive && !dragging && liftSettled && abs(squash) < 0.0005 && handover >= 1.0
        if (settled) {
            squashModel.stop()
            squash = 0.0
            travelSign = 0.0
            travelSignEased = 0.0
        }
        position = drawn.toFloat()
        liftX = liftXPos.toFloat()
        liftY = liftYPos.toFloat()
        material = materialPos.coerceIn(0.0, 1.0).toFloat()
        deviation = squash.toFloat()
        glassPresence = (1 - handover).coerceIn(0.0, 1.0).toFloat()
        isFlat = settled
        return !settled
    }

    private fun travelProgress(): Double {
        val span = abs(travelTarget - travelFrom)
        if (span < 1e-6) return 1.0
        return (1 - abs(travelTarget - travelPos) / span).coerceIn(0.0, 1.0)
    }

    private fun signOf(span: Double) = if (abs(span) < 1e-6) 0.0 else sign(span)

    private companion object {
        const val TravelStiffness = 280.0
        const val TravelDamping = 31.4
        /** Damping ratio ~0.6 across, ~0.7 down: the width overshoots a shade further. */
        const val LiftStiffness = 250.0
        const val LiftDampingX = 19.0
        const val LiftDampingY = 22.1
        /** Critically damped and four times as stiff: the glass is on long before the size settles. */
        const val MaterialStiffness = 1000.0
        const val MaterialDamping = 63.3
        const val HandoverStart = 0.92
        const val HandoverTau = 0.09
        const val GlassReturnTau = 0.05
        const val FollowTau = 0.05
        const val SignTau = 0.25

        /** Semi-implicit Euler in 1/240 s substeps, so a long frame can't blow it up. */
        fun springStep(x: Double, vel: Double, target: Double, dt: Double, stiffness: Double, damping: Double): Pair<Double, Double> {
            var t = dt
            var px = x
            var pv = vel
            while (t > 0) {
                val step = if (t > 1 / 240.0) 1 / 240.0 else t
                pv += (-stiffness * (px - target) - damping * pv) * step
                px += pv * step
                t -= step
            }
            return px to pv
        }

        /** A spring step, snapped to its target once there's nothing left to see. */
        fun stepLift(x: Double, vel: Double, target: Double, dt: Double, stiffness: Double, damping: Double): Pair<Double, Double> {
            val r = springStep(x, vel, target, dt, stiffness, damping)
            return if (abs(r.first - target) < 0.0008 && abs(r.second) < 0.01) target to 0.0 else r
        }
    }
}

/**
 * Acceleration to deformation: the pill's position over a short window, differentiated twice
 * and averaged, scaled and clamped, then eased so one noisy frame can't flick it. In px/s².
 */
private class SquashModel {
    private val history = ArrayList<Pair<Double, Double>>()
    var isTracking = false
        private set
    private var value = 0.0

    fun start() {
        history.clear()
        value = 0.0
        isTracking = true
    }

    fun stop() {
        isTracking = false
        history.clear()
        value = 0.0
    }

    fun track(x: Double, now: Double, dt: Double): Double {
        if (!isTracking) return value
        history.add(x to now)
        history.removeAll { it.second < now - SampleWindow }
        val raw = (averageAcceleration() * Sensitivity).coerceIn(-MaxDeformation, MaxDeformation)
        value += (raw - value) * (dt / ResponseTime).coerceIn(0.0, 1.0)
        return value
    }

    private fun averageAcceleration(): Double {
        if (history.size < 3) return 0.0
        val velocities = ArrayList<Pair<Double, Double>>(history.size)
        for (i in 1 until history.size) {
            val dt = history[i].second - history[i - 1].second
            if (dt <= 0) continue
            velocities += (history[i].first - history[i - 1].first) / dt to (history[i].second + history[i - 1].second) / 2
        }
        if (velocities.size < 2) return 0.0
        var total = 0.0
        var count = 0
        for (i in 1 until velocities.size) {
            val dt = velocities[i].second - velocities[i - 1].second
            if (dt <= 0) continue
            total += (velocities[i].first - velocities[i - 1].first) / dt
            count++
        }
        return if (count == 0) 0.0 else total / count
    }

    private companion object {
        const val SampleWindow = 0.3
        const val Sensitivity = 0.00007
        /** ±12%: the pill travels inside the bar, and more would climb out of it. */
        const val MaxDeformation = 0.12
        const val ResponseTime = 0.18
    }
}

@Composable
fun rememberLiquidPillState(initial: Int): LiquidPillState {
    val scope = rememberCoroutineScope()
    val reduce = LocalMotion.current.reduce
    return remember { LiquidPillState(initial.coerceAtLeast(0), scope) }.also { it.reduceMotion = reduce }
}

/**
 * Geometry shared by the flat fill and the lens.
 *
 * @param restSize the pill at rest, filling one item
 * @param liftedSize the pill lifted: taller than the bar, same proportions
 * @param center where the pill's centre is for a position in item steps, in the item row
 * @param stepPx one item step, px
 */
@Stable
class LiquidPillGeometry(
    val restSize: Size,
    val liftedSize: Size,
    val center: (Float) -> Offset,
    val stepPx: Float,
)

/** Size this frame: the two lift springs between rest and lifted, then the squash. */
private fun LiquidPillState.liveSize(geometry: LiquidPillGeometry): Size {
    val w = geometry.restSize.width + (geometry.liftedSize.width - geometry.restSize.width) * liftX
    val h = geometry.restSize.height + (geometry.liftedSize.height - geometry.restSize.height) * liftY
    return Size(w * (1f + deviation), h * (1f - deviation))
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

/**
 * The flat fill under the items. Drawn while the pill is flat (or can't lift); while lifted
 * the lens draws the fill itself, under the items it refracts.
 */
@Composable
fun LiquidPillFill(
    state: LiquidPillState,
    geometry: LiquidPillGeometry,
    color: Color,
    /** 0 hides it, e.g. while nothing is selected. */
    visible: () -> Float,
    modifier: Modifier = Modifier,
) {
    state.stepPx = geometry.stepPx
    if (state.lifts && !state.isFlat) return
    Box(
        modifier
            .pillPlacement(state, geometry, Offset.Zero)
            .background(color.copy(alpha = color.alpha * visible()), CircleShape),
    )
}

/**
 * The lifted lens, drawn above the bar and outside its clip so it can stand past its edges.
 * It bends the page, the bar's exported glass, the fill (fading out as the glass comes on) and
 * the recorded items, in that order. Only composed while the pill isn't flat.
 */
@Composable
fun LiquidPillLens(
    state: LiquidPillState,
    geometry: LiquidPillGeometry,
    page: Backdrop,
    bar: Backdrop,
    items: Backdrop,
    fillColor: Color,
    /** Where the item row starts inside the box this is placed in. */
    offset: Offset,
    modifier: Modifier = Modifier,
) {
    if (!state.lifts || state.isFlat) return
    // Settings → Appearance: the pill only bends what's under it with Liquid Glass on.
    val lens = com.subhashh.lumen.ui.theme.LocalGlass.current.lens
    val backdrop = remember(page, bar, items, fillColor) {
        PillBackdrop(page, bar, items) { fillColor.copy(alpha = fillColor.alpha * (1f - state.material)) }
    }
    Box(
        modifier
            .pillPlacement(state, geometry, offset)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    if (lens) lens(
                        LensHeight.toPx() * state.glassPresence,
                        LensAmount.toPx() * state.material,
                        chromaticAberration = true,
                    )
                },
                highlight = {
                    val alpha = state.material * rimOf(state.glassPresence)
                    if (alpha > 0f) Highlight.Default.copy(alpha = alpha) else null
                },
                shadow = {
                    val alpha = state.material * rimOf(state.glassPresence)
                    if (alpha > 0f) LensShadow.copy(alpha = alpha) else null
                },
            ),
    )
}

/** The rim and its shadow are the first things shed: gone by the time the hand-off is 55% through. */
private fun rimOf(presence: Float) = ((presence - 0.45f) / 0.55f).coerceIn(0f, 1f)

/** Page, bar glass, fill, items: the fill sits under the items, or it would grey the icons. */
private class PillBackdrop(
    private val page: Backdrop,
    private val bar: Backdrop,
    private val items: Backdrop,
    private val fill: () -> Color,
) : Backdrop {
    override val isCoordinatesDependent = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(page) { drawBackdrop(density, coordinates, layerBlock) }
        with(bar) { drawBackdrop(density, coordinates, layerBlock) }
        val color = fill()
        if (color.alpha > 0f) {
            // Past every edge: the recorded area includes the lens's padding.
            drawRect(color, topLeft = Offset(-size.width, -size.height), size = Size(size.width * 3, size.height * 3))
        }
        with(items) { drawBackdrop(density, coordinates, layerBlock) }
    }
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
            if (step <= 0f) return@detectHorizontalDragGestures
            val nearest = state.dragBy(amount / step, count)
            if (nearest != lastNearest) {
                lastNearest = nearest
                onCross()
            }
        },
    )
}

private val LensHeight = 12.dp
private val LensAmount = 14.dp
private val LensShadow = Shadow(radius = 12.dp, offset = DpOffset(0.dp, 2.dp), color = Color.Black.copy(alpha = 0.3f))
