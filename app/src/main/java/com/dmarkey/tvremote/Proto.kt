package com.dmarkey.tvremote

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Just enough protobuf for the Android TV Remote v2 protocol (polo.proto and
 * remotemessage.proto). Writing it by hand avoids the protobuf toolchain and
 * runtime for roughly 20 small messages.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    private fun rawVarint(value: Long) {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
    }

    private fun tag(field: Int, wireType: Int) = rawVarint(((field shl 3) or wireType).toLong())

    fun int(field: Int, value: Long) = apply { tag(field, 0); rawVarint(value) }
    fun int(field: Int, value: Int) = int(field, value.toLong())
    fun bool(field: Int, value: Boolean) = int(field, if (value) 1 else 0)

    fun bytes(field: Int, value: ByteArray) = apply {
        tag(field, 2); rawVarint(value.size.toLong()); out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray())

    fun msg(field: Int, build: ProtoWriter.() -> Unit) = bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()
}

fun proto(build: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(build).toByteArray()

/** Parsed message: field number to the values seen (Long for varints, ByteArray for length-delimited). */
class ProtoMsg(bytes: ByteArray) {
    private val fields = HashMap<Int, MutableList<Any>>()

    init {
        var i = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = bytes[i++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        while (i < bytes.size) {
            val key = varint()
            val field = (key ushr 3).toInt()
            val value: Any = when ((key and 7).toInt()) {
                0 -> varint()
                1 -> { i += 8; continue }
                2 -> {
                    val len = varint().toInt()
                    bytes.copyOfRange(i, i + len).also { i += len }
                }
                5 -> { i += 4; continue }
                else -> throw IllegalArgumentException("Bad wire type in field $field")
            }
            fields.getOrPut(field) { mutableListOf() }.add(value)
        }
    }

    fun has(field: Int) = fields.containsKey(field)
    fun int(field: Int): Long = fields[field]?.firstOrNull() as? Long ?: 0L
    fun bool(field: Int): Boolean = int(field) != 0L
    fun str(field: Int): String = (fields[field]?.firstOrNull() as? ByteArray)?.decodeToString() ?: ""
    fun msg(field: Int): ProtoMsg? = (fields[field]?.firstOrNull() as? ByteArray)?.let { ProtoMsg(it) }
}

/** Messages on the wire are prefixed with their length as a varint. */
fun OutputStream.writeFrame(body: ByteArray) {
    val buf = ByteArrayOutputStream(body.size + 5)
    var v = body.size
    while (v and 0x7F.inv() != 0) {
        buf.write((v and 0x7F) or 0x80)
        v = v ushr 7
    }
    buf.write(v)
    buf.write(body)
    write(buf.toByteArray()) // one write, so the frame goes out in a single TLS record
    flush()
}

fun InputStream.readFrame(): ByteArray {
    var len = 0
    var shift = 0
    while (true) {
        val b = read()
        if (b < 0) throw EOFException("Connection closed")
        len = len or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) break
        shift += 7
    }
    val body = ByteArray(len)
    var off = 0
    while (off < len) {
        val n = read(body, off, len - off)
        if (n < 0) throw EOFException("Connection closed")
        off += n
    }
    return body
}
