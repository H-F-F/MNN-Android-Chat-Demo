package com.mnn.chatdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mnn.chatdemo.ui.ChatScreen
import com.mnn.chatdemo.ui.theme.MnnChatTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MnnChatTheme {
                ChatScreen()
            }
        }
    }
}
