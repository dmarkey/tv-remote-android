package com.dmarkey.tvremote

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Crisp taps on the F9 Ultra's X-axis linear motor. Uses predefined effects rather
 * than View haptics, so HyperOS's "touch feedback" toggle doesn't silence them.
 */
class Haptics(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
    private val tick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    private val click = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    private val heavy = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)

    fun tick() = vibrator.vibrate(tick, attrs)
    fun click() = vibrator.vibrate(click, attrs)
    fun heavy() = vibrator.vibrate(heavy, attrs)
}
