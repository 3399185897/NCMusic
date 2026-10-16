package com.buddy.ncmusic.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 版本更新检查。
 *
 * 数据源是项目的 **GitHub Releases**（`releases/latest`）——
 * 应用自身的接口域名（`music.163.com`）与发行渠道无关，
 * 版本信息只能来自仓库。
 *
 * ## 为什么单独建一个 OkHttp 客户端
 *
 * `DirectApi` 的客户端带有网易云专用请求头（`Referer` / `Cookie`）且复用连接池。
 * 把它用于 GitHub 请求既会把网易的 Referer 发给第三方，
 * 也会让两类完全无关的流量共用一个连接池。
 * 这里单独建一个轻量客户端，超时也设得更短（更新检查不值得让用户等）。
 *
 * ## 网络配置
 *
 * `api.github.com` 走 HTTPS，而本项目已通过 `network_security_config`
 * 默认强制 HTTPS、仅对网易域名开放明文例外 —— 因此该请求无需额外配置。
 */
object UpdateChecker {

    private const val REPO = "3399185897/NCMusic"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"

    /** 单次检查的超时：更新检查是"顺带"操作，不该让用户久等 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 一个可用的新版本 */
    data class ReleaseInfo(
        /** 例如 `v1.2.0` */
        val tag: String,
        /** Release 页面地址，用于跳转浏览器 */
        val htmlUrl: String,
        /** 更新说明（Release body），可能为空 */
        val notes: String,
    ) {
        /** 去掉前导 `v` 的版本号，用于展示 */
        val version: String get() = tag.trimStart('v', 'V')
    }

    /** 检查结果 */
    sealed interface Result {
        /** 已是最新 */
        data object UpToDate : Result

        /** 有可用更新 */
        data class Available(val info: ReleaseInfo) : Result

        /** 检查失败（网络、解析、限流等） */
        data class Failed(val message: String) : Result
    }

    /**
     * 检查是否有新版本。
     *
     * @param currentVersion 当前版本号（如 `1.2.0`，不含前导 `v`）
     */
    suspend fun check(currentVersion: String): Result = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NCMusic-update-check")
                .build()

            client.newCall(request).execute().use { resp ->
                when {
                    // 404：仓库尚无任何 Release —— 视为"暂无可更新版本"而非错误
                    resp.code == 404 -> return@withContext Result.UpToDate

                    // 403：GitHub 对未认证请求有速率限制（每小时 60 次）
                    resp.code == 403 -> return@withContext Result.Failed(
                        "检查过于频繁，请稍后再试",
                    )

                    !resp.isSuccessful -> return@withContext Result.Failed(
                        "服务暂时不可用（HTTP ${resp.code}）",
                    )
                }

                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@withContext Result.Failed("响应为空")

                val json = JSONObject(body)
                val tag = json.optString("tag_name").takeIf { it.isNotBlank() }
                    ?: return@withContext Result.Failed("响应缺少版本信息")
                val url = json.optString("html_url")
                    .takeIf { it.isNotBlank() }
                    ?: "https://github.com/$REPO/releases"

                if (isNewer(tag, currentVersion)) {
                    Result.Available(
                        ReleaseInfo(
                            tag = tag,
                            htmlUrl = url,
                            notes = json.optString("body").orEmpty(),
                        ),
                    )
                } else {
                    Result.UpToDate
                }
            }
        }.getOrElse { e ->
            Result.Failed(
                when (e) {
                    is java.io.IOException -> "网络连接失败，请检查网络"
                    else -> "检查失败：${e.message ?: e.javaClass.simpleName}"
                },
            )
        }
    }

    /**
     * 判断 [remote] 是否比 [local] 更新。
     *
     * 逐段比较数字（`1.10.0` > `1.9.0` —— 字符串比较会得出相反结论）。
     * 段数不一致时缺失段按 0 处理（`1.2` 与 `1.2.0` 视为相同）。
     * 任一段不是数字（如 `v1.2.0-beta`）时，只比较能解析出的部分。
     */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        val size = maxOf(r.size, l.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    /** `v1.2.0` / `1.2.0-beta` -> `[1, 2, 0]` */
    private fun parseVersion(raw: String): List<Int> =
        raw.trim()
            .trimStart('v', 'V')
            // 预发布后缀（-beta / +build）不参与比较
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .mapNotNull { it.trim().takeWhile(Char::isDigit).toIntOrNull() }
}
