package com.dmarkey.tvremote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.dmarkey.tvremote.ui.App

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()
    private lateinit var haptics: Haptics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        preferHighestRefreshRate()
        haptics = Haptics(this)
        setContent { App(vm, haptics) }
    }

    /**
     * The F9 Ultra's panel goes to 185 Hz but HyperOS often holds ordinary apps at
     * 60/120. Ask for the fastest mode at the current resolution; the LTPO panel
     * still drops back down when nothing on screen is moving.
     */
    private fun preferHighestRefreshRate() {
        val display = display ?: return
        val current = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onBackground()
    }

    /** While the remote is connected, the phone's volume rocker controls the TV (holding it repeats). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if ((code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            vm.screen.value == Screen.Remote && vm.session.link.value == Link.Connected
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                vm.session.key(code)
                haptics.tick()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
