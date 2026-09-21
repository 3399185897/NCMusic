package com.buddy.ncmusic.core.network.crypto

import android.util.Base64
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 weapi 加密（AES-128-CBC 双层 + RSA 加密随机密钥）。
 *
 * 客户端内置该逻辑后即可直连 music.163.com，无需任何中间服务器。
 * 算法与官方 Web 端保持一致：明文 JSON 先用固定密钥 AES 加密一次，
 * 再用随机 16 位密钥加密一次，随机密钥用 RSA 公钥加密后作为 encSecKey 提交。
 */
object WeapiCrypto {

    private const val PRESET_KEY = "0CoJUm6Qyw8W8jud"
    private const val IV = "0102030405060708"
    private const val PUB_EXP = "010001"
    private const val MODULUS =
        "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7"
    private const val SECRET_CHARS = "0123456789abcdef"

    private val random = SecureRandom()
    private val modulus = BigInteger(MODULUS, 16)
    private val exponent = BigInteger(PUB_EXP, 16)

    private fun aesEncrypt(text: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(IV.toByteArray(Charsets.UTF_8)),
        )
        return Base64.encodeToString(cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun rsaEncrypt(secretKey: String): String {
        val reversed = secretKey.reversed()
        val hex = reversed.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
        return BigInteger(hex, 16).modPow(exponent, modulus).toString(16).padStart(256, '0')
    }

    /** 加密请求体，返回 params 与 encSecKey */
    fun encrypt(jsonBody: String): Pair<String, String> {
        val secretKey = buildString(16) {
            repeat(16) { append(SECRET_CHARS[random.nextInt(SECRET_CHARS.length)]) }
        }
        val params = aesEncrypt(aesEncrypt(jsonBody, PRESET_KEY), secretKey)
        return params to rsaEncrypt(secretKey)
    }
}
