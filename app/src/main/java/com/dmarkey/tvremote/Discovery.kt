package com.dmarkey.tvremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.util.concurrent.Executor

data class FoundTv(val name: String, val host: String)

/** Streams the Android TVs advertising the remote service on the local network (mDNS). */
fun discoverTvs(context: Context): Flow<List<FoundTv>> = callbackFlow {
    val nsd = context.getSystemService(NsdManager::class.java)
    // Callbacks run inline on NsdManager's own thread. A private executor can't be used:
    // NsdManager still delivers onServiceInfoCallbackUnregistered to it after awaitClose,
    // and a shut-down executor rejects that and crashes the app.
    val executor = Executor { it.run() }
    val found = LinkedHashMap<String, FoundTv>()
    val resolvers = mutableListOf<NsdManager.ServiceInfoCallback>()

    fun resolve(service: NsdServiceInfo) {
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) {
                val address = info.hostAddresses.firstOrNull { it is Inet4Address } ?: info.hostAddresses.firstOrNull()
                    ?: return
                val host = address.hostAddress ?: return
                synchronized(found) { found[info.serviceName] = FoundTv(info.serviceName, host); trySend(found.values.toList()) }
            }
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}
            override fun onServiceLost() {
                synchronized(found) { found.remove(service.serviceName); trySend(found.values.toList()) }
            }
            override fun onServiceInfoCallbackUnregistered() {}
        }
        runCatching { nsd.registerServiceInfoCallback(service, executor, callback) }
            .onSuccess { synchronized(resolvers) { resolvers += callback } }
    }

    val listener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) = resolve(service)
        override fun onServiceLost(service: NsdServiceInfo) {
            synchronized(found) { found.remove(service.serviceName); trySend(found.values.toList()) }
        }
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

    trySend(emptyList())
    nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, listener)

    awaitClose {
        runCatching { nsd.stopServiceDiscovery(listener) }
        synchronized(resolvers) { resolvers.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } } }
    }
}
