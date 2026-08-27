package com.bdalxy.edencourt.data.setting

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 加密组件（T12.1，S1 安全红线）。
 *
 * 核心思路：敏感字段（apiKey）不解密进 Preferences DataStore，而是用 **Android Keystore**
 * 派生的 AES/GCM 密钥加密后再写入 DataStore——Keystore 中的密钥由系统硬件级保护、
 * 不可导出，即使他人拿到备份文件 / root 读取 DataStore，也得不到明文 Key。
 *
 * 职责：
 * - 首次调用按 alias 生成 AES-256/GCM 密钥（存 AndroidKeyStore，进程可复用）
 * - [encrypt] 返回「IV(12B) + 密文」Base64 串（GCM 默认 12 字节 IV，前置便于解密时还原）
 * - [decrypt] 反向还原；对空串 / 密钥缺失 / 解密失败返回空串（不强抛，保证不崩主流程）
 *
 * 说明：不引入 androidx.security-crypto（其 EncryptedSharedPreferences 已标记 deprecated，
 * 官方推荐自实现 Keystore 方案）；本项目 minSdk=29>=23，可直接用 KeyGenParameterSpec。
 */
class ApiKeyCipher {

    /** Keystore 别名：进程内单实例，首次生成后复用 */
    private var key: SecretKey? = null

    /** AES 加解密的 IV 长度（GCM 推荐 12 字节） */
    private val IV_LENGTH_BYTES = 12

    /** 密钥别名 */
    private val alias = "edencourt_api_key"

    /** Keystore 类型（系统推荐，兼容所有 API 29+ 设备） */
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    /** 获取或创建 Keystore AES 密钥（懒生成，只建一次） */
    private fun getOrCreateKey(): SecretKey {
        key?.let { return it }
        (keyStore.getKey(alias, null) as? SecretKey)?.let {
            key = it
            return it
        }
        // 不存在则用 KeyGenParameterSpec 生成 AES-256/GCM 密钥（不可导出、需用户解锁）
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        val created = generator.generateKey()
        key = created
        return created
    }

    /**
     * 加密明文（非空时返回 Base64「IV+密文」）。
     * 空串直接返回空串（空 Key 不需要加密）。
     */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val en = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val cipherText = en.doFinal(plain.toByteArray(Charsets.UTF_8))
        return encryptedBase64(en.iv, cipherText)
    }

    private fun encryptedBase64(iv: ByteArray, cipherText: ByteArray): String {
        val merged = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, merged, 0, iv.size)
        System.arraycopy(cipherText, 0, merged, iv.size, cipherText.size)
        return Base64.encodeToString(merged, Base64.NO_WRAP)
    }

    /**
     * 解密 Base64「IV+密文」为明文。
     * 空串直接返空；Key 缺失 / 密文损坏 / 解密失败记 warn 并返空（保证不阻塞主流程）。
     */
    fun decrypt(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return try {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = data.copyOfRange(0, IV_LENGTH_BYTES)
            val ct = data.copyOfRange(IV_LENGTH_BYTES, data.size)
            val secret = getOrCreateKey()
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
                String(doFinal(ct), Charsets.UTF_8)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "解密 API Key 失败（可能 Keystore 被清除），返回空串", e)
            ""
        }
    }

    private companion object {
        const val TAG = "ApiKeyCipher"
    }
}