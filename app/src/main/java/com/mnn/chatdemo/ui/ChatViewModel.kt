package com.mnn.chatdemo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mnn.chatdemo.data.Message
import com.mnn.chatdemo.inference.MnnEngine
import com.mnn.chatdemo.model.ModelManager
import com.mnn.chatdemo.util.Logger
import com.mnn.chatdemo.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 聊天状态管理与推理调度层。
 *
 * 发送流程(全部在 viewModelScope 内):
 *  校验输入 → 追加 user 消息 → 模型就绪(IO 线程) → 加载模型(Default 线程)
 *  → 后台阻塞生成,逐 token 回调切回主线程更新最后一条 assistant 消息 → 完成
 *
 * 约束:
 * - 推理期间 isGenerating=true,输入框/发送按钮禁用;
 * - 模型加载成功后句柄复用,不重复创建 native 会话;
 * - onCleared 释放 native 资源(AC-5);
 * - 对话仅内存保存,退出即清空。
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 一次性错误事件(通过 Snackbar 展示) */
    private val _errorChannel = Channel<String>(Channel.BUFFERED)
    val errorChannel = _errorChannel.receiveAsFlow()

    private val engine = MnnEngine()
    private var modelHandle: Long = 0L
    private var modelLoaded = false
    private var messageId = 0L

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = Message(id = ++messageId, role = Message.Role.USER, content = text)
        _uiState.update { it.copy(messages = it.messages + userMessage, input = "", isGenerating = true) }

        viewModelScope.launch {
            try {
                val modelDir = ensureModel()
                val handle = ensureLoaded(modelDir)

                val assistantMessage = Message(id = ++messageId, role = Message.Role.ASSISTANT, content = "")
                _uiState.update { it.copy(messages = it.messages + assistantMessage) }

                val startNanos = System.nanoTime()
                Logger.logElapsed("ChatViewModel", startNanos, "开始生成 prompt=${text.take(40)}")

                // 阻塞推理放到 Default 线程;native 线程逐 token 回调,内部切回主线程更新 UI
                val fullResponse = withContext(Dispatchers.Default) {
                    engine.nativeReset(handle)
                    val repeatWindow = 24
                    var repeatHits = 0
                    val raw = StringBuilder()
                    engine.nativeGenerate(handle, text) { token ->
                        // 重复检测:尾部 24 字符与再往前 24 字符完全相同,判定为复读,连中 2 次立即停止
                        raw.append(token)
                        val len = raw.length
                        if (len > repeatWindow * 2) {
                            val tail = raw.substring(len - repeatWindow)
                            val prev = raw.substring(len - repeatWindow * 2, len - repeatWindow)
                            if (tail == prev) {
                                repeatHits++
                                if (repeatHits >= 2) {
                                    engine.nativeStop(handle)
                                }
                            } else {
                                repeatHits = 0
                            }
                        }
                        // native 回调线程 -> 主线程更新最后一条 assistant 消息
                        viewModelScope.launch {
                            _uiState.update { state ->
                                val list = state.messages.toMutableList()
                                val index = list.indexOfLast { it.id == assistantMessage.id }
                                if (index >= 0) {
                                    list[index] = list[index].copy(content = list[index].content + token)
                                }
                                state.copy(messages = list)
                            }
                        }
                    }
                    raw.toString()
                }
                Logger.logElapsed("ChatViewModel", startNanos, "生成完成 len=${fullResponse.length}")
            } catch (e: Exception) {
                Logger.e("[ChatViewModel] 推理失败", e)
                _errorChannel.send(e.message ?: "推理失败")
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    /** 模型就绪:首次从 assets 拷贝到内部存储(IO 线程),返回模型目录绝对路径 */
    private suspend fun ensureModel(): String = withContext(Dispatchers.IO) {
        when (val result = ModelManager.ensureModel(getApplication())) {
            is Result.Success -> result.data
            is Result.Failure -> throw IllegalStateException(result.message)
        }
    }

    /** 模型加载:仅首次真正调用 nativeLoadModel,之后复用句柄 */
    private suspend fun ensureLoaded(modelDir: String): Long {
        if (modelLoaded) return modelHandle
        val handle = withContext(Dispatchers.Default) { engine.nativeLoadModel(modelDir) }
        if (handle == 0L) throw IllegalStateException("模型加载失败(native 返回空句柄)")
        modelHandle = handle
        modelLoaded = true
        Logger.i("[ChatViewModel] 模型加载完成 handle=$handle")
        return handle
    }

    override fun onCleared() {
        if (modelLoaded) {
            try {
                engine.nativeRelease(modelHandle)
                Logger.i("[ChatViewModel] MnnEngine released")
            } catch (_: Throwable) {
                // 释放失败不影响进程退出
            }
        }
        super.onCleared()
    }
}
