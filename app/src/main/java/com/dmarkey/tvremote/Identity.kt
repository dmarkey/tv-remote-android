package com.dmarkey.tvremote

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

/**
 * The client certificate the TV knows us by. The RSA key is generated inside the
 * phone's Android Keystore and never leaves it; one identity serves every TV.
 */
object Identity {
    private const val ALIAS = "tv_remote_client_v2"
    private const val OLD_ALIAS = "tv_remote_client" // v1 lacked raw-RSA padding, unusable for TLS 1.3

    private val keyStore: KeyStore by lazy { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } }

    @Synchronized
    private fun ensureKey() {
        if (keyStore.containsAlias(ALIAS)) return
        if (keyStore.containsAlias(OLD_ALIAS)) keyStore.deleteEntry(OLD_ALIAS)
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(2048)
            .setDigests(
                KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512,
            )
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            // Conscrypt signs TLS handshakes through Cipher upcalls: TLS 1.2 uses
            // RSA/ECB/PKCS1Padding, while TLS 1.3 pads for PSS itself and then needs a raw
            // RSA/ECB/NoPadding private-key operation. Both paddings must be authorised.
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE, KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
            .setRandomizedEncryptionRequired(false)
            .setCertificateSubject(X500Principal("CN=atvremote"))
            .setCertificateSerialNumber(BigInteger.valueOf(1000))
            .setCertificateNotBefore(Date(now - 86_400_000L))
            .setCertificateNotAfter(Date(now + 20L * 365 * 86_400_000L))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply {
            initialize(spec)
            generateKeyPair()
        }
    }

    val certificate: X509Certificate
        get() { ensureKey(); return keyStore.getCertificate(ALIAS) as X509Certificate }

    private val privateKey: PrivateKey
        get() { ensureKey(); return keyStore.getKey(ALIAS, null) as PrivateKey }

    private val keyManager = object : X509ExtendedKeyManager() {
        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
        override fun getCertificateChain(alias: String?) = arrayOf(certificate)
        override fun getPrivateKey(alias: String?) = privateKey
        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = null
        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = null
    }

    // TVs present self-signed certificates; the protocol's trust comes from the pairing code.
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
    }

    private val sslContext: SSLContext by lazy {
        ensureKey()
        SSLContext.getInstance("TLS").apply { init(arrayOf(keyManager), arrayOf(trustAll), null) }
    }

    fun openSocket(host: String, port: Int, readTimeoutMs: Int): SSLSocket {
        val raw = Socket()
        raw.tcpNoDelay = true
        raw.connect(java.net.InetSocketAddress(host, port), 3000)
        raw.soTimeout = readTimeoutMs
        val ssl = sslContext.socketFactory.createSocket(raw, host, port, true) as SSLSocket
        ssl.startHandshake()
        return ssl
    }
}
