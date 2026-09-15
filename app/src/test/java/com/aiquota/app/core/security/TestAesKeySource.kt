package com.aiquota.app.core.security

import javax.crypto.SecretKey

/**
 * 共享的标准 JCA AES-256 密钥来源。
 *
 * 用于 JVM 级测试 SecureCipherStore / RoomCredentialStore 的加解密逻辑，
 * 避免依赖 Robolectric 环境下不稳定的 AndroidKeyStore JCA Provider。
 */
object TestAesKeySource : SecureKeySource {
    private val key: SecretKey =
        javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun getOrCreate(alias: String): SecretKey = key
    override fun contains(alias: String): Boolean = true
}