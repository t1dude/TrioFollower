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
 * A single custom gesture detector combining pan+zoom (like detectTransformGestures), fling
 * velocity on release, and double-tap — all in one pointerInput block. Compose's built-in
 * detectTransformGestures doesn't expose touch velocity or a "gesture ended" hook, and running
 * a second, independent gesture recognizer (e.g. detectTapGestures) alongside it on the same
 * touch stream doesn't work reliably: whichever detector consumes position changes first can
 * make the other unable to recognize its own gesture, since most of Compose's detectors abort
 * when they see a change already marked consumed.
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

        do {
            val event = awaitPointerEvent()
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
            // A tap, not a drag/pinch — check if it completes a double-tap.
            if (lastTapDownMillis >= 0 && down.uptimeMillis - lastTapDownMillis <= doubleTapTimeoutMillis) {
                onDoubleTap(down.position)
                lastTapDownMillis = -1L
            } else {
                lastTapDownMillis = down.uptimeMillis
            }
        } else {
            lastTapDownMillis = -1L
            onFlingVelocity(velocityTracker.calculateVelocity().x)
        }
    }
}
