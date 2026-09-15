# MNN Android Chat Demo - JNI 构建脚本
#
# 产物:libmnnchat.so(链接 libMNN.so + libllm.so)
#
# 构建前置(缺失时给出明确报错):
#   1. 把 MNN 仓库 include/ 目录内容拷入 app/src/main/cpp/mnn/include/
#   2. 把 libMNN.so、libllm.so 放入 app/src/main/jniLibs/arm64-v8a/
# 详见项目根 README「依赖就位」。

cmake_minimum_required(VERSION 3.22.1)
project(mnnchat LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 本目录为 app/src/main/cpp,向上两级即 app/src/main/jniLibs
set(MNN_JNI_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../jniLibs/arm64-v8a)

foreach(lib MNN llm)
    if(NOT EXISTS "${MNN_JNI_LIBS_DIR}/lib${lib}.so")
        message(FATAL_ERROR
            "[mnnchat] 缺少 lib${lib}.so: 请先将 MNN 预编译库放入 "
            "app/src/main/jniLibs/arm64-v8a/ 后再构建(见 README「依赖就位」)")
    endif()
endforeach()

# 引入 MNN 预编译库
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${MNN_JNI_LIBS_DIR}/libMNN.so")

add_library(llm SHARED IMPORTED)
set_target_properties(llm PROPERTIES IMPORTED_LOCATION "${MNN_JNI_LIBS_DIR}/libllm.so")

# 自研 JNI 库
add_library(mnnchat SHARED mnn_inference.cpp)

# MNN 头文件目录(MNN 仓库 include/ 内容拷入)
target_include_directories(mnnchat PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/mnn/include)

# 链接 MNN 推理库;Android 打包时 libMNN.so / libllm.so 随 jniLibs 一起进 APK
target_link_libraries(mnnchat MNN llm)
