package com.wulalala66.jiaoji

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "tab"

        /** 点闹钟通知进来时，把要跳的页面广播给 Compose（ovens / today） */
        val pendingTab = MutableStateFlow<String?>(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.I.init(applicationContext)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            JiaoJiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    Root()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val tab = intent?.getStringExtra(EXTRA_TAB) ?: return
        pendingTab.value = tab
    }

    override fun onResume() {
        super.onResume()
        // 从通知/闹钟回来时，后台可能改过数据
        Repo.I.reload()
    }
}
