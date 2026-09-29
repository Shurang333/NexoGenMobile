package com.example.nexogen

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

/**
 * Простая крипто-обёртка:
 * - ECDH на X25519 (или P-256, если 25519 недоступно)
 * - HKDF-SHA256 для получения ключа
 * - AES-256-GCM для шифрования сообщений
 */
object Crypto {

    private const val TAG = "Crypto"

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val AES_KEY_LENGTH_BYTES = 32

    private val secureRandom = SecureRandom()

    /**
     * Генерирует ECDH keypair (X25519 если доступно, иначе P-256).
     */
    fun generateKeyPair(): KeyPair {
        return try {
            val kpg = KeyPairGenerator.getInstance("X25519")
            kpg.generateKeyPair()
        } catch (e: Exception) {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(256)
            kpg.generateKeyPair()
        }
    }

    fun encodePublicKey(key: PublicKey): String {
        return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
    }

    fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return try {
            val kf = KeyFactory.getInstance("X25519")
            kf.generatePublic(X509EncodedKeySpec(bytes))
        } catch (e: Exception) {
            val kf = KeyFactory.getInstance("EC")
            kf.generatePublic(X509EncodedKeySpec(bytes))
        }
    }

    /**
     * Из своего приватного и чужого публичного ключа получает общий секрет.
     */
    fun deriveSharedSecret(myPrivate: PrivateKey, theirPublic: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance(
            try { "X25519" } catch (e: Exception) { "ECDH" }
        )
        ka.init(myPrivate)
        ka.doPhase(theirPublic, true)
        return ka.generateSecret()
    }

    /**
     * HKDF-SHA256: из сырого shared secret получает AES-ключ длиной 32 байта.
     */
    fun deriveAesKey(sharedSecret: ByteArray, info: String): SecretKey {
        val salt = ByteArray(32) // нулевой salt
        val infoBytes = info.toByteArray(Charsets.UTF_8)

        // Extract
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(sharedSecret)

        // Expand — нам нужно 32 байта, один блок
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        mac.update(infoBytes)
        mac.update(0x01)
        val okm = mac.doFinal()

        return SecretKeySpec(okm.copyOf(AES_KEY_LENGTH_BYTES), "AES")
    }

    /**
     * Шифрует данные AES-GCM. Возвращает <IV(12)><ciphertext><tag(16)>.
     */
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ct = cipher.doFinal(plaintext)

        // IV + ciphertext(с тегом внутри)
        return iv + ct
    }

    /**
     * Расшифровывает <IV(12)><ciphertext><tag(16)>.
     */
    fun decrypt(key: SecretKey, data: ByteArray): ByteArray {
        if (data.size < GCM_IV_LENGTH + 16) {
            throw IllegalArgumentException("ciphertext too short")
        }

        val iv = data.copyOfRange(0, GCM_IV_LENGTH)
        val ct = data.copyOfRange(GCM_IV_LENGTH, data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ct)
    }
}