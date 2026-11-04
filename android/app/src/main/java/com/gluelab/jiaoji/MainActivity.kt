package com.gluelab.jiaoji

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
        // 回到前台时磁盘可能已经被改过（比如从别的 App 导入备份后切回来）
        Repo.I.reload()
    }
}
