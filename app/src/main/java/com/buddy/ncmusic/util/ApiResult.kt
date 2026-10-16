package com.buddy.ncmusic.util

/**
 * 统一的结果封装，用于网络层到 UI 层的错误传递。
 *
 * ## 为什么需要 [ErrorKind]
 *
 * 原先只有 `Success` / `Error(message)` 两态，UI 无法分辨失败的性质，
 * 结果只能一律提示"加载失败"，用户既不知道该重试还是该登录，
 * 也看不出「服务器正常返回但确实没有数据」与「请求根本没成功」的区别。
 *
 * 现在把失败**显式分类**，让调用方可以据此给出不同的引导：
 *  - [ErrorKind.NETWORK]  —— 值得重试
 *  - [ErrorKind.PARSE]    —— 数据异常，重试通常无效
 *  - [ErrorKind.AUTH]     —— 必须登录，重试无用
 *  - [ErrorKind.BUSINESS] —— 业务码错误，需要具体文案
 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>

    data class Error(
        val message: String,
        val code: Int = -1,
        val kind: ErrorKind = ErrorKind.BUSINESS,
    ) : ApiResult<Nothing>

    /** 失败性质的分类，供 UI 决定引导方式 */
    enum class ErrorKind {
        /** 网络层失败：连接不上、超时、HTTP 非 2xx */
        NETWORK,

        /** 响应体无法解析为预期结构（JSON 语法错误或结构不符） */
        PARSE,

        /** 登录态失效或需要登录（网易云 code 301 / 401） */
        AUTH,

        /** 服务端返回了明确业务错误码 */
        BUSINESS,
    }
}

/** 业务异常，message 会直接展示给用户 */
class ApiException(val code: Int, message: String) : Exception(message)
