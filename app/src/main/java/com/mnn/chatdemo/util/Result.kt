package com.mnn.chatdemo.util

/**
 * 统一结果封装:成功携带数据,失败携带异常与可展示给用户的错误信息。
 * 避免用裸异常/裸 null 在层间传递状态。
 */
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure(val exception: Throwable? = null, val message: String) : Result<Nothing>()
}
