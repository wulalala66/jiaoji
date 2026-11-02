package com.wulalala66.jiaoji

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 全屏响铃界面：锁屏也能亮屏显示，App 在后台/被杀时由闹钟直接拉起。
 */
class RingActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_KEY = "key"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_KIND = "kind"

        fun intent(ctx: Context, key: String, title: String, body: String, kind: String): Intent =
            Intent(ctx, RingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra(EXTRA_KEY, key)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
                putExtra(EXTRA_KIND, kind)
            }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 优先靠 showWhenLocked + turnScreenOn 直接盖在锁屏上面（不给解锁弹窗）；
        // 如果 ROM 无视它，1.2 秒后会用 requestDismissKeyguard 兜底（见下面的 postDelayed）。
        try {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        } catch (_: Throwable) {
        }

        val key = intent.getStringExtra(EXTRA_KEY) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "提醒"
        val body = intent.getStringExtra(EXTRA_BODY) ?: ""
        val kind = intent.getStringExtra(EXTRA_KIND) ?: "due"
        AlarmLog.add(this, "响铃界面打开 $key")

        // 界面起来了但服务没起来（极少数机型会拦前台服务）→ 由 Activity 再兜一次
        try {
            startForegroundService(RingService.intent(this, key, title, body, kind))
        } catch (_: Throwable) {
        }

        setContent {
            JiaoJiTheme {
                RingScreen(
                    title = title,
                    body = body,
                    kind = kind,
                    onDone = {
                        RingActions.done(this, key)
                        finishAndRemoveTask()
                    },
                    onSnooze = {
                        RingActions.snooze(this, key, 2)
                        finishAndRemoveTask()
                    },
                    onSilence = {
                        RingActions.stopRinging(this)
                        finishAndRemoveTask()
                    }
                )
            }
        }

        // 兜底：有些国产 ROM 没给「锁屏显示」权限时会直接无视 showWhenLocked ——
        // 页面其实已经起来了，但被锁屏盖住，用户只听到声音看不到界面。
        // 1.2 秒还没拿到窗口焦点，就请求解锁把响铃页顶出来（有密码的话系统会先要解锁，
        // 这是系统行为，去设置里把「锁屏显示」打开就不会再出现）。
        handler.postDelayed({
            if (!isFinishing && !isDestroyed && !hasWindowFocus()) {
                try {
                    AlarmLog.add(this, "锁屏挡住了响铃页，请求解锁")
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    km.requestDismissKeyguard(this, null)
                } catch (_: Throwable) {
                }
            }
        }, 1200L)

        // 10 分钟没人处理就自己停，避免一直响
        handler.postDelayed({
            RingActions.stopRinging(this)
            finishAndRemoveTask()
        }, 10 * 60_000L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

@Composable
private fun RingScreen(
    title: String,
    body: String,
    kind: String,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onSilence: () -> Unit
) {
    var pulse by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(700)
            pulse += 1
        }
    }
    val isDue = kind == "due"
    val accent = if (isDue) Brand else Warn

    Surface(color = ColorRingBg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(if (pulse % 2 == 0L) 96.dp else 104.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(accent, if (isDue) BrandDark else Warn)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                title,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                fontSize = 17.sp,
                color = Ink2,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isDue) "响铃中 · 处理完请点下面的按钮" else "提前提醒 · 不用处理可以关掉",
                fontSize = 13.sp,
                color = Ink3,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.weight(1f))
            if (isDue) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand)
                ) {
                    Text("已取出 / 已处理", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("延后 2 分钟", fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Brand)
                ) {
                    Text("知道了", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSilence) {
                Text("只关掉声音", fontSize = 14.sp, color = Ink3)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private val ColorRingBg = androidx.compose.ui.graphics.Color(0xFFF4F6F3)
