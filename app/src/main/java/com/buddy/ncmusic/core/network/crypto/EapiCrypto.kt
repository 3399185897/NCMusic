package com.buddy.ncmusic.core.network.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 网易云 **eapi** 加密（AES-128-ECB + MD5 摘要）。
 *
 * ## 为什么需要它
 *
 * 项目原先只有 weapi（AES-CBC 双层 + RSA），走 `music.163.com/weapi/...`。
 * 但网易云的**高解析音质（Hi-Res / 超清母带 / 空间音频）只在 eapi 通道开放** ——
 * eapi 对应移动端客户端，weapi 对应 Web 端。Web 端能力被裁剪，
 * 即便账号是黑胶 VIP，`hires` 请求也会返回空地址，
 * 客户端只能降级并提示"无权限"。
 *
 * 因此 `song/enhance/player/url/v1` 这类音质接口必须走
 * `interface.music.163.com/eapi/...`。
 *
 * ## 算法
 *
 * ```
 * text    = 请求体 JSON
 * message = "nobody" + apiPath + "use" + text + "md5forencrypt"
 * digest  = md5(message)                                     // 32 位小写
 * data    = apiPath + "-36cd479b6b5-" + text + "-36cd479b6b5-" + digest
 * params  = hex(AES-128-ECB-PKCS7(data, key))                // 大写
 * ```
 *
 * 其中 `apiPath` 是 **`/api/...`** 形式的路径（不是 `/eapi/...`）——
 * 摘要与拼接都用它，只有实际请求地址才换成 `/eapi/...`。
 * 这是最容易写错的一点：两处路径不一致会导致服务端返回
 * "参数错误"或直接 400。
 */
object EapiCrypto {

    /** 官方移动端固定密钥 */
    private const val KEY = "e82ckenh8dichen8"

    /**
     * 路径与正文之间的固定分隔串（`-36cd479b6b5-`）。
     *
     * 注意区分：另有算法使用 `36cdhje8iv4j4apw9` 作为分隔，
     * 两者长度与用途都不同，混淆会导致服务端直接判参数错误。
     */
    private const val SEPARATOR = "-36cd479b6b5-"

    /**
     * 生成 eapi 的 `params` 参数值。
     *
     * @param apiPath `/api/...` 形式的接口路径
     * @param jsonBody 请求体 JSON 文本
     * @return 大写十六进制密文
     */
    fun encrypt(apiPath: String, jsonBody: String): String {
        val digest = md5Hex("nobody$apiPath" + "use" + jsonBody + "md5forencrypt")
        // ⚠️ 两处分隔符**都必须是** SEPARATOR（`-36cd479b6b5-`）。
        //
        // 这里曾经只在第一处用 SEPARATOR、第二处写成裸 `"-"`，
        // 得到 `path-36cd479b6b5-json-digest` —— 服务端能解开 AES，
        // 但按 `path-SEP-json-SEP-digest` 的结构取不到摘要，
        // 于是**静默返回 HTTP 200 + 空 body**（Content-Type 仍是 application/json）。
        // 客户端只看到"解析失败"，完全查不到原因。
        val plain = apiPath + SEPARATOR + jsonBody + SEPARATOR + digest
        return aesEcbHexUpper(plain)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun aesEcbHexUpper(plain: String): String {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(KEY.toByteArray(Charsets.UTF_8), "AES"),
        )
        return cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .uppercase()
    }
}
