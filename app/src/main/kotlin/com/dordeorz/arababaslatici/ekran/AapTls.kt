package com.dordeorz.arababaslatici.ekran

import android.content.Context
import android.util.Base64
import com.dordeorz.arababaslatici.R
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLException
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * Ana birim (bizim uygulama) tarafının TLS'i. AAP'de ana birim TLS **istemcisi**dir; Android Auto
 * sunucu. TLS kayıtları AAP çerçevelerinin içinde taşınır, bu yüzden soket yerine SSLEngine.
 *
 * İstemci sertifikası `res/raw/aap_sertifika` + `aap_anahtar`: Google'ın referans ana birim
 * sertifikası (O=Google-Android-Reference), açık kaynak ana birim projelerinin kullandığı,
 * Open Headunit'ten alındı. Sunucu sertifikası doğrulanmaz (Android Auto kendi imzalı).
 */
class AapTls(context: Context) {
    private val motor: SSLEngine
    private var gelen = ByteBuffer.allocate(0)
    private val duzTampon: ByteBuffer
    private val agTampon: ByteBuffer

    init {
        val sertifika = context.resources.openRawResource(R.raw.aap_sertifika).use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        val anahtarMetni = context.resources.openRawResource(R.raw.aap_anahtar)
            .bufferedReader().use { it.readText() }
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val anahtar = KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(anahtarMetni, Base64.DEFAULT)))

        val baglam = SSLContext.getInstance("TLS")
        baglam.init(arrayOf(TekAnahtar(sertifika, anahtar)), arrayOf(HepsineGuven), SecureRandom())
        motor = baglam.createSSLEngine().apply { useClientMode = true }
        duzTampon = ByteBuffer.allocate(motor.session.applicationBufferSize.coerceAtLeast(256 * 1024))
        agTampon = ByteBuffer.allocate(motor.session.packetBufferSize.coerceAtLeast(64 * 1024))
    }

    /**
     * El sıkışmasını yürütür. [gonder] şifresiz SSL_EL_SIKISMA çerçevesi yollar, [al] bir sonraki
     * SSL_EL_SIKISMA çerçevesinin TLS baytlarını döndürür.
     */
    fun elSikis(gonder: (ByteArray) -> Unit, al: () -> ByteArray) {
        motor.beginHandshake()
        while (true) {
            when (motor.handshakeStatus) {
                HandshakeStatus.NEED_WRAP -> {
                    agTampon.clear()
                    val s = motor.wrap(ByteBuffer.allocate(0), agTampon)
                    gorevleriCalistir(s)
                    if (s.status != SSLEngineResult.Status.OK) throw SSLException("wrap: ${s.status}")
                    agTampon.flip()
                    if (agTampon.hasRemaining()) gonder(ByteArray(agTampon.remaining()).also { agTampon.get(it) })
                }
                HandshakeStatus.NEED_UNWRAP -> {
                    if (!gelen.hasRemaining()) gelen = ByteBuffer.wrap(al())
                    duzTampon.clear()
                    val s = motor.unwrap(gelen, duzTampon)
                    gorevleriCalistir(s)
                    when (s.status) {
                        SSLEngineResult.Status.OK -> Unit
                        SSLEngineResult.Status.BUFFER_UNDERFLOW -> gelen = ekle(gelen, al())
                        else -> throw SSLException("unwrap: ${s.status}")
                    }
                }
                HandshakeStatus.NEED_TASK -> gorevleriCalistir(null)
                HandshakeStatus.FINISHED, HandshakeStatus.NOT_HANDSHAKING -> return
                else -> throw SSLException("Beklenmeyen el sıkışma durumu ${motor.handshakeStatus}")
            }
        }
    }

    /** Düz metni TLS kayıt(lar)ına çevirir. */
    @Synchronized
    fun sifrele(duz: ByteArray): ByteArray {
        val kaynak = ByteBuffer.wrap(duz)
        val cikti = ByteArrayOutputStream()
        while (kaynak.hasRemaining()) {
            agTampon.clear()
            val s = motor.wrap(kaynak, agTampon)
            if (s.status != SSLEngineResult.Status.OK) throw SSLException("wrap: ${s.status}")
            cikti.write(agTampon.array(), 0, agTampon.position())
        }
        return cikti.toByteArray()
    }

    /** Bir çerçevenin TLS baytlarını çözer; çerçeve tam kayıt(lar) taşır. */
    fun coz(tls: ByteArray): ByteArray {
        val kaynak = ByteBuffer.wrap(tls)
        val cikti = ByteArrayOutputStream()
        while (kaynak.hasRemaining()) {
            duzTampon.clear()
            val s = motor.unwrap(kaynak, duzTampon)
            if (s.status != SSLEngineResult.Status.OK) throw SSLException("unwrap: ${s.status}")
            cikti.write(duzTampon.array(), 0, duzTampon.position())
        }
        return cikti.toByteArray()
    }

    val protokol: String get() = motor.session.protocol

    private fun gorevleriCalistir(s: SSLEngineResult?) {
        if (s == null || s.handshakeStatus == HandshakeStatus.NEED_TASK) {
            while (true) (motor.delegatedTask ?: break).run()
        }
    }

    private fun ekle(eski: ByteBuffer, yeni: ByteArray): ByteBuffer {
        val b = ByteBuffer.allocate(eski.remaining() + yeni.size)
        b.put(eski).put(yeni).flip()
        return b
    }

    /** Hep aynı sertifikayı sunar; sunucunun istediği veren listesine bakmaz. */
    private class TekAnahtar(
        private val sertifika: X509Certificate,
        private val anahtar: PrivateKey,
    ) : X509ExtendedKeyManager() {
        override fun getClientAliases(t: String?, i: Array<out Principal>?) = arrayOf(AD)
        override fun chooseClientAlias(t: Array<out String>?, i: Array<out Principal>?, s: Socket?) = AD
        override fun chooseEngineClientAlias(t: Array<out String>?, i: Array<out Principal>?, e: SSLEngine?) = AD
        override fun getServerAliases(t: String?, i: Array<out Principal>?) = arrayOf(AD)
        override fun chooseServerAlias(t: String?, i: Array<out Principal>?, s: Socket?) = AD
        override fun chooseEngineServerAlias(t: String?, i: Array<out Principal>?, e: SSLEngine?) = AD
        override fun getCertificateChain(a: String?) = arrayOf(sertifika)
        override fun getPrivateKey(a: String?) = anahtar

        companion object { const val AD = "aap" }
    }

    @Suppress("CustomX509TrustManager", "TrustAllX509TrustManager")
    private object HepsineGuven : X509TrustManager {
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
    }
}
