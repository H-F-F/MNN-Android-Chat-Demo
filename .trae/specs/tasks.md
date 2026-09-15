# MNN-Android-Chat-Demo - Implementation Plan

> **状态更新(2026-09-15)**:T1-T9 全部代码已生成(见根 README「验证状态」)。
> 编译与真机验证待 Android SDK/NDK 与 MNN 三件套就位后执行;依赖放置路径见各占位目录 README。

## Task 1: 工程骨架与 Gradle 构建配置
- **Status**: `pending`
- **Priority**: high
- **Depends On**: None
- **Description**:
  - 创建 `settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties`。
  - 创建 `app/build.gradle.kts`：minSdk 26、targetSdk 34、仅 arm64-v8a、Compose、协程、Lifecycle、Room（可选）。
  - 配置 `externalNativeBuild` 指向 `app/src/main/cpp/CMakeLists.txt`，NDK 25，CMake 3.22.1。
  - 创建 `AndroidManifest.xml`（不声明 INTERNET 或声明但注释）、`MainActivity.kt`。
  - 创建 `gradle-wrapper.properties`（Gradle 8.4）。
- **Acceptance Criteria Addressed**: AC-1
- **Test Requirements**:
  - `rule` TR-1.1: `./gradlew assembleDebug` 退出码 0，apk 生成成功；证据：Gradle 输出日志。

## Task 2: 工具层（Logger、Result、Dispatcher）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - `util/Logger.kt`：统一 tag `MnnChat`，封装 d/i/w/e，支持耗时日志 `logElapsed(tag, startNanos, message)`。
  - `util/Result.kt`：`sealed class Result<out T>` 封装成功/失败。
  - `util/DispatcherProvider.kt`：提供 `Default`/`IO`/`Main` dispatcher，便于测试替换。
- **Acceptance Criteria Addressed**: AC-7
- **Test Requirements**:
  - `rule` TR-2.1: Logger 输出包含 tag `MnnChat`；证据：Logcat 输出。
  - `rule` TR-2.2: `logElapsed` 输出包含毫秒数；证据：Logcat 输出示例。

## Task 3: 模型文件管理层
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 2
- **Description**:
  - `model/ModelManager.kt`：`ensureModelExists()` 从 assets 拷贝到 `filesDir/models/qwen2-0.5b.mnn`，存在则跳过；`getModelPath()`、`getModelSize()`。
  - 懒加载：首次调用 `ensureModelExists` 才执行拷贝。
  - 拷贝异常捕获并返回 Result。
- **Acceptance Criteria Addressed**: AC-2
- **Test Requirements**:
  - `rule` TR-3.1: 首次调用后内部存储存在模型文件且大小匹配；证据：文件存在性检查 + 大小日志。
  - `rule` TR-3.2: 二次调用跳过拷贝；证据：Logcat "skip copy" 日志。

## Task 4: JNI native 层（C++ 封装 MNN 2.8）
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 1
- **Description**:
  - `cpp/CMakeLists.txt`：链接 `libMNN.so`、`libMNN_Express.so`，include MNN 头文件目录，生成 `libmnnchat.so`。
  - `cpp/mnn_inference.h` / `.cpp`：实现 `loadModel`、`generate`、`release` JNI 函数。
  - `generate` 支持回调：每生成一个 token 调用 Kotlin 回调方法。
  - native 侧 try-catch，异常通过 JNI `ThrowNew` 抛出。
  - 头文件与 so 放置说明写入注释（`cpp/mnn/include/`、`jniLibs/arm64-v8a/`）。
- **Acceptance Criteria Addressed**: AC-3, AC-5, AC-6
- **Test Requirements**:
  - `rule` TR-4.1: `loadModel` 成功后 native 句柄非空；证据：Kotlin 侧日志。
  - `rule` TR-4.2: `generate` 回调被调用至少 1 次；证据：Logcat token 回调日志。
  - `rule` TR-4.3: `release` 后再次 `generate` 抛异常或返回失败；证据：异常日志。

## Task 5: Kotlin 推理引擎层封装
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 2, Task 4
- **Description**:
  - `inference/MnnEngine.kt`：`init { System.loadLibrary("mnnchat") }`，external 方法 `nativeLoadModel/nativeGenerate/nativeRelease`。
  - 对外 API：`loadModel(path): Result<Unit>`、`generate(prompt, onToken: (String) -> Unit): Result<String>`、`release()`。
  - `generate` 在 `Dispatchers.Default` 执行，通过回调逐 token 推送，最终返回完整文本。
  - 防止重复加载，已加载则复用。
  - 所有异常捕获，转为 `Result.failure`。
- **Acceptance Criteria Addressed**: AC-3, AC-4, AC-6
- **Test Requirements**:
  - `rule` TR-5.1: `loadModel` 在后台线程执行，主线程不阻塞；证据：线程名日志。
  - `rule` TR-5.2: `generate` 回调在主线程或可切换；证据：回调线程日志。
  - `rule` TR-5.3: 重复 `loadModel` 不重复创建 native 会话；证据：日志仅一次 "session created"。

## Task 6: ChatViewModel 与状态管理
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 5
- **Description**:
  - `ui/ChatUiState.kt`：`data class` 包含 `messages: List<Message>`、`input: String`、`isGenerating: Boolean`、`error: String?`。
  - `data/Message.kt`：`id, role(user/assistant), content, timestamp`。
  - `ui/ChatViewModel.kt`：持有 `_uiState = MutableStateFlow`，`errorChannel = Channel()`。
  - `sendMessage()`：校验非空 → 追加 user 消息 → 调用 `modelManager.ensureModelExists` → `engine.loadModel` → `engine.generate`，逐 token 更新 assistant 消息。
  - `onCleared` 调用 `engine.release()`。
- **Acceptance Criteria Addressed**: AC-3, AC-4, AC-5, AC-6
- **Test Requirements**:
  - `rule` TR-6.1: 输入为空时 `sendMessage` 不触发推理；证据：isGenerating 仍为 false。
  - `rule` TR-6.2: 推理中 isGenerating 为 true，完成后为 false；证据：StateFlow 状态变化日志。
  - `rule` TR-6.3: onCleared 调用 release；证据："MnnEngine released" 日志。

## Task 7: Compose 聊天 UI
- **Status**: `pending`
- **Priority**: high
- **Depends On**: Task 6
- **Description**:
  - `ui/ChatScreen.kt`：`LazyColumn` 消息列表、底部 `Row(TextField + Button)`。
  - `ui/MessageItem.kt`：user 右对齐气泡、assistant 左对齐气泡。
  - `ui/theme/`：Compose Material3 主题。
  - assistant 消息流式渲染：内容为 StateFlow 中最新值，逐 token 自动重组。
  - 推理中输入框与按钮 disabled，显示加载指示器。
  - 错误通过 `LaunchedEffect` collect errorChannel 显示 Snackbar。
- **Acceptance Criteria Addressed**: AC-3, AC-4, AC-6
- **Test Requirements**:
  - `rule` TR-7.1: 发送后 user 气泡立即出现；证据：UI 交互截图。
  - `rule` TR-7.2: assistant 气泡逐 token 增长；证据：UI 交互录屏/截图序列。
  - `rule` TR-7.3: 推理中按钮 disabled；证据：UI 状态截图。

## Task 8: 耗时日志与端到端验证
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 7
- **Description**:
  - 在 `MnnEngine.loadModel`、`generate`、`ModelManager.ensureModelExists` 中插入 `Logger.logElapsed`。
  - 记录：模型拷贝耗时、模型加载耗时、首 token 耗时、整体推理耗时、token 总数。
  - 手动端到端验证：安装 apk → 发送 prompt → 检查日志与 UI。
- **Acceptance Criteria Addressed**: AC-7
- **Test Requirements**:
  - `rule` TR-8.1: 一次完整推理后 Logcat 包含至少 4 项耗时日志；证据：Logcat 输出。

## Task 9: 异常场景加固
- **Status**: `pending`
- **Priority**: medium
- **Depends On**: Task 8
- **Description**:
  - 模型文件缺失时 `ensureModelExists` 返回失败，UI 提示。
  - native loadModel 失败时捕获异常，UI 提示。
  - 推理中途异常时已生成 token 保留，UI 提示错误。
  - 页面旋转/重建时 ViewModel 保留状态（不重复加载模型）。
- **Acceptance Criteria Addressed**: AC-6
- **Test Requirements**:
  - `rule` TR-9.1: 删除模型文件后触发加载，应用不崩溃且显示错误；证据：不 crash + Snackbar。
  - `rule` TR-9.2: 推理中模拟异常，已生成内容保留；证据：UI 显示部分内容 + 错误提示。

## 目录树（实现后预期）

```
MNN-Android-Chat-Demo/
├── .trae/specs/
│   ├── spec.md
│   └── tasks.md
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/mnnchat/
│       │   ├── MainActivity.kt
│       │   ├── ui/
│       │   │   ├── ChatScreen.kt
│       │   │   ├── ChatViewModel.kt
│       │   │   ├── ChatUiState.kt
│       │   │   ├── MessageItem.kt
│       │   │   └── theme/
│       │   │       ├── Color.kt
│       │   │       ├── Theme.kt
│       │   │       └── Type.kt
│       │   ├── inference/
│       │   │   └── MnnEngine.kt
│       │   ├── model/
│       │   │   └── ModelManager.kt
│       │   ├── data/
│       │   │   └── Message.kt
│       │   └── util/
│       │       ├── Logger.kt
│       │       ├── Result.kt
│       │       └── DispatcherProvider.kt
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── mnn_inference.cpp
│       │   ├── mnn_inference.h
│       │   └── mnn/
│       │       └── include/        # MNN 头文件（用户放入）
│       ├── assets/
│       │   └── models/
│       │       └── qwen2-0.5b.mnn  # 模型文件（用户放入）
│       └── jniLibs/
│           └── arm64-v8a/
│               ├── libMNN.so        # 用户放入
│               └── libMNN_Express.so # 用户放入
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/
    └── gradle-wrapper.properties
```

## 依赖清单

| 依赖 | 版本 | 用途 |
|------|------|------|
| AGP | 8.1.4 | Android Gradle Plugin |
| Kotlin | 1.9.22 | 语言 |
| Gradle | 8.4 | 构建 |
| Compose BOM | 2023.10.01 | UI 框架 |
| Lifecycle ViewModel Compose | 2.7.0 | 状态管理 |
| Kotlin Coroutines | 1.7.3 | 异步 |
| Room (可选) | 2.6.1 | 持久化 |
| NDK | 25.2.9519653 | native 编译 |
| CMake | 3.22.1 | native 构建 |
| MNN | 2.8 | LLM 推理引擎（外部 so） |
