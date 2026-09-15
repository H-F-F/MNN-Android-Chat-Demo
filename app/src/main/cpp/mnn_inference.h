//
// mnn_inference.h
// MNN LLM 推理的 JNI 封装声明。
//
#ifndef MNN_INFERENCE_H
#define MNN_INFERENCE_H

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeLoadModel(JNIEnv* env, jobject thiz, jstring configPath);

JNIEXPORT jstring JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeGenerate(JNIEnv* env, jobject thiz, jlong handle,
                                                          jstring prompt, jobject callback);

JNIEXPORT void JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeReset(JNIEnv* env, jobject thiz, jlong handle);

JNIEXPORT void JNICALL
Java_com_mnn_chatdemo_inference_MnnEngine_nativeRelease(JNIEnv* env, jobject thiz, jlong handle);

#ifdef __cplusplus
}
#endif

#endif // MNN_INFERENCE_H
