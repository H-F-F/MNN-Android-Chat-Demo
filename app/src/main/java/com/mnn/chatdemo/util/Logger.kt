package com.mnn.chatdemo.util

import android.util.Log

/**
 * 统一日志工具。
 * 所有日志使用固定 tag `MnnChat`,并提供毫秒级耗时统计,用于 T8 的端到端耗时验证。
 */
object Logger {

    private const val TAG = "MnnChat"

    fun d(message: String) = Log.d(TAG, message)

    fun i(message: String) = Log.i(TAG, message)

    fun w(message: String) = Log.w(TAG, message)

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable == null) Log.e(TAG, message) else Log.e(TAG, message, throwable)
    }

    /**
     * 打印耗时日志。
     * @param tag 业务标签,如 "ModelManager" / "MnnEngine" / "ChatViewModel"
     * @param startNanos System.nanoTime() 起点
     * @param message 日志内容
     */
    fun logElapsed(tag: String, startNanos: Long, message: String) {
        val ms = (System.nanoTime() - startNanos) / 1_000_000
        i("[$tag] $message 耗时: ${ms}ms")
    }
}
