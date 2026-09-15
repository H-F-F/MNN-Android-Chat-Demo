//
// mnn_inference.cpp
// MNN LLM 推理引擎的 JNI 实现(对应 Kotlin 类 com.mnn.chatdemo.inference.MnnEngine)。
//
// 依赖:
//   - libMNN.so / libllm.so(MNN 需以 -DMNN_BUILD_LLM=ON 编译,见 README「依赖就位」)
//   - MNN 头文件:放置于 app/src/main/cpp/mnn/include/ 下(把 MNN 仓库 include/ 目录内容拷入)
//
// 版本适配说明(重要):
//   - 头文件:新版 MNN 主仓为 <llm/llm.hpp>;老版 mnn-llm 独立仓为 <llm.hpp>。
//     若编译报找不到头文件,改 #include <llm.hpp> 即可。
//   - 命名空间:MNN 2.8 为 MNN::LLM::Llm;若编译报命名空间不存在,改 using MNN::Llm。
//   - 入口:确认存在 Llm::createLLM(configPath) / load() / generate(...) / getResponse()。
//     如你的头文件 generate 只接受 int token 数组,参考下方被注释的重载写法。
//
// 流式输出策略:generate 的回调收到 tokenId 后,通过 getResponse() 与已发送长度做差,
// 得到增量文本逐段回调 Kotlin。若你的版本 getResponse() 仅在结束后可用,可改为
// 结束后一次性返回(仍可正常出结果,只是不流式)。
//

#include <jni.h>
#include <string>
#include <memory>
#include <cstdint>

#include "mnn_inference.h"

#include <llm/llm.hpp>   // MNN 2.8 主仓路径;老版为 <llm.hpp>

// 命名空间适配:2.8 为 MNN::LLM;更早版本为 MNN
using MnnLlm = MNN::LLM::Llm;

namespace {

// 抛出带 message 的 IllegalStateException,调用方(Kotlin)以异常形式捕获
void ThrowIllegalState(JNIEnv* env, const std::string& message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message.c_str());
        env->DeleteLocalRef(clazz);
    }
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeLoadModel(JNIEnv* env, jobject /*thiz*/,
                                                           jstring configPath) {
    if (configPath == nullptr) {
        ThrowIllegalState(env, "configPath 为空");
        return 0L;
    }
    const char* pathChars = env->GetStringUTFChars(configPath, nullptr);
    if (pathChars == nullptr) {
        return 0L;  // OutOfMemoryError 已抛出
    }
    std::string path(pathChars);
    env->ReleaseStringUTFChars(configPath, pathChars);

    try {
        // createLLM 读取 config.json(backend_type / thread_num / precision 等参数在文件里配置)
        std::unique_ptr<MnnLlm> llm(MnnLlm::createLLM(path));
        if (!llm) {
            ThrowIllegalState(env, "createLLM 失败: 请检查 config.json 与模型文件是否完整");
            return 0L;
        }
        auto error = llm->load();
        if (error != MNN::NO_ERROR) {
            ThrowIllegalState(env, "模型加载失败, ErrorCode=" + std::to_string(static_cast<int>(error)));
            return 0L;
        }
        return reinterpret_cast<jlong>(llm.release());
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

    const char* promptChars = env->GetStringUTFChars(prompt, nullptr);
    if (promptChars == nullptr) {
        return nullptr;
    }
    std::string promptStr(promptChars);
    env->ReleaseStringUTFChars(prompt, promptChars);

    // 解析 TokenCallback.onToken(String) 方法
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)V");
    env->DeleteLocalRef(callbackClass);
    if (onTokenMethod == nullptr) {
        return nullptr;  // NoSuchMethodError 已抛出
    }

    try {
        size_t lastLength = 0;
        // 逐 token 生成;tokenId == -1 表示生成结束
        // 若你的 MNN 版本 generate 只接受 token 数组,参考:
        //   std::vector<int> ids = ...; llm->generate(ids.data(), ids.size(), fn);
        auto error = llm->generate(promptStr, [&](int32_t tokenId) -> int32_t {
            if (tokenId == -1) {
                return 0;
            }
            std::string full = llm->getResponse();
            if (full.size() > lastLength) {
                std::string delta = full.substr(lastLength);
                lastLength = full.size();
                jstring jDelta = env->NewStringUTF(delta.c_str());
                if (jDelta != nullptr) {
                    env->CallVoidMethod(callback, onTokenMethod, jDelta);
                    env->DeleteLocalRef(jDelta);
                }
            }
            return 0;
        });

        std::string response = llm->getResponse();
        if (error != MNN::NO_ERROR && response.empty()) {
            ThrowIllegalState(env, "生成失败, ErrorCode=" + std::to_string(static_cast<int>(error)));
            return nullptr;
        }
        return env->NewStringUTF(response.c_str());
    } catch (const std::exception& e) {
        ThrowIllegalState(env, std::string("nativeGenerate 异常: ") + e.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeReset(JNIEnv* /*env*/, jobject /*thiz*/,
                                                       jlong handle) {
    auto* llm = reinterpret_cast<MnnLlm*>(handle);
    if (llm == nullptr) {
        return;
    }
    // 清空 KV cache,使下一次 generate 从零开始(单轮对话模式)
    llm->reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeRelease(JNIEnv* /*env*/, jobject /*thiz*/,
                                                         jlong handle) {
    auto* llm = reinterpret_cast<MnnLlm*>(handle);
    if (llm == nullptr) {
        return;
    }
    // Llm 析构负责释放会话与 KV cache;若你的头文件提供 release(),可在 delete 前调用
    delete llm;
}
