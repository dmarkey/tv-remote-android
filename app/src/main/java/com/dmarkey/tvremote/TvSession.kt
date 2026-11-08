package com.dmarkey.tvremote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

sealed interface Link {
    data object Idle : Link
    data object Connecting : Link
    data object Connected : Link
    /** The TV rejected our certificate: it was reset, or it forgot us. */
    data object NeedsPairing : Link
    data class Offline(val reason: String) : Link
}

data class Volume(val level: Int, val max: Int, val muted: Boolean)

/**
 * The remote-control connection on port 6466. It keeps reconnecting until
 * [disconnect] is called. Sends never block the caller.
 */
class TvSession(private val scope: CoroutineScope) {
    private val _link = MutableStateFlow<Link>(Link.Idle)
    val link: StateFlow<Link> = _link
    private val _volume = MutableStateFlow<Volume?>(null)
    val volume: StateFlow<Volume?> = _volume
    private val _isOn = MutableStateFlow(true)
    val isOn: StateFlow<Boolean> = _isOn

    private val writer = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val writeLock = Any()

    private var job: Job? = null
    private var host: String? = null
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var out: OutputStream? = null

    private var imeCounter = 0
    private var fieldCounter = 0
    private var haveImeCounter = false
    private var haveFieldCounter = false

    fun connect(host: String) {
        if (this.host == host && job?.isActive == true) return
        disconnect()
        this.host = host
        job = scope.launch(Dispatchers.IO) { run(host) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        host = null
        closeSocket()
        _link.value = Link.Idle
    }

    private suspend fun run(host: String) {
        // A cancelled run must not overwrite the state of the run that replaced it.
        suspend fun setLink(value: Link) {
            if (currentCoroutineContext().isActive) _link.value = value
        }
        var backoff = 250L
        var authFailures = 0
        while (currentCoroutineContext().isActive) {
            if (_link.value !is Link.Connected) setLink(Link.Connecting)
            var started = false
            try {
                session(host) { started = true; backoff = 250L; authFailures = 0 }
            } catch (e: SSLException) {
                // The TV ends the handshake (or the first read under TLS 1.3) when it doesn't know our cert.
                if (!started && ++authFailures >= 2) {
                    setLink(Link.NeedsPairing)
                    return
                }
                setLink(Link.Offline(e.message ?: "TLS error"))
            } catch (e: IOException) {
                setLink(Link.Offline(e.message ?: "Can't reach the TV"))
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(4_000L)
        }
    }

    /** One connection: answer the TV's messages until the socket dies. */
    private fun session(host: String, onStarted: () -> Unit) {
        // The TV pings every 5 s, so 16 s of silence means the link is dead.
        val s = Identity.openSocket(host, PORT, readTimeoutMs = 16_000)
        socket = s
        out = s.outputStream
        try {
            val input = s.inputStream
            var announced = false
            while (true) {
                val msg = ProtoMsg(input.readFrame())
                if (!announced) { announced = true; onStarted() }
                handle(msg)
            }
        } finally {
            closeSocket(s)
        }
    }

    private fun handle(msg: ProtoMsg) {
        when {
            msg.has(1) -> { // remote_configure
                val features = OUR_FEATURES and msg.msg(1)!!.int(1).toInt()
                write(proto {
                    msg(1) {
                        int(1, features)
                        msg(2) { int(3, 1); string(4, "1"); string(5, "atvremote"); string(6, "1.0.0") }
                    }
                })
            }
            msg.has(2) -> write(proto { msg(2) { int(1, OUR_FEATURES) } }) // remote_set_active
            msg.has(8) -> write(proto { msg(9) { int(1, msg.msg(8)!!.int(1)) } }) // ping
            msg.has(20) -> { // remote_ime_key_inject: focused app + text field
                val inject = msg.msg(20)!!
                imeCounter = inject.msg(1)?.int(1)?.toInt() ?: 0
                fieldCounter = inject.msg(2)?.int(1)?.toInt() ?: 0
                haveImeCounter = imeCounter != 0
                haveFieldCounter = fieldCounter != 0
            }
            msg.has(22) -> msg.msg(22)!!.msg(2)?.let { fieldCounter = it.int(1).toInt(); haveFieldCounter = true }
            msg.has(21) -> {
                val edit = msg.msg(21)!!
                if (!haveImeCounter) imeCounter = edit.int(1).toInt()
                if (!haveFieldCounter) fieldCounter = edit.int(2).toInt()
            }
            msg.has(40) -> { // remote_start: the TV is ready for commands
                _isOn.value = msg.msg(40)!!.bool(1)
                _link.value = Link.Connected
            }
            msg.has(50) -> msg.msg(50)!!.let {
                _volume.value = Volume(it.int(7).toInt(), it.int(6).toInt(), it.bool(8))
            }
            msg.has(3) -> Log.w(TAG, "TV reported an error")
        }
    }

    private fun write(frame: ByteArray): Boolean {
        val o = out ?: return false
        return try {
            synchronized(writeLock) { o.writeFrame(frame) }
            true
        } catch (e: IOException) {
            closeSocket() // the reader notices and reconnects
            false
        }
    }

    private fun send(frame: ByteArray) {
        if (out == null) return
        scope.launch(writer) { write(frame) }
    }

    fun key(code: Int, direction: Int = SHORT) = send(proto { msg(10) { int(1, code); int(2, direction) } })

    fun text(value: String) {
        if (value.isEmpty()) return
        send(proto {
            msg(21) {
                int(1, imeCounter)
                int(2, fieldCounter)
                msg(3) { int(1, 1); msg(2) { int(1, value.length); int(2, value.length); string(3, value) } }
            }
        })
    }

    fun launch(appLink: String) = send(proto { msg(90) { string(1, appLink) } })

    /** Closes [which] (default: the current socket), clearing the fields only if they still point at it. */
    @Synchronized
    private fun closeSocket(which: SSLSocket? = socket) {
        if (which == null) return
        if (socket === which) {
            socket = null
            out = null
        }
        runCatching { which.close() }
    }

    companion object {
        private const val TAG = "TvSession"
        private const val PORT = 6466
        const val START_LONG = 1
        const val END_LONG = 2
        const val SHORT = 3

        // PING | KEY | IME | POWER | VOLUME | APP_LINK
        private const val OUR_FEATURES = 1 or 2 or 4 or 32 or 64 or 512
    }
}
