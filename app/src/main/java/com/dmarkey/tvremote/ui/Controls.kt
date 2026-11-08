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

/**
 * A button that fires on touch-down (not on release) for zero perceived lag,
 * and, if [repeat], keeps firing while held.
 */
@Composable
fun PadButton(
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    repeat: Boolean = false,
    tint: androidx.compose.ui.graphics.Color = Palette.key,
    content: @Composable () -> Unit,
) {
    val action by rememberUpdatedState(onPress)
    val scope = rememberCoroutineScope()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) Palette.keyPressed else tint)
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    pressed = true
                    action()
                    val repeater = if (repeat) scope.launch {
                        delay(REPEAT_AFTER_MS)
                        while (true) { action(); delay(REPEAT_EVERY_MS) }
                    } else null
                    tryAwaitRelease()
                    repeater?.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

enum class Zone { UP, DOWN, LEFT, RIGHT, OK }

/**
 * One big thumb-sized disc: four arrow wedges around an OK button. Arrows fire on
 * touch-down and auto-repeat while held. OK fires on release, or holding it sends
 * a long press (context menus on Google TV).
 */
@Suppress("DEPRECATION")
@Composable
fun DPad(
    size: Dp,
    onArrow: (Zone) -> Unit,
    onOk: () -> Unit,
    onOkLongStart: () -> Unit,
    onOkLongEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val arrow by rememberUpdatedState(onArrow)
    val ok by rememberUpdatedState(onOk)
    val longStart by rememberUpdatedState(onOkLongStart)
    val longEnd by rememberUpdatedState(onOkLongEnd)
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
                    if (zone == Zone.OK) {
                        var longSent = false
                        val timer = scope.launch {
                            delay(450)
                            longSent = true
                            longStart()
                        }
                        waitForUpOrCancellation()
                        timer.cancel()
                        if (longSent) longEnd() else ok()
                    } else {
                        arrow(zone)
                        val repeater = scope.launch {
                            delay(REPEAT_AFTER_MS)
                            while (true) { arrow(zone); delay(REPEAT_EVERY_MS) }
                        }
                        waitForUpOrCancellation()
                        repeater.cancel()
                    }
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
