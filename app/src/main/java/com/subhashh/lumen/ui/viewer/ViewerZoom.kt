package com.subhashh.lumen.ui.viewer

import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomableState

/**
 * Double-tap zoom like iOS: in just far enough to fill the screen (a wide photo fills its
 * height), at least 2× and at most 3×, centred on the tap; double-tap again to come back out.
 * Telephoto's own goes straight to the 8× pinch limit.
 */
internal class FillOnDoubleClick(private val viewport: () -> IntSize) : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
        if ((state.zoomFraction ?: 0f) > 0.01f) {
            state.resetZoom(ZoomSpring)
            return
        }
        val content = state.transformedContentBounds
        val view = viewport()
        val fill = if (content.width > 0f && content.height > 0f && view.width > 0 && view.height > 0) {
            maxOf(view.width / content.width, view.height / content.height)
        } else {
            MinZoom
        }
        state.zoomTo(fill.coerceIn(MinZoom, MaxZoom), centroid, ZoomSpring)
    }

    private companion object {
        const val MinZoom = 2f
        const val MaxZoom = 3f
        val ZoomSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)
    }
}
