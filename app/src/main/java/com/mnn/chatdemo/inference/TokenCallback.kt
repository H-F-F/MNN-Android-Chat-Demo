package com.mnn.chatdemo.inference

/**
 * native 逐 token 回调接口。
 * C++ 侧通过 JNI 找到本接口的 onToken 方法并逐段回调增量文本。
 * 注意:此接口被 JNI 直接调用,签名变更需同步修改 mnn_inference.cpp 中的 GetMethodID。
 */
interface TokenCallback {
    fun onToken(token: String)
}
