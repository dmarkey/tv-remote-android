package com.dmarkey.tvremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot

private const val REPEAT_AFTER_MS = 380L
private const val REPEAT_EVERY_MS = 85L

/** Hold this long and a key becomes a TV long press (same as the desktop app's 300 ms, plus slack for thumbs). */
const val LONG_PRESS_MS = 350L

/**
 * A remote button. Three behaviours:
 *  - default: fires on touch-down (not release) for zero perceived lag;
 *  - [repeat]: fires on touch-down, then keeps firing while held;
 *  - [onLongStart] set: a tap fires [onPress] on release, a hold sends
 *    [onLongStart] after [LONG_PRESS_MS] and [onLongEnd] on release, i.e. a real
 *    long press on the TV (Home → app/settings panel, Power → power menu, ...).
 */
@Composable
fun PadButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    repeat: Boolean = false,
    onLongStart: (() -> Unit)? = null,
    onLongEnd: (() -> Unit)? = null,
    tint: androidx.compose.ui.graphics.Color = Palette.key,
    content: @Composable () -> Unit,
) {
    val action by rememberUpdatedState(onPress)
    val longStart by rememberUpdatedState(onLongStart)
    val longEnd by rememberUpdatedState(onLongEnd)
    val hasLong = onLongStart != null
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) Palette.keyPressed else tint)
            .pointerInput(repeat, hasLong) {
                detectTapGestures(onPress = {
                    pressed = true
                    if (hasLong) {
                        var long = false
                        val timer = scope.launch {
                            delay(LONG_PRESS_MS)
                            long = true
                            longStart?.invoke()
                        }
                        val released = tryAwaitRelease()
                        timer.cancel()
                        // A finger that slides off without a long press is a cancel, like Android buttons.
                        if (long) longEnd?.invoke() else if (released) action()
                    } else {
                        action()
                        val repeater = if (repeat) scope.launch {
                            delay(REPEAT_AFTER_MS)
                            while (true) { action(); delay(REPEAT_EVERY_MS) }
                        } else null
                        tryAwaitRelease()
                        repeater?.cancel()
                    }
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

enum class Zone { UP, DOWN, LEFT, RIGHT, OK }

/**
 * One big thumb-sized disc: four arrow wedges around an OK button. Every zone
 * behaves like a physical remote key: a tap fires on release, and holding past
 * [LONG_PRESS_MS] sends a real long press until release, so the TV does its own
 * hold behaviour (continuous scrubbing in players, list acceleration, OK context menus).
 */
@Suppress("DEPRECATION")
@Composable
fun DPad(
    size: Dp,
    onTap: (Zone) -> Unit,
    onLongStart: (Zone) -> Unit,
    onLongEnd: (Zone) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tap by rememberUpdatedState(onTap)
    val longStart by rememberUpdatedState(onLongStart)
    val longEnd by rememberUpdatedState(onLongEnd)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf<Zone?>(null) }

    BoxWithConstraints(
        modifier
            .size(size)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val c = this.size.width / 2f
                    val dx = down.position.x - c
                    val dy = down.position.y - c
                    val zone = when {
                        hypot(dx, dy) < c * OK_RATIO -> Zone.OK
                        else -> when (Math.toDegrees(atan2(dy, dx).toDouble())) {
                            in -45.0..45.0 -> Zone.RIGHT
                            in 45.0..135.0 -> Zone.DOWN
                            in -135.0..-45.0 -> Zone.UP
                            else -> Zone.LEFT
                        }
                    }
                    pressed = zone
                    var longSent = false
                    val timer = scope.launch {
                        delay(LONG_PRESS_MS)
                        longSent = true
                        longStart(zone)
                    }
                    val up = waitForUpOrCancellation()
                    timer.cancel()
                    // A long press always gets its key-up; a short touch that was cancelled sends nothing.
                    if (longSent) longEnd(zone) else if (up != null) tap(zone)
                    pressed = null
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            drawCircle(Palette.key, r)
            val start = when (pressed) {
                Zone.RIGHT -> -45f; Zone.DOWN -> 45f; Zone.LEFT -> 135f; Zone.UP -> 225f; else -> null
            }
            if (start != null) drawArc(Palette.keyPressed, start, 90f, useCenter = true)
            drawCircle(Palette.background, r * OK_RATIO + 3.dp.toPx())
            drawCircle(if (pressed == Zone.OK) Palette.accentPressed else Palette.accent, r * OK_RATIO, Offset(r, r))
        }
        val edge = maxWidth / 2 - 34.dp
        val icon = Modifier.size(40.dp)
        Icon(Icons.Rounded.KeyboardArrowUp, null, icon.align(Alignment.Center).offset(y = -edge), tint = Palette.label)
        Icon(Icons.Rounded.KeyboardArrowDown, null, icon.align(Alignment.Center).offset(y = edge), tint = Palette.label)
        // Deliberately not the auto-mirrored icons: these are TV directions, not reading direction.
        Icon(Icons.Rounded.KeyboardArrowLeft, null, icon.align(Alignment.Center).offset(x = -edge), tint = Palette.label)
        Icon(Icons.Rounded.KeyboardArrowRight, null, icon.align(Alignment.Center).offset(x = edge), tint = Palette.label)
    }
}

private const val OK_RATIO = 0.36f
