package com.mnn.chatdemo.inference

import com.mnn.chatdemo.util.Logger

/**
 * MNN LLM 推理引擎的 Kotlin JNI 封装层。
 *
 * native 方法所在类:com.mnn.chatdemo.inference.MnnEngine
 * JNI 函数名严格遵循 Android JNI 规范:
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeLoadModel
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeGenerate
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeReset
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeRelease
 * (与 app/src/main/cpp/mnn_inference.cpp 中的实现一一对应)
 *
 * 线程模型:
 * - nativeLoadModel / nativeGenerate / nativeReset 均为阻塞调用,必须跑在后台线程
 *   (由 ChatViewModel 通过 Dispatchers.Default 调度);
 * - nativeGenerate 在 native 线程上逐 token 回调 [TokenCallback.onToken],
 *   回调内部由调用方负责切回主线程更新 UI。
 */
class MnnEngine {

    init {
        // 加载自研 JNI 库;libMNN.so / libllm.so 由 CMake 链接进 libmnnchat.so
        System.loadLibrary("mnnchat")
    }

    /**
     * 加载模型。configPath 指向 MNN LLM 模型目录下的 config.json。
     * @return native 句柄(非 0 表示成功);失败时通过异常抛出并返回 0
     */
    external fun nativeLoadModel(configPath: String): Long

    /**
     * 流式生成。prompt 为原始文本;每生成一段文本即回调 [TokenCallback.onToken]。
     * @return 完整回复文本
     */
    external fun nativeGenerate(handle: Long, prompt: String, callback: TokenCallback): String

    /** 重置会话(清空 KV cache),使下一次 generate 从零开始 */
    external fun nativeReset(handle: Long)

    /** 释放 native 会话与内存 */
    external fun nativeRelease(handle: Long)

    companion object {
        private const val TAG = "MnnEngine"
        fun log(message: String) = Logger.i("[$TAG] $message")
    }
}
