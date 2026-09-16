package com.mnn.chatdemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 聊天主界面:消息列表 + 底部输入栏。
 * 消息列表由 StateFlow 驱动,assistant 消息随逐 token 回调流式重组;
 * 推理期间输入框/发送按钮禁用并显示加载指示器。
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 错误事件 -> Snackbar
    LaunchedEffect(Unit) {
        viewModel.errorChannel.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // 新消息或流式增长时自动滚到底部
    val messageCount = state.messages.size
    val lastContentLength = state.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messageCount, lastContentLength) {
        if (messageCount > 0) {
            listState.animateScrollToItem(messageCount - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            ChatHeader(state.isGenerating)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageItem(message)
                }
            }
            InputBar(
                input = state.input,
                isGenerating = state.isGenerating,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::sendMessage
            )
        }
    }
}

@Composable
private fun ChatHeader(isGenerating: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("MNN Chat Demo", style = MaterialTheme.typography.titleLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                "Qwen2-0.5B · 端侧离线推理",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isGenerating) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 2.dp).size(12.dp),
                    strokeWidth = 2.dp
                )
                Text("生成中…", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    isGenerating: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            enabled = !isGenerating,
            placeholder = { Text("输入消息(模型在本地运行,无网络请求)") },
            modifier = Modifier.weight(1f),
            maxLines = 4
        )
        Button(
            onClick = onSend,
            enabled = input.isNotBlank() && !isGenerating
        ) {
            Text("发送")
        }
    }
}
