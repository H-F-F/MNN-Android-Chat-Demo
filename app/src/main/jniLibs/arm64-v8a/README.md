# jniLibs/arm64-v8a 占位

放入 MNN 预编译 so(仅 arm64-v8a):

```
libMNN.so    ← MNN 基础推理库
libllm.so    ← MNN LLM 模块库(必须,MNN 需以 -DMNN_BUILD_LLM=ON 编译)
```

**注意:不是 libMNN_Express.so!** LLM 推理需要的是 libllm.so。

## 获取方式

1. **优先**:检查 MNN GitHub Release 的 android 预编译包是否包含 libllm.so;
2. **否则自己编译**(官方文档 docs/compile):
   ```
   cd MNN && mkdir build_android && cd build_android
   cmake .. -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
            -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
            -DMNN_BUILD_LLM=ON -DMNN_SUPPORT_TRANSFORMER_FUSE=ON
   make -j8
   ```
   产物 libMNN.so / libllm.so 拷入本目录。
3. 若使用 OpenCL 加速,还需 libMNN_CL.so(本项目默认 CPU,暂不需要)。

## 注意

- 本目录下的 .so 已被 .gitignore 排除,建议用 git-lfs 或提供下载脚本;
- CMakeLists.txt 会在构建前校验两个 so 是否存在,缺失时报明确错误。
