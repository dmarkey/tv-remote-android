package com.dmarkey.tvremote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ProtoTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun pairingRequestMatchesProtobufEncoding() {
        // OuterMessage{protocol_version: 2, status: 200, pairing_request{service_name: "atvremote", client_name: "x"}}
        val bytes = proto { int(1, 2); int(2, 200); msg(10) { string(1, "atvremote"); string(2, "x") } }
        assertArrayEquals(hex("08 02 10 c8 01 52 0e 0a 09 61747672656d6f7465 12 01 78"), bytes)
    }

    @Test fun keyInjectEncoding() {
        // RemoteMessage{remote_key_inject{key_code: KEYCODE_DPAD_UP (19), direction: SHORT (3)}}
        assertArrayEquals(hex("52 04 08 13 10 03"), proto { msg(10) { int(1, 19); int(2, 3) } })
    }

    @Test fun appLinkUsesTwoByteTag() {
        // field 90, wire type 2 -> tag 722 -> varint d2 05
        assertArrayEquals(hex("d2 05 03 0a 01 61"), proto { msg(90) { string(1, "a") } })
    }

    @Test fun negativeInt32IsTenByteVarint() {
        val bytes = proto { int(1, -1) }
        assertEquals(11, bytes.size)
        assertEquals(-1L, ProtoMsg(bytes).int(1))
    }

    @Test fun parsesNestedAndSkipsUnknownWireTypes() {
        // field 7 fixed32, field 8 fixed64, then remote_start{started: true}
        val bytes = hex("3d 01020304 41 0102030405060708") + proto { msg(40) { bool(1, true) } }
        val msg = ProtoMsg(bytes)
        assertTrue(msg.has(40))
        assertTrue(msg.msg(40)!!.bool(1))
    }

    @Test fun framesRoundTripAcrossSplitReads() {
        val big = ByteArray(300) { it.toByte() } // length needs a 2-byte varint
        val out = ByteArrayOutputStream()
        out.writeFrame(big)
        out.writeFrame(hex("0102"))
        val wire = out.toByteArray()
        assertArrayEquals(hex("ac 02"), wire.copyOfRange(0, 2))
        // Deliver one byte per read() to mimic TLS records splitting anywhere.
        val trickle = object : java.io.InputStream() {
            var i = 0
            override fun read() = if (i < wire.size) wire[i++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (i >= wire.size) return -1
                b[off] = wire[i++]; return 1
            }
        }
        assertArrayEquals(big, trickle.readFrame())
        assertArrayEquals(hex("0102"), trickle.readFrame())
    }
}
