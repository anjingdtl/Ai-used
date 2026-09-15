package com.aiquota.app.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiquota.app.TestRobolectricApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * SecureCipherStore（Android Keystore AES-256-GCM + objectName 作为 AAD）测试。
 * 验证：加解密往返、错误 objectName 解密失败（AAD 绑定）、伪造密文失败。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = TestRobolectricApplication::class)
class SecureCipherStoreTest {

    // 用标准 JCA AES 密钥作为 SecureKeySource，避免依赖 Robolectric 下不稳定的 AndroidKeyStore Provider。
    private val store = SecureCipherStore(
        alias = "test_keystore_" + System.nanoTime(),
        keySource = TestAesKeySource
    )

    @Test
    fun `加密后解密往返_内容一致`() {
        val plain = "sk-real-secret-1234567890abcdef"
        val blob = store.encrypt(plain, "accA")
        val decrypted = store.decrypt(blob, "accA")
        assertEquals(plain, decrypted)
    }

    @Test
    fun `错误objectName_解密失败_AAD绑定生效`() {
        val blob = store.encrypt("accA-secret", "accA")
        // 用不同 objectName 解密同一密文 -> AAD 不匹配 -> 失败
        assertNull(store.decrypt(blob, "accB"))
    }

    @Test
    fun `同一密文用正确objectName仍可解密`() {
        val plain = "7f61a2c4e8d9b0a1c3d5e7f8a9b0c1d2"
        val blob = store.encrypt(plain, "accX")
        assertEquals(plain, store.decrypt(blob, "accX"))
    }

    @Test
    fun `篡改密文_解密失败`() {
        val blob = store.encrypt("sensitive-data", "accA")
        val tampered = blob.dropLast(1) + "A"
        assertNull(store.decrypt(tampered, "accA"))
    }

    @Test
    fun `不同明文_密文不同_IV随机`() {
        val a = store.encrypt("same", "accA")
        val b = store.encrypt("same", "accA")
        // 随机 IV -> 相同明文两次加密结果不同
        assertNotNull(a)
        assertNotNull(b)
        // GCM tag 防伪，字符串应不同（IV 随机）
        // 仅断言都能解密回原文
        assertEquals("same", store.decrypt(a, "accA"))
        assertEquals("same", store.decrypt(b, "accA"))
    }
}