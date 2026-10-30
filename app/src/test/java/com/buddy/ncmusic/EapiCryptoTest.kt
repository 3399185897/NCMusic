package com.buddy.ncmusic

import com.buddy.ncmusic.core.network.crypto.EapiCrypto
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * eapi 加密的**逐字节**验证。
 *
 * 背景：多轮真机反馈 eapi 均返回「HTTP 200 + 空 body」，
 * 而用 Python + openssl 按同一算法生成的 params 却能拿到正常 JSON。
 * 说明 Kotlin 实现与参考实现之间存在**肉眼难辨**的差异
 * （此前仅靠"逻辑对比"漏过了真实的差异点）。
 *
 * 因此把参考实现算出的 params 硬编码进来，直接断言逐字节相等；
 * 不相等时把实际值完整打印到测试输出，便于定位差异位置。
 */
class EapiCryptoTest {

    @Test
    fun encrypt_matchesReference() {
        val body = buildJsonObject {
            put("ids", "[186016]")
            put("level", "hires")
            put("encodeType", "flac")
            put("csrf_token", "")
        }
        val jsonBody = body.toString()
        println("ACTUAL_JSON_BODY=$jsonBody")
        println("EXPECT_JSON_BODY=$EXPECTED_JSON_BODY")

        val actual = EapiCrypto.encrypt("/api/song/enhance/player/url/v1", jsonBody)
        println("ACTUAL_PARAMS=$actual")
        println("EXPECT_PARAMS=$EXPECTED_PARAMS")

        // 逐字节断言；失败时输出会把差异暴露出来
        assertEquals(EXPECTED_PARAMS, actual)
    }

    private companion object {
        const val EXPECTED_JSON_BODY =
            """{"ids":"[186016]","level":"hires","encodeType":"flac","csrf_token":""}"""

        const val EXPECTED_PARAMS =
            "FA90B329E9614F79E79598F37DC2EDB487F00D1BC4C9B24CD57E6C318B907356" +
                "9338432CD7D98D1A3626E997A2C531218543BC970F66310273D4660DAF94B671" +
                "A972AA7E667D7C6902E6EFF01A3096430A41E6E5D6BF982EFB798708965E277D1" +
                "8956223899E8A6425BF8D45B6DA54F61EDA5D0691B5EE1C54F82C2ECF8AB3A6B8" +
                "7E6CB08DC488B64733826D3CE5BF73C8E95E82D3F256A7DFA11FC7CD96B204"
    }
}
