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

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

/**
 * 密钥来源抽象。
 *
 * 生产默认使用 [AndroidKeystoreKeySource]（基于 Android Keystore 的单个 AES-256-GCM 主密钥，
 * 无法被导出的硬件/系统级密钥）。
 * 单元测试注入 [SecureKeySource] 的标准 AES 密钥，从而在无 Android Keystore Provider 的
 * JVM/Robolectric 环境下，依然能确定性地验证 AES-GCM + AAD 的往返、防伪与账号绑定逻辑。
 */
interface SecureKeySource {
    /** 返回指定 alias 的密钥；若不存在则创建。 */
    fun getOrCreate(alias: String): SecretKey

    /** 判断某 alias 是否已存在密钥。 */
    fun contains(alias: String): Boolean
}

/** 生产实现：Android Keystore，AES-256-GCM，密钥不可导出。 */
class AndroidKeystoreKeySource : SecureKeySource {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun getOrCreate(alias: String): SecretKey {
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

    override fun contains(alias: String): Boolean = keyStore.containsAlias(alias)
}

/**
 * 基于 AES-256-GCM 的加密存储。
 *
 * 加密方案：使用单个 AES-256-GCM 主密钥 [alias]，随机 12 字节 IV，
 * 将 [objectName] 作为 AAD（附加认证数据）参与认证标签计算。
 * 密文以 Base64(version + aliasLen + alias + ivLen + iv + ct) 持久化。
 *
 * 使用同一 [objectName] 作为 AAD 保证：不同账号（objectName）加密的密文，
 * 无法被复制到其他账号解密 —— 保证账号间密文不可互换。
 * （本实现使用单一主密钥，账号绑定由 AAD 而非密钥区分完成。）
 */
class SecureCipherStore(
    private val alias: String = "ai_quota_master_key",
    private val logTag: String = "SecureCipherStore",
    private val keySource: SecureKeySource = AndroidKeystoreKeySource()
) {

    private val masterKey: SecretKey by lazy { keySource.getOrCreate(alias) }

    /**
     * 加密明文，返回可安全存储在普通数据库/文件中的字符串。
     * @param plaintext 待加密明文
     * @param objectName 作为 AAD 的唯一命名空间（通常为账号 id），用于绑定密文归属
     */
    fun encrypt(plaintext: String, objectName: String): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        cipher.updateAAD(objectName.toByteArray(Charsets.UTF_8))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val blob = newBlob(alias, iv, ct)
        return Base64Helpers.encode(blob)
    }

    fun decrypt(ciphertextB64: String, objectName: String): String? {
        return try {
            val blob = Base64Helpers.decode(ciphertextB64) ?: return null
            val (keyAlias, iv, ct) = parseBlob(blob)
            val key = keySource.getOrCreate(keyAlias)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.updateAAD(objectName.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(logTag, "decrypt failed", e)
            null
        }
    }

    fun containsAlias(alias: String): Boolean = keySource.contains(alias)
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