package com.mnn.chatdemo.inference

import com.mnn.chatdemo.util.Logger

/**
 * MNN LLM 推理引擎的 Kotlin JNI 封装层。
 *
 * native 方法所在类:com.mnn.chatdemo.inference.MnnEngine
 * JNI 函数名严格遵循 Android JNI 规范:
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeLoadModel
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeGenerate
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeStop
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeGetPerf
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeReset
 *   Java_com_mnn_chatdemo_inference_MnnEngine_nativeRelease
 * (与 app/src/main/cpp/mnn_inference.cpp 中的实现一一对应)
 *
 * 底层引擎:官方 MNNChat App 同款 libMNN.so(MNN 3.5+,-DMNN_BUILD_LLM=ON,
 * LLM 模块内置其中,无独立 libllm.so)。
 *
 * 线程模型:
 * - nativeLoadModel / nativeGenerate / nativeReset 均为阻塞调用,必须跑在后台线程
 *   (由 ChatViewModel 通过 Dispatchers.Default 调度);
 * - nativeGenerate 在 native 线程上逐 token 回调 [TokenCallback.onToken],
 *   回调内部由调用方负责切回主线程更新 UI;
 * - nativeStop 可随时从任意线程调用,生成循环会在下一个 token 边界提前退出。
 */
class MnnEngine {

    init {
        // 加载自研 JNI 库;libMNN.so 由 CMake 链接进 libmnnchat.so
        System.loadLibrary("mnnchat")
    }

    /**
     * 加载模型。modelDir 为 MNN LLM 模型【目录】的绝对路径(含 config.json)。
     * @return native 句柄(非 0 表示成功);失败时通过异常抛出并返回 0
     */
    external fun nativeLoadModel(modelDir: String): Long

    /**
     * 流式生成。prompt 为原始文本;每生成一段文本即回调 [TokenCallback.onToken]。
     * @return 完整回复文本
     */
    external fun nativeGenerate(handle: Long, prompt: String, callback: TokenCallback): String

    /** 停止当前生成(任意线程可调;下一个 token 边界生效) */
    external fun nativeStop(handle: Long)

    /**
     * 获取最近一次生成性能指标,返回 "prompt_len,gen_seq_len,prefill_us,decode_us"。
     * 供 UI 计算 tokens/s 使用。
     */
    external fun nativeGetPerf(handle: Long): String

    /** 重置会话(清空 KV cache),使下一次 generate 从零开始 */
    external fun nativeReset(handle: Long)

    /** 释放 native 会话与内存 */
    external fun nativeRelease(handle: Long)

    companion object {
        private const val TAG = "MnnEngine"
        fun log(message: String) = Logger.i("[$TAG] $message")
    }
}
