package com.spiderpulse.osint.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "spiderpulse_master_aes_gcm_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
        val existingEntry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingEntry != null) {
            return existingEntry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptToBytes(plainText: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(1 + iv.size + cipherBytes.size)
        buffer.put(iv.size.toByte())
        buffer.put(iv)
        buffer.put(cipherBytes)
        return buffer.array()
    }

    fun decryptFromBytes(encryptedPayload: ByteArray): String {
        require(encryptedPayload.size > 13) { "Invalid encrypted payload length" }
        val buffer = ByteBuffer.wrap(encryptedPayload)
        val ivLength = buffer.get().toInt() and 0xFF
        require(ivLength in 12..16 && encryptedPayload.size > 1 + ivLength) {
            "Corrupted IV header in encrypted storage"
        }
        val iv = ByteArray(ivLength)
        buffer.get(iv)
        val cipherBytes = ByteArray(buffer.remaining())
        buffer.get(cipherBytes)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }

    @Synchronized
    fun rotateMasterKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
        }
    }

    /**
     * Masks sensitive API keys or tokens in URLs, error strings, or logs.
     */
    fun redactSecret(text: String, secret: String): String {
        val cleanSecret = secret.trim()
        if (cleanSecret.length < 3 || text.isEmpty()) return text
        var result = text.replace(cleanSecret, "[REDACTED]")
        val urlEncoded = runCatching {
            java.net.URLEncoder.encode(cleanSecret, "UTF-8")
        }.getOrNull()
        if (!urlEncoded.isNullOrBlank() && urlEncoded != cleanSecret) {
            result = result.replace(urlEncoded, "[REDACTED]")
        }
        return result
    }

    /**
     * SSRF Protection: Determines whether an IP address belongs to a private, loopback,
     * link-local, carrier-grade NAT, or reserved range that external API modules must not target.
     */
    fun isRestrictedInternalAddress(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress ||
            addr.isLoopbackAddress ||
            addr.isLinkLocalAddress ||
            addr.isSiteLocalAddress ||
            addr.isMulticastAddress
        ) {
            return true
        }

        if (addr is Inet4Address) {
            val b = addr.address
            val b0 = b[0].toInt() and 0xFF
            val b1 = b[1].toInt() and 0xFF
            // 100.64.0.0/10 (CGNAT RFC 6598)
            if (b0 == 100 && b1 in 64..127) return true
            // 169.254.0.0/16 (Cloud metadata / link-local)
            if (b0 == 169 && b1 == 254) return true
            // 127.0.0.0/8
            if (b0 == 127) return true
            // 0.0.0.0/8
            if (b0 == 0) return true
        } else if (addr is Inet6Address) {
            val b = addr.address
            val firstByte = b[0].toInt() and 0xFF
            // Unique Local Address fc00::/7 (fc00..fdff)
            if (firstByte == 0xFC || firstByte == 0xFD) return true
        }
        return false
    }
}
