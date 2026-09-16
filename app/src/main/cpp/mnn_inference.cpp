//
// mnn_inference.cpp
// MNN LLM 推理引擎的 JNI 实现(对应 Kotlin 类 com.mnn.chatdemo.inference.MnnEngine)。
//
// 依赖:
//   - libMNN.so:MNN 以 -DMNN_BUILD_LLM=ON 编译,LLM 模块内置其中,
//     官方 MNNChat Android App 即以这种形态分发(无独立 libllm.so)。
//   - MNN 头文件:app/src/main/cpp/mnn/include/ 下(MNN/ 与 llm/ 两个目录,
//     取自 MNN 仓库 include/ 与 transformers/llm/engine/include/)。
//
// API 说明(MNN 3.5+ / 3.6,命名空间 MNN::Transformer):
//   入口 : MNN::Transformer::Llm::createLLM(modelDir) -> load();
//   推理 : response(history, &os, "<eop>", 0) 做 prefill,
//          再循环 generate(1) 逐 token 解码;
//   流式 : 解码文本经自定义 streambuf -> UTF-8 边界整理 -> 回调 TokenCallback.onToken;
//   结束 : 生成完成标记为 "<eop>"。
//
// 参考实现:官方 MNN 仓库 apps/Android/MnnLlmChat 的 llm_session.cpp,
// 本文件为其最小化裁剪(去掉 R1 / 多模态 / 历史同步,保留核心 stepping 逻辑)。
//

#include <jni.h>
#include <cstdio>
#include <string>
#include <atomic>
#include <functional>
#include <sstream>

#include "mnn_inference.h"
#include "llm_stream_buffer.hpp"
#include "utf8_stream_processor.hpp"

#include <llm/llm.hpp>

using MnnLlm = MNN::Transformer::Llm;
using ChatMessages = MNN::Transformer::ChatMessages;
using LlmStatus = MNN::Transformer::LlmStatus;

namespace {

constexpr const char* kEndMark = "<eop>";
constexpr int kMaxNewTokens = 512;
constexpr const char* kSystemPrompt = "You are a helpful assistant.";

// 停止标志:nativeStop() 可被任意线程调用,生成循环轮询该标志提前退出
std::atomic<bool> g_stopRequested{false};

void ThrowIllegalState(JNIEnv* env, const std::string& message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
        env->DeleteLocalRef(clazz);
    }
}

// 官方 resolveAndroidSteppingEop 的裁剪版:Android 预编译运行时每次 generate(1)
// 都会输出 <eop> 并把状态置为 MAX_TOKENS_FINISHED,因此"未停止且未达上限"时
// 视为中间边界,把状态复位为 RUNNING 继续解码;真正的结束以最终 <eop> 判定。
bool HandleStepEnd(MnnLlm* llm, bool stop_requested, int current_size, bool& pending_eop) {
    auto* context = llm->getContext();
    if (context == nullptr) {
        return true;  // 无法判断,按结束处理
    }
    if (context->status == LlmStatus::MAX_TOKENS_FINISHED && !stop_requested &&
        current_size < kMaxNewTokens) {
        const_cast<MNN::Transformer::LlmContext*>(context)->status = LlmStatus::RUNNING;
        pending_eop = false;  // 中间边界,不作为最终结束
        return false;
    }
    if (context->status == LlmStatus::NORMAL_FINISHED && !pending_eop && !stop_requested &&
        current_size < kMaxNewTokens) {
        const_cast<MNN::Transformer::LlmContext*>(context)->status = LlmStatus::RUNNING;
        return false;
    }
    if (pending_eop) {
        return true;  // 真正的生成结束
    }
    return false;
}

// 若上一轮结束留下终态,先复位,避免新一轮 response() 直接失败
void EnsureRunningState(MnnLlm* llm) {
    auto* context = llm->getContext();
    if (context == nullptr) {
        return;
    }
    if (context->status == LlmStatus::MAX_TOKENS_FINISHED ||
        context->status == LlmStatus::NORMAL_FINISHED ||
        context->status == LlmStatus::USER_CANCEL) {
        const_cast<MNN::Transformer::LlmContext*>(context)->status = LlmStatus::RUNNING;
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeLoadModel(JNIEnv* env, jobject /*thiz*/,
                                                           jstring modelDir) {
    if (modelDir == nullptr) {
        ThrowIllegalState(env, "modelDir 为空");
        return 0L;
    }
    const char* dirChars = env->GetStringUTFChars(modelDir, nullptr);
    if (dirChars == nullptr) {
        return 0L;
    }
    std::string dir(dirChars);
    env->ReleaseStringUTFChars(modelDir, dirChars);

    try {
        // createLLM 读取模型目录内的 config.json(backend / thread_num / precision 均在文件内)
        MnnLlm* llm = MnnLlm::createLLM(dir);
        if (llm == nullptr) {
            ThrowIllegalState(env, "createLLM 失败: 请检查模型目录与 config.json 是否完整");
            return 0L;
        }
        if (!llm->load()) {
            ThrowIllegalState(env, "模型加载失败(load() 返回 false): 模型文件缺失/损坏或内存不足");
            delete llm;
            return 0L;
        }
        return reinterpret_cast<jlong>(llm);
    } catch (const std::exception& e) {
        ThrowIllegalState(env, std::string("nativeLoadModel 异常: ") + e.what());
        return 0L;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeGenerate(JNIEnv* env, jobject /*thiz*/,
                                                          jlong handle, jstring prompt,
                                                          jobject callback) {
    auto* llm = reinterpret_cast<MnnLlm*>(handle);
    if (llm == nullptr) {
        ThrowIllegalState(env, "engine 未加载(handle 为空)");
        return nullptr;
    }
    if (prompt == nullptr) {
        ThrowIllegalState(env, "prompt 为空");
        return nullptr;
    }
    if (callback == nullptr) {
        ThrowIllegalState(env, "callback 为空");
        return nullptr;
    }

    const char* promptChars = env->GetStringUTFChars(prompt, nullptr);
    if (promptChars == nullptr) {
        return nullptr;
    }
    std::string promptStr(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    // TokenCallback.onToken(String) —— 签名必须与 Kotlin 侧一致
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(callbackClass);
    if (onTokenMethod == nullptr) {
        return nullptr;
    }

    g_stopRequested = false;
    bool pending_eop = false;
    bool generate_end = false;
    int current_size = 0;
    std::stringstream response_buffer;

    try {
        // 1) 构造对话历史(system + user),引擎按模型模板套用
        ChatMessages history;
        history.emplace_back("system", kSystemPrompt);
        history.emplace_back("user", promptStr);

        // 2) 流式管线:streambuf -> UTF-8 边界整理 -> (检测 <eop> / 回调 Kotlin)
        Utf8StreamProcessor utf8_processor([&](const std::string& chunk) {
            if (chunk.find(kEndMark) != std::string::npos) {
                pending_eop = true;  // <eop> 不追加文本也不回调
                return;
            }
            response_buffer << chunk;
            jstring jDelta = env->NewStringUTF(chunk.c_str());
            if (jDelta != nullptr) {
                env->CallVoidMethod(callback, onTokenMethod, jDelta);
                env->DeleteLocalRef(jDelta);
            }
        });
        LlmStreamBuffer stream_buffer(
            [&](const char* str, size_t len) { utf8_processor.processStream(str, len); });
        std::ostream output_ostream(&stream_buffer);

        // 3) prefill:只处理输入,不解码;结束后逐 token 解码
        EnsureRunningState(llm);
        llm->response(history, &output_ostream, kEndMark, 0);
        while (!g_stopRequested.load() && !generate_end && current_size < kMaxNewTokens) {
            llm->generate(1);
            current_size++;
            if (HandleStepEnd(llm, g_stopRequested.load(), current_size, pending_eop)) {
                generate_end = true;
            }
        }
    } catch (const std::exception& e) {
        ThrowIllegalState(env, std::string("nativeGenerate 异常: ") + e.what());
        return nullptr;
    }

    std::string response = response_buffer.str();
    if (response.empty() && current_size == 0) {
        ThrowIllegalState(env, "生成失败: 未产生任何输出");
        return nullptr;
    }
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeStop(JNIEnv* /*env*/, jobject /*thiz*/,
                                                      jlong /*handle*/) {
    // 任意线程调用;生成循环轮询到该标志后提前退出
    g_stopRequested = true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeGetPerf(JNIEnv* env, jobject /*thiz*/,
                                                         jlong handle) {
    auto* llm = reinterpret_cast<MnnLlm*>(handle);
    if (llm == nullptr) {
        return env->NewStringUTF("");
    }
    auto* context = llm->getContext();
    if (context == nullptr) {
        return env->NewStringUTF("");
    }
    // 返回 "prompt_len,gen_seq_len,prefill_us,decode_us",供 Kotlin 计算 tokens/s
    char buf[128];
    snprintf(buf, sizeof(buf), "%d,%d,%lld,%lld", context->prompt_len, context->gen_seq_len,
             static_cast<long long>(context->prefill_us),
             static_cast<long long>(context->decode_us));
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT void JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeReset(JNIEnv* /*env*/, jobject /*thiz*/,
                                                       jlong handle) {
    auto* llm = reinterpret_cast<MnnLlm*>(handle);
    if (llm == nullptr) {
        return;
    }
    g_stopRequested = false;
    llm->reset();  // 清空 KV cache 与内部历史(单轮对话模式)
}

extern "C" JNIEXPORT void JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/,
                                                         jlong handle) {
    auto* llm = reinterpret_cast<MnnLlm*>(handle);
    if (llm == nullptr) {
        return;
    }
    delete llm;  // Llm 析构负责释放会话与 KV cache
}
