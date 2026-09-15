# MNN-Android-Chat-Demo

纯离线端侧大模型聊天 Demo:**Android 原生(Kotlin + Jetpack Compose)+ MNN LLM 推理引擎**,
在 arm64 手机上本地运行 Qwen2-0.5B 量化模型,流式输出对话,全程无网络请求。

```
┌─────────────────────────────────────────────────┐
│              UI 聊天层 (Compose)                  │
│  ChatScreen / ChatViewModel / ChatUiState        │
│  MessageItem / Theme                             │
└───────────────────┬─────────────────────────────┘
                    │ StateFlow / Channel
┌───────────────────▼─────────────────────────────┐
│           MNN 推理引擎层 (Kotlin + JNI)           │
│  MnnEngine (loadModel/generate/reset/release)    │
│  ↓ JNI                                           │
│  cpp/mnn_inference.cpp (MNN LLM C++ API 封装)     │
└───────────────────┬─────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────┐
│          模型文件管理层                            │
│  ModelManager (assets→内部存储拷贝/校验/懒加载)     │
└───────────────────┬─────────────────────────────┘
                    │
┌───────────────────▼─────────────────────────────┐
│            通用工具层                              │
│  Logger / Result / DispatcherProvider             │
└─────────────────────────────────────────────────┘
```

## 技术亮点(面试可以直接讲)

1. **端侧 LLM 完整链路**:assets 模型目录 → 内部存储拷贝/校验 → `Llm::createLLM(config.json)`
   → 加载 → 流式生成 → 释放,验证 MNN LLM 在 Android arm64 上的完整工程链路;
2. **JNI 桥接**:Kotlin `external` 方法 ↔ C++ `Java_com_mnn_chatdemo_inference_MnnEngine_nativeXxx`
   严格按 Android JNI 规范命名,逐 token 回调经 JNI 回传,`Dispatchers.Main` 更新 UI;
3. **线程模型**:推理强制后台线程(Dispatchers.Default),主线程零阻塞,无 ANR;
4. **健壮性**:模型缺失/损坏有存在性校验和 Snackbar 提示,不崩溃;`onCleared` 释放 native 资源;
5. **工程化**:四层单向依赖、协程 + StateFlow、统一日志(含毫秒级耗时埋点)、仅 arm64-v8a。

## 目录结构

```
app/src/main/
├── java/com/mnn/chatdemo/
│   ├── MainActivity.kt
│   ├── ui/          ChatScreen / ChatViewModel / ChatUiState / MessageItem / theme
│   ├── inference/   MnnEngine(JNI 封装) / TokenCallback
│   ├── model/       ModelManager(模型拷贝/校验)
│   ├── data/        Message
│   └── util/        Logger / Result / DispatcherProvider
├── cpp/             CMakeLists.txt + mnn_inference.cpp/h + mnn/include/(MNN 头文件,自备)
├── assets/models/   qwen2-0.5b-instruct/(模型目录,自备)
└── jniLibs/         arm64-v8a/(libMNN.so + libllm.so,自备)
```

## 快速开始

### 1. 环境(一次性)

- Android Studio + SDK Platform 34 + NDK 25.2.9519653 + CMake 3.22.1
- JDK 17+(本机 JDK 21 亦可,Gradle wrapper 已配 8.7)
- **arm64 真机**(仅编译 arm64-v8a,模拟器 x86 无法运行)

### 2. 依赖就位(三件套)

| 依赖 | 放哪 | 说明 |
|---|---|---|
| MNN 头文件 | `cpp/mnn/include/` | 把 MNN 仓库 `include/` 内容拷入,见该目录 README |
| MNN so | `jniLibs/arm64-v8a/` | `libMNN.so` + `libllm.so`(MNN 需 `-DMNN_BUILD_LLM=ON`),见该目录 README |
| 模型目录 | `assets/models/qwen2-0.5b-instruct/` | llmexport 导出产物,见该目录 README |

> 三处均为外部依赖,工程代码只做存在性校验与友好提示,不伪造占位实现。
> CMake 会在缺 so 时报错;App 会在缺模型时 Snackbar 提示。

### 3. 构建运行

```bash
./gradlew assembleDebug          # 产出 app/build/outputs/apk/debug/app-debug.apk
```

安装到真机 → 首次发送消息会触发模型从 assets 拷贝到内部存储(约 400MB,耗时十几秒,
Logcat 可见进度)→ 之后流式对话。

### 4. 耗时验证(T8)

一次推理后 Logcat(`tag=MnnChat`)应包含:

```
[ModelManager] 模型就绪 ... 耗时: xxxms      ← 拷贝耗时
[ChatViewModel] 开始生成 ...                   ← 首 token 等待起点
[ChatViewModel] 生成完成 len=xxx 耗时: xxxms   ← 整体推理耗时
```

## 模型获取

```bash
git lfs install
git clone https://www.modelscope.cn/qwen/Qwen2-0.5B-Instruct.git
cd MNN/transformers/llm/export
pip install -r requirements.txt
python llmexport.py --path /path/to/Qwen2-0.5B-Instruct --export mnn --quant_bit 4 --hqq
```

把导出目录(含 config.json 的那一层)整体放入 `assets/models/qwen2-0.5b-instruct/`。
也可从 ModelScope/HuggingFace 下载 mnn-llm 预转换模型。

## 已知边界与 TODO

- **单轮对话**:目前每次生成前 `reset()` 清空 KV cache,保证输出可控;
  多轮上下文复用(reuse_kv + chat template)留作扩展点;
- **CPU 后端**:config.json `backend_type="cpu"` 保证全机型兼容;
  Kotlin 层已预留 OpenCL 开关位,后续可切 GPU 并对比耗时;
- **内存对话**:无 Room 持久化,退出即清空(按需求简化);
- **双端**:如需双端原生,可基于同一架构扩展 iOS(MNN Metal 后端)。

## 验证状态

- [x] 工程骨架与全部代码生成(T1-T7)
- [x] JNI 命名一致性静态校验
- [x] 日志埋点与异常加固(T8/T9 代码就位)
- [ ] 编译验证:需 SDK/NDK + MNN 三件套就位(见「依赖就位」)
- [ ] 真机端到端验证 + 演示录屏

## License

MIT
