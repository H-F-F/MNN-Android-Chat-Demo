# MNN Android Chat Demo - JNI 构建脚本
#
# 产物:libmnnchat.so(链接官方 MNNChat App 同款 libMNN.so,LLM 模块内置其中)
#
# 构建前置(缺失时给出明确报错):
#   1. 把 MNN 头文件(MNN/ 与 llm/ 目录)放入 app/src/main/cpp/mnn/include/
#   2. 把 libMNN.so 放入 app/src/main/jniLibs/arm64-v8a/
# 自动化获取脚本:scripts/fetch_mnn_libs.ps1(下载官方 MNNChat APK 并提取 libMNN.so)

cmake_minimum_required(VERSION 3.22.1)
project(mnnchat LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 本目录为 app/src/main/cpp,向上两级即 app/src/main/jniLibs
set(MNN_JNI_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/arm64-v8a)

if(NOT EXISTS "${MNN_JNI_LIBS_DIR}/libMNN.so")
    message(FATAL_ERROR
        "[mnnchat] 缺少 libMNN.so: 请先将 MNN 预编译库放入 "
        "app/src/main/jniLibs/arm64-v8a/ 后再构建。"
        "可运行 scripts/fetch_mnn_libs.ps1 自动获取(下载官方 MNNChat APK 并提取)。")
endif()

# 引入 MNN 预编译库(LLM 模块已内置其中,无需单独的 libllm.so)
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${MNN_JNI_LIBS_DIR}/libMNN.so")

# 自研 JNI 库
add_library(mnnchat SHARED mnn_inference.cpp)

# MNN 头文件目录(MNN 仓库 include/ + transformers/llm/engine/include/ 内容拷入)
target_include_directories(mnnchat PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/mnn/include)

# 链接 MNN 推理库;Android 打包时 libMNN.so 随 jniLibs 一起进 APK
target_link_libraries(mnnchat MNN)
