package com.mnn.chatdemo.ui

import com.mnn.chatdemo.data.Message

/**
 * 聊天界面 UI 状态。
 * 由 ChatViewModel 以 StateFlow 驱动 Compose 重组。
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null
)
