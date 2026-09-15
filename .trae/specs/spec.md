# MNN-Android-Chat-Demo - 技术方案 spec.md

> **实现状态更新(2026-09-15)**:T1-T9 代码已全部生成。按 MNN 官方文档核实后,以下关键点与本 spec 初版不同,以本更新为准:
> 1. **MNN LLM 模型是目录而非单个 .mnn 文件**:llmexport 导出 config.json / llm.mnn / llm.mnn.weight / tokenizer.mtok / llm_config.json;推理入口为 config.json 路径;
> 2. **依赖库为 libMNN.so + libllm.so**(MNN 需 `-DMNN_BUILD_LLM=ON`),不是 libMNN_Express.so;
> 3. **头文件**:新版主仓 `llm/llm.hpp`(命名空间 MNN::LLM,类 Llm,入口 createLLM(configPath));老版 `llm.hpp`(命名空间 MNN),代码已做适配注释;
> 4. tokenizer 使用 MNN 内置(.mtok 自动加载),无字符级切分降级;
> 5. 包名定为 com.mnn.chatdemo;Gradle 8.7 + AGP 8.2.2(兼容本机 JDK 21);
> 6. 生成前每次 reset() 清空 KV cache(单轮对话模式),多轮复用留作 TODO。
> 编译验证待 Android SDK/NDK 与 MNN 三件套(头文件/so/模型)就位后进行。

## 一、项目概述

| 项 | 值 |
|---|---|
| 项目名 | MNN-Android-Chat-Demo |
| 路径 | D:\GitHub\MNN-Android-Chat-Demo |
| 技术栈 | Android Kotlin + Jetpack Compose + MNN 2.8 LLM 推理引擎 |
| 定位 | 纯离线本地聊天 Demo，端侧加载 Qwen2-0.5B 量化 `.mnn` 模型完成对话 |

**目标**：验证 MNN 2.8 在 Android arm64-v8a 端侧运行 LLM 推理的完整工程链路（JNI 加载 → 模型管理 → 后台推理 → 流式输出 → UI 展示 → 内存释放），为后续更大模型落地提供可复用工程骨架。

## 二、硬性约束

| 类别 | 约束 |
|---|---|
| SDK | minSdk 26，targetSdk 34，compileSdk 34 |
| NDK | 25.2.9519653 |
| 构建 | CMake 3.22.1 + JNI |
| ABI | 仅 arm64-v8a（abiFilters 限制） |
| 网络 | 纯离线，不调用任何云端 API |
| 模型 | 内置 Qwen2-0.5B 量化 `.mnn` 模型 |
| 架构 | 四层分离：UI聊天层 / MNN推理引擎层 / 模型文件管理层 / 通用工具层 |
| 异步 | 协程 + ViewModel，推理强制后台线程，不阻塞 UI 主线程 |
| 健壮性 | 完善异常捕获、内存自动释放、运行耗时日志打印 |

## 三、分层架构设计

```
┌─────────────────────────────────────────────────┐
│              UI 聊天层 (Compose)                  │
│  ChatScreen / ChatViewModel / ChatUiState        │
│  MessageItem / Theme                             │
└───────────────────┬─────────────────────────────┘
                    │ StateFlow / Channel
┌───────────────────▼─────────────────────────────┐
│           MNN 推理引擎层 (Kotlin + JNI)           │
│  MnnEngine (loadModel/generate/release)          │
│  ↓ JNI                                           │
│  cpp/mnn_inference.cpp (MNN 2.8 LLM API 封装)     │
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

**依赖方向**：UI → ViewModel → 引擎/模型管理 → 工具，单向依赖，无循环。

## 四、目录树

```
MNN-Android-Chat-Demo/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/mnnchat/
│       │   ├── MainActivity.kt
│       │   ├── ui/
│       │   │   ├── ChatScreen.kt            # 聊天主界面
│       │   │   ├── ChatViewModel.kt         # 状态管理 + 推理调度
│       │   │   ├── ChatUiState.kt           # UI 状态 data class
│       │   │   ├── MessageItem.kt           # 消息气泡组件
│       │   │   └── theme/
│       │   │       ├── Color.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   ├── inference/
│       │   │   └── MnnEngine.kt             # Kotlin JNI 封装 + 流式推理
│       │   ├── model/
│       │   │   └── ModelManager.kt          # 模型拷贝/校验/路径管理
│       │   ├── data/
│       │   │   └── Message.kt               # 消息数据类
│       │   └── util/
│       │       ├── Logger.kt                # 统一日志 + 耗时统计
│       │       ├── Result.kt                # 成功/失败封装
│       │       └── DispatcherProvider.kt    # 协程调度器注入
│       ├── cpp/
│       │   ├── CMakeLists.txt               # native 构建脚本
│       │   ├── mnn_inference.cpp            # JNI 实现 + MNN 2.8 调用
│       │   ├── mnn_inference.h
│       │   └── mnn/
│       │       └── include/                 # MNN 头文件（用户放入）
│       ├── assets/
│       │   └── models/
│       │       └── qwen2-0.5b.mnn           # 模型文件（用户放入）
│       └── jniLibs/
│           └── arm64-v8a/
│               ├── libMNN.so                # MNN 推理库（用户放入）
│               └── libMNN_Express.so        # MNN Express 库（用户放入）
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/
    └── gradle-wrapper.properties
```

> **外部依赖说明**：`cpp/mnn/include/`、`jniLibs/arm64-v8a/*.so`、`assets/models/qwen2-0.5b.mnn` 由用户自行提供并放入上述目录。工程代码中对其做存在性校验与缺失提示，不伪造占位实现。

## 五、依赖清单

| 依赖 | 版本 | 用途 |
|------|------|------|
| AGP | 8.1.4 | Android Gradle Plugin |
| Kotlin | 1.9.22 | 开发语言 |
| Gradle | 8.4 | 构建工具 |
| Compose BOM | 2023.10.01 | UI 框架 |
| Material3 | 随 BOM | UI 组件库 |
| Lifecycle ViewModel Compose | 2.7.0 | ViewModel 状态管理 |
| Kotlin Coroutines | 1.7.3 | 异步调度 |
| Activity Compose | 1.8.2 | Compose Activity |
| NDK | 25.2.9519653 | native 编译 |
| CMake | 3.22.1 | native 构建 |
| MNN | 2.8 | LLM 推理引擎（外部 so + 头文件） |

## 六、核心类职责

### 6.1 工具层
- **Logger**：统一 tag `MnnChat`，封装 `d/i/w/e`，提供 `logElapsed(tag, startNanos, message)` 打印毫秒级耗时。
- **Result\<T\>**：`sealed class`，`Success(data)` / `Failure(exception, message)`。
- **DispatcherProvider**：提供 `default/ io / main` dispatcher，便于测试替换。

### 6.2 模型文件管理层
- **ModelManager**：
  - `ensureModelExists(): Result<String>` — 首次调用从 `assets/models/qwen2-0.5b.mnn` 拷贝到 `filesDir/models/`，存在则跳过；返回模型绝对路径。
  - `getModelSize(): Long` — 返回模型文件大小。
  - 懒加载：仅首次推理时触发，不阻塞 Application 启动。

### 6.3 MNN 推理引擎层
- **MnnEngine (Kotlin)**：
  - `init { System.loadLibrary("mnnchat") }`
  - external 方法：`nativeLoadModel(path)`、`nativeGenerate(prompt)`、`nativeRelease()`
  - 对外 API：
    - `loadModel(path): Result<Unit>` — 后台线程加载，防止重复加载
    - `generate(prompt, onToken: (String) -> Unit): Result<String>` — `Dispatchers.Default` 执行，逐 token 回调，最终返回完整文本
    - `release(): Unit` — 释放 native 资源
- **mnn_inference.cpp (C++/JNI)**：
  - `loadModel`：创建 MNN LLM 会话，加载 `.mnn` 模型
  - `generate`：设置 prompt，循环生成 token，通过 `CallVoidMethod` 回调 Kotlin
  - `release`：销毁会话，释放内存
  - 全链路 try-catch，异常通过 `env->ThrowNew` 抛出

### 6.4 UI 聊天层
- **ChatUiState**：`messages: List<Message>`、`input: String`、`isGenerating: Boolean`、`error: String?`
- **Message**：`id`、`role(user/assistant)`、`content`、`timestamp`
- **ChatViewModel**：
  - `_uiState = MutableStateFlow(ChatUiState())`，`errorChannel = Channel<String>()`
  - `sendMessage()`：校验非空 → 追加 user 消息 → `ensureModelExists` → `loadModel` → `generate`，逐 token 更新最后一条 assistant 消息的 content
  - `onCleared()` → `engine.release()`
- **ChatScreen**：
  - `LazyColumn` 消息列表，底部 `Row(TextField + Button)`
  - assistant 气泡流式渲染（StateFlow 驱动重组）
  - 推理中输入框/按钮 disabled，显示加载指示器
  - `LaunchedEffect` collect errorChannel → Snackbar 提示

## 七、开发任务清单

| # | 任务 | 优先级 | 依赖 | 关键产出 |
|---|------|--------|------|----------|
| T1 | 工程骨架与 Gradle 构建配置 | high | 无 | settings/build.gradle.kts、app/build.gradle.kts、CMake 配置、MainActivity |
| T2 | 通用工具层 | high | T1 | Logger、Result、DispatcherProvider |
| T3 | 模型文件管理层 | high | T2 | ModelManager（拷贝/校验/懒加载） |
| T4 | JNI native 层 | high | T1 | CMakeLists.txt、mnn_inference.cpp/h（MNN 2.8 封装） |
| T5 | Kotlin 推理引擎层 | high | T2,T4 | MnnEngine（loadModel/generate/release） |
| T6 | ChatViewModel 与状态管理 | high | T5 | ChatUiState、Message、ChatViewModel |
| T7 | Compose 聊天 UI | high | T6 | ChatScreen、MessageItem、Theme |
| T8 | 耗时日志与端到端验证 | medium | T7 | 全链路耗时埋点 + 手动验证 |
| T9 | 异常场景加固 | medium | T8 | 模型缺失/加载失败/推理中断容错 |

## 八、验收标准

| ID | 类型 | 描述 | 通过条件 |
|----|------|------|----------|
| AC-1 | rule | 工程可编译 | `./gradlew assembleDebug` 退出码 0，生成 apk |
| AC-2 | rule | 模型文件管理 | 首次拷贝到内部存储，二次跳过，大小匹配 |
| AC-3 | rule | 离线推理输出 | assistant 返回非空文本，UI 流式逐字渲染 |
| AC-4 | rule | UI 不卡顿 | 推理期间主线程无 ANR、无大量跳帧警告 |
| AC-5 | rule | 内存释放 | onCleared 调用 release，native 资源释放，无 crash |
| AC-6 | rule | 异常捕获 | 模型缺失/损坏时应用不崩溃，Snackbar 提示 |
| AC-7 | rule | 耗时日志完整 | 一次推理后日志含模型加载/分词/首token/整体耗时 |
| AC-8 | rubric | 架构分层清晰 | 1-5 分，阈值 ≥4：四层分离、依赖单向 |

## 九、关键风险与对策

| 风险 | 对策 |
|------|------|
| MNN 2.8 LLM API 命名空间/头文件路径不确定 | C++ 侧使用条件编译 + 注释标注 API 占位，用户根据实际头文件微调 |
| 模型文件未提供导致无法运行 | 工程内做存在性校验，缺失时明确日志提示，不伪造模型 |
| tokenizer 实现复杂 | 默认采用 Kotlin 侧简化 BPE 或通过 JNI 调用 MNN 内置 tokenizer；若不可用则降级为字符级切分并标注 |
| 流式输出线程切换 | generate 在 Default 线程执行，onToken 回调通过 `withContext(Dispatchers.Main)` 切回主线程更新 UI |

## 十、待确认问题

1. Qwen2-0.5B 量化 `.mnn` 模型的准确文件名与大小？是否附带 tokenizer 词表文件？
2. MNN 2.8 的 LLM 推理 API 属于 `MNN::Express` 还是 `MNN::LLM` 命名空间？头文件具体路径？
3. 是否需要聊天记录持久化（Room）？Demo 级别可仅内存存储。
4. 推理后端选择：CPU / OpenCL / Vulkan？默认 CPU 是否满足 Demo 需求？
