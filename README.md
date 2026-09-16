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

1. **端侧 LLM 完整链路**:assets 模型目录 → 内部存储拷贝/校验 → `Llm::createLLM(modelDir)`
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
├── cpp/             CMakeLists.txt + mnn_inference.cpp/h + llm_stream_buffer.hpp
│                    + utf8_stream_processor.hpp + mnn/include/(MNN 头文件,已入库)
├── assets/models/   qwen2-0.5b-instruct/(模型目录,自备,不入库)
└── jniLibs/         arm64-v8a/libMNN.so(官方 MNNChat App 同款,已入库)
```

## 快速开始

### 1. 环境(一次性)

- Android Studio + SDK Platform 34 + NDK 25.2.9519653 + CMake 3.22.1
- JDK 17+(本机 JDK 21 亦可,Gradle wrapper 已配 8.7)
- **arm64 真机**(仅编译 arm64-v8a,模拟器 x86 无法运行)

### 2. 依赖就位(三件套)

| 依赖 | 放哪 | 状态 |
|---|---|---|
| MNN 头文件 | `cpp/mnn/include/`(`MNN/` 与 `llm/` 目录) | ✅ 已随仓库提交 |
| MNN so | `jniLibs/arm64-v8a/libMNN.so` | ✅ 已随仓库提交(官方 MNNChat App 同款,LLM 内置) |
| 模型目录 | `assets/models/qwen2-0.5b-instruct/` | ⬜ **需自行下载**(体积 ~532MB,不入库,见下方「模型获取」) |

> so 与头文件已就位:`libMNN.so` 取自官方 MNNChat App(引擎 3.5.x,`-DMNN_BUILD_LLM=ON`,
> LLM 模块内置其中,无需 `libllm.so`),头文件取自 MNN 仓库 `include/` 与
> `transformers/llm/engine/include/`。如需重新获取 so,运行
> `powershell -ExecutionPolicy Bypass -File scripts\fetch_mnn_libs.ps1`。
>
> 模型缺失时 App 会 Snackbar 提示,不崩溃;CMake 缺 so 时会直接报错。

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

**推荐:直接下载官方预转换模型(ModelScope,已验证可直连)**

```
MNN/Qwen2-0.5B-Instruct-MNN
```

单文件下载地址模板:

```
https://modelscope.cn/api/v1/models/MNN/Qwen2-0.5B-Instruct-MNN/repo?Revision=master&FilePath=<文件名>
```

需要 8 个文件:`config.json`、`configuration.json`、`llm_config.json`、`tokenizer.txt`、
`llm.mnn`、`llm.mnn.json`、`llm.mnn.weight`、`embeddings_bf16.bin`。
大文件(`llm.mnn.weight` 等)返回 302 跳转,需解析响应中的 `href` 再下载。
模型目录内另有 README 说明。

**备选:自行转换**

```bash
git lfs install
git clone https://www.modelscope.cn/qwen/Qwen2-0.5B-Instruct.git
cd MNN/transformers/llm/export
pip install -r requirements.txt
python llmexport.py --path /path/to/Qwen2-0.5B-Instruct --export mnn --quant_bit 4 --hqq
```

把导出目录(含 config.json 的那一层)整体放入 `assets/models/qwen2-0.5b-instruct/`。

## 真机调优记录(面试亮点)

在 8 核 arm64 中端手机(vivo,Android 14)上实测调优:

| 项 | 结论 |
| --- | --- |
| 模型选型 | **0.5B 可用**(热后 1~6 秒/条);1.5B 内容更稳但 CPU 仅 1~2 token/s,单条 2 分钟+,物理不可用。端侧"速度 vs 质量"权衡的实证 |
| 采样参数 | 默认参数下 0.5B 严重复读死循环;收敛为 `temperature 0.7 / top_k 40 / top_p 0.9 / repetition_penalty 1.3 / n_gram 4 / ngram_factor 1.2` 后复读大幅缓解 |
| 系统提示词 | **长指令会让 0.5B 带偏**(吐提示词、答非所问);极简提示词(`你是AI助手,请简洁准确回答。`)恢复稳定 |
| 重复兜底 | Kotlin 层文本窗口(24 字符)检测尾部复读,连中 2 次调 `nativeStop` 掐断生成;`max_new_tokens=128` 双保险 |
| 长度上限 | C++ `kMaxNewTokens=128`:复读最多重复一小段,正常短问答不受影响 |
| JNI 语义 | MNN 3.5+ 的 `Llm::createLLM` 需传 **config.json 完整路径**(以文件所在目录为 base_dir);传裸目录会拼出缺斜杠路径导致 `tokenizer file not found` |
| 线程模型 | 推理在 `Dispatchers.Default`,逐 token JNI 回调切主线程更新 UI,全程无 ANR |

## 已知边界与 TODO

- **单轮对话**:目前每次生成前 `reset()` 清空 KV cache,保证输出可控;
  多轮上下文复用(reuse_kv + chat template)留作扩展点;
- **CPU 后端**:config.json `backend_type="cpu"` 保证全机型兼容;
  Kotlin 层已预留 OpenCL 开关位,后续可切 GPU 并对比耗时;
- **内存对话**:无 Room 持久化,退出即清空(按需求简化);
- **双端**:如需双端原生,可基于同一架构扩展 iOS(MNN Metal 后端)。

## 验证状态

- [x] 工程骨架与全部代码生成
- [x] JNI 命名一致性静态校验
- [x] MNN 三件套:头文件 + so 已入库,模型按「模型获取」下载
- [x] `assembleDebug` 全链路编译通过(AGP 8.2.2 / Kotlin 1.9.22 / Gradle 8.7 / NDK 25)
- [x] **真机端到端验证**:0.5B 加载成功、流式对话、数学题 1 秒内答对、复读被检测掐断
- [ ] 演示录屏(GitHub README 可放 GIF)
- [ ] iOS 端(可选扩展)

## License

MIT
