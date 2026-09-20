package com.trionsandroid.app.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange

/**
 * One detector for pan, zoom, fling velocity and double-tap. Two separate detectors on the same
 * touch stream interfere, and detectTransformGestures exposes neither velocity nor the end of a gesture.
 */
suspend fun PointerInputScope.detectChartGestures(
    onTouchDown: () -> Unit,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onFlingVelocity: (velocityPxPerSec: Float) -> Unit,
    onDoubleTap: (position: Offset) -> Unit,
) {
    var lastTapDownMillis = -1L
    val doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis
    val touchSlop = viewConfiguration.touchSlop

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onTouchDown()

        val velocityTracker = VelocityTracker()
        velocityTracker.addPointerInputChange(down)
        var totalMovement = 0f
        var pastSlop = false
        var wasMultiTouch = false

        do {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) wasMultiTouch = true
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            totalMovement += panChange.getDistance()
            if (!pastSlop && totalMovement > touchSlop) pastSlop = true

            if (pastSlop && (zoomChange != 1f || panChange != Offset.Zero)) {
                onGesture(event.calculateCentroid(useCurrent = false), panChange, zoomChange)
            }
            event.changes.forEach { change ->
                if (change.positionChanged()) {
                    velocityTracker.addPointerInputChange(change)
                }
                change.consume()
            }
        } while (event.changes.any { it.pressed })

        if (!pastSlop) {
            // A tap, not a drag: check whether it completes a double-tap.
            if (lastTapDownMillis >= 0 && down.uptimeMillis - lastTapDownMillis <= doubleTapTimeoutMillis) {
                onDoubleTap(down.position)
                lastTapDownMillis = -1L
            } else {
                lastTapDownMillis = down.uptimeMillis
            }
        } else {
            lastTapDownMillis = -1L
            // Velocity is meaningless once a second finger joins, so only fling after a single-finger pan.
            if (!wasMultiTouch) {
                onFlingVelocity(velocityTracker.calculateVelocity().x)
            }
        }
    }
}
