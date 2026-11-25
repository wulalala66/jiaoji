package com.gluelab.jiaoji

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
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
        handleIncoming(intent)
        enableEdgeToEdge()
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
        setIntent(intent)
        handleIncoming(intent)
    }

    /** 从微信/文件管理器「用其他应用打开」进来：直接导入 */
    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            @Suppress("DEPRECATION")
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        val raw = uri?.let {
            runCatching {
                contentResolver.openInputStream(it)?.use { s -> s.bufferedReader().readText() }
            }.getOrNull()
        } ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.trimStart().startsWith("{") }

        if (raw.isNullOrBlank()) return
        val ok = Repo.I.importJson(raw)
        Toast.makeText(
            this,
            if (ok) "备份已导入" else "这不是胶记的备份文件",
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onResume() {
        super.onResume()
        // 回到前台时磁盘可能已经被改过（比如从别的 App 导入备份后切回来）
        Repo.I.reload()
    }
}
