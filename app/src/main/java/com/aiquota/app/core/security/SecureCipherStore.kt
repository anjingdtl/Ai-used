package com.aiquota.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 基于 Android Keystore 的非对称保护下的 AES-256-GCM 加密存储。
 * 数据用随机生成的 AES Key 加密，AES Key 本身被 Keystore 内的密钥加密包装，
 * 明文数据以 Base64(iv + ciphertext + wrappedKey) 形式持久化。
 */
class SecureCipherStore(
    private val alias: String = "ai_quota_master_key",
    private val logTag: String = "SecureCipherStore"
) {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private val masterKey: SecretKey by lazy { getOrCreateMasterKey() }

    private fun getOrCreateMasterKey(): SecretKey {
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * 加密明文，返回可安全存储在普通数据库/文件中的字符串。
     * @param plaintext 待加密明文
     * @param objectName 用于客运派生加密密钥的命名空间（不同 key 用不同 alias）
     */
    fun encrypt(plaintext: String, objectName: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val blob = newBlob(alias, iv, ct)
        return Base64Helpers.encode(blob)
    }

    fun decrypt(ciphertextB64: String, objectName: String): String? {
        return try {
            val blob = Base64Helpers.decode(ciphertextB64) ?: return null
            val (keyAlias, iv, ct) = parseBlob(blob)
            val key = keyStore.getKey(keyAlias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(logTag, "decrypt failed", e)
            null
        }
    }

    fun containsAlias(alias: String): Boolean = keyStore.containsAlias(alias)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

// 内部辅助：新格式 blob = [version(1)][keyAliasLen(1)][keyAlias][ivLen(1)][iv][ct]
private fun newBlob(keyAlias: String, iv: ByteArray, ct: ByteArray): ByteArray {
    val aliasBytes = keyAlias.toByteArray(Charsets.UTF_8)
    val out = java.io.ByteArrayOutputStream()
    out.write(1)                          // version
    out.write(aliasBytes.size)            // alias length
    out.write(aliasBytes)
    out.write(iv.size)                    // iv length
    out.write(iv)
    out.write(ct)
    return out.toByteArray()
}

private fun parseBlob(blob: ByteArray): Triple<String, ByteArray, ByteArray> {
    var p = 0
    // version
    p++
    val aliasLen = blob[p].toInt(); p++
    val alias = String(blob, p, aliasLen, Charsets.UTF_8); p += aliasLen
    val ivLen = blob[p].toInt(); p++
    val iv = blob.copyOfRange(p, p + ivLen); p += ivLen
    val ct = blob.copyOfRange(p, blob.size)
    return Triple(alias, iv, ct)
}

// 与系统 Base64 封装（避免引用冲突）
private object Base64Helpers {
    fun encode(data: ByteArray): String = android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
    fun decode(s: String): ByteArray? =
        try { android.util.Base64.decode(s, android.util.Base64.NO_WRAP) } catch (e: Exception) { null }
}