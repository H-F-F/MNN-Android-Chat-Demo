package com.mnn.chatdemo.data

/**
 * 单条聊天消息。
 * 按需求:对话仅保存在内存中,退出 App 即清空,不引入 Room 持久化。
 */
data class Message(
    val id: Long,
    val role: Role,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Role { USER, ASSISTANT }
}
