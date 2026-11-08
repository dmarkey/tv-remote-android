package com.dmarkey.tvremote

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Remote : Screen
    data object Setup : Screen
    data class Pair(val tv: Tv) : Screen
}

sealed interface PairState {
    data object Connecting : PairState
    data class EnterCode(val error: String? = null) : PairState
    data object Checking : PairState
    data class Failed(val message: String) : PairState
}

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TvStore(app)
    val session = TvSession(viewModelScope)

    private val _tvs = MutableStateFlow(store.load())
    val tvs: StateFlow<List<Tv>> = _tvs
    private val _current = MutableStateFlow(_tvs.value.firstOrNull { it.host == store.lastHost } ?: _tvs.value.firstOrNull())
    val current: StateFlow<Tv?> = _current
    private val _screen = MutableStateFlow<Screen>(if (_current.value == null) Screen.Setup else Screen.Remote)
    val screen: StateFlow<Screen> = _screen
    private val _pair = MutableStateFlow<PairState>(PairState.Connecting)
    val pair: StateFlow<PairState> = _pair

    private var pairing: Pairing? = null
    private var pairJob: Job? = null
    private var idleJob: Job? = null

    private val clientName: String =
        Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME) ?: "Phone remote"

    /** Connect as soon as the app is visible; a warm connection makes the first press instant. */
    fun onForeground() {
        idleJob?.cancel()
        val tv = _current.value
        if (tv != null && _screen.value == Screen.Remote) session.connect(tv.host)
    }

    /** Keep the link for a minute in the background so a quick app switch doesn't reconnect. */
    fun onBackground() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(60_000)
            session.disconnect()
        }
    }

    fun select(tv: Tv) {
        _current.value = tv
        store.lastHost = tv.host
        _screen.value = Screen.Remote
        session.connect(tv.host)
    }

    fun addTv() {
        _screen.value = Screen.Setup
    }

    fun forget(tv: Tv) {
        val remaining = _tvs.value.filterNot { it.host == tv.host }
        _tvs.value = remaining
        store.save(remaining)
        if (_current.value?.host == tv.host) {
            session.disconnect()
            val next = remaining.firstOrNull()
            if (next != null) select(next) else {
                _current.value = null
                _screen.value = Screen.Setup
            }
        }
    }

    fun startPairing(tv: Tv) {
        closePairing()
        session.disconnect()
        _screen.value = Screen.Pair(tv)
        _pair.value = PairState.Connecting
        pairJob = viewModelScope.launch(Dispatchers.IO) {
            val p = Pairing(tv.host, clientName)
            pairing = p
            _pair.value = try {
                p.start()
                PairState.EnterCode()
            } catch (e: Exception) {
                p.close()
                PairState.Failed(e.message ?: "Couldn't reach ${tv.name}")
            }
        }
    }

    fun submitCode(tv: Tv, code: String) {
        val p = pairing ?: return
        _pair.value = PairState.Checking
        pairJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                p.finish(code)
                p.close() // not closePairing(): that would cancel this very job
                pairing = null
                withContext(Dispatchers.Main) {
                    val list = _tvs.value.filterNot { it.host == tv.host } + tv
                    _tvs.value = list
                    store.save(list)
                    select(tv)
                }
            } catch (e: WrongCodeException) {
                _pair.value = PairState.EnterCode(e.message)
            } catch (e: Exception) {
                p.close()
                pairing = null
                _pair.value = PairState.Failed(e.message ?: "Pairing failed")
            }
        }
    }

    fun cancelPairing() {
        closePairing()
        val tv = _current.value
        if (tv != null && _tvs.value.any { it.host == tv.host }) select(tv) else _screen.value = Screen.Setup
    }

    private fun closePairing() {
        pairJob?.cancel()
        pairing?.close()
        pairing = null
    }

    override fun onCleared() {
        closePairing()
        session.disconnect()
    }
}
