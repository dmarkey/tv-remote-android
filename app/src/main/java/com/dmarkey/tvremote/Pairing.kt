package com.dmarkey.tvremote

import java.io.Closeable
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(message: String) : IOException(message)

/** The code doesn't match this pairing session. The TV keeps showing it, so the user can retype. */
class WrongCodeException : IOException("That code doesn't match the one on the TV")

/**
 * Polo pairing on port 6467. [start] runs the handshake until the TV shows a
 * 6-character hex code; [finish] proves we've seen it. Both block, so call them off the main thread.
 */
class Pairing(private val host: String, private val clientName: String) : Closeable {
    private var socket: SSLSocket? = null

    private fun send(body: ProtoWriter.() -> Unit) {
        socket!!.outputStream.writeFrame(proto { int(1, 2); int(2, 200); body() })
    }

    private fun receive(): ProtoMsg {
        val msg = ProtoMsg(socket!!.inputStream.readFrame())
        when (val status = msg.int(2).toInt()) {
            200 -> return msg
            402 -> throw PairingException("The TV rejected the code. Try again.")
            else -> throw PairingException("The TV refused to pair (status $status)")
        }
    }

    fun start() {
        socket = Identity.openSocket(host, PORT, readTimeoutMs = 10_000)
        send { msg(10) { string(1, "atvremote"); string(2, clientName) } }
        while (true) {
            val reply = receive()
            when {
                // pairing_request_ack -> offer a 6-symbol hex code, we type it in
                reply.has(11) -> send { msg(20) { msg(1) { int(1, HEX); int(2, 6) }; int(3, ROLE_INPUT) } }
                // options -> confirm the configuration
                reply.has(20) -> send { msg(30) { msg(1) { int(1, HEX); int(2, 6) }; int(2, ROLE_INPUT) } }
                // configuration_ack -> the code is on the TV screen now
                reply.has(31) -> return
                else -> throw PairingException("Unexpected reply from the TV")
            }
        }
    }

    fun finish(code: String) {
        val clean = code.trim().uppercase()
        if (clean.length != 6 || clean.any { it !in HEX_CHARS }) throw WrongCodeException()
        val s = socket ?: throw PairingException("Not connected")

        val client = Identity.certificate.publicKey as RSAPublicKey
        val server = s.session.peerCertificates[0].publicKey as RSAPublicKey
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(client.modulus.unsigned())
            update(client.publicExponent.unsigned())
            update(server.modulus.unsigned())
            update(server.publicExponent.unsigned())
            update(clean.substring(2).hexToBytes())
            digest()
        }
        // The first byte of the code is a checksum: catches typos without a round trip.
        if (digest[0] != clean.substring(0, 2).toInt(16).toByte()) throw WrongCodeException()

        send { msg(40) { bytes(1, digest) } }
        if (!receive().has(41)) throw PairingException("The TV didn't accept the code")
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        const val PORT = 6467
        const val HEX = 3
        const val ROLE_INPUT = 1
        const val HEX_CHARS = "0123456789ABCDEF"

        /** Big-endian magnitude without BigInteger's sign byte. */
        fun BigInteger.unsigned(): ByteArray {
            val b = toByteArray()
            return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
        }

        fun String.hexToBytes() = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
