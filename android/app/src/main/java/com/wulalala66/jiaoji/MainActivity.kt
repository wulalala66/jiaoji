package com.wulalala66.jiaoji

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.I.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            JiaoJiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    Root()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从通知/闹钟回来时，后台可能改过数据
        Repo.I.reload()
    }
}
