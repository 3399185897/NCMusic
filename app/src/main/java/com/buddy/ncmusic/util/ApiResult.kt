package com.buddy.ncmusic.util

/** 统一的结果封装，用于网络层到 UI 层的错误传递 */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val code: Int = -1) : ApiResult<Nothing>
}

/** 业务异常，message 会直接展示给用户 */
class ApiException(val code: Int, message: String) : Exception(message)
