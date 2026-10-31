package com.wulalala66.jiaoji

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Today : Screen
    data object Ovens : Screen
    data object Records : Screen
    data object Settings : Screen
    data class Detail(val sampleId: String) : Screen
    data object AddTask : Screen
    data class BakeForm(val bakeId: String? = null, val sampleId: String? = null, val oven: String = "door") : Screen
    data class TypeEditor(val typeId: String? = null) : Screen
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun Root() {
    val state by Repo.I.state.collectAsState()
    val now = rememberNow()
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Today) }
    var sampledTaskId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            Repo.I.flipDueBakes()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val activity = remember(ctx) { ctx.findActivity() }
    val running = state.activeBakes().size
    LaunchedEffect(state.settings.keepAwake, running) {
        val w = activity?.window ?: return@LaunchedEffect
        if (state.settings.keepAwake && running > 0) {
            w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler(enabled = screen !is Screen.Today) { screen = Screen.Today }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            BottomBar(
                current = screen,
                onSelect = { screen = it },
                onAdd = { screen = Screen.AddTask },
            )
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            when (val s = screen) {
                is Screen.Today -> TodayScreen(
                    state = state,
                    now = now,
                    onOpenOven = { screen = Screen.Ovens },
                    onSampled = { sampledTaskId = it },
                    onOpenSample = { screen = Screen.Detail(it) },
                    onAddTask = { screen = Screen.AddTask },
                )

                is Screen.Ovens -> OvensScreen(
                    state = state,
                    now = now,
                    onNewBake = { oven -> screen = Screen.BakeForm(oven = oven) },
                    onOpenSample = { screen = Screen.Detail(it) },
                )

                is Screen.Records -> RecordsScreen(
                    state = state,
                    now = now,
                    onOpenSample = { screen = Screen.Detail(it) },
                )

                is Screen.Settings -> SettingsScreen(
                    state = state,
                    onOpenType = { screen = Screen.TypeEditor(it) },
                )

                is Screen.Detail -> DetailScreen(
                    state = state,
                    now = now,
                    sampleId = s.sampleId,
                    onBack = { screen = Screen.Today },
                    onBake = { screen = Screen.BakeForm(sampleId = s.sampleId) },
                )

                is Screen.AddTask -> AddTaskScreen(
                    state = state,
                    onBack = { screen = Screen.Today },
                    onSaved = { screen = Screen.Today },
                )

                is Screen.BakeForm -> BakeFormScreen(
                    state = state,
                    bakeId = s.bakeId,
                    sampleId = s.sampleId,
                    defaultOven = s.oven,
                    onBack = { screen = Screen.Ovens },
                    onSaved = { screen = Screen.Ovens },
                )

                is Screen.TypeEditor -> TypeEditorScreen(
                    state = state,
                    typeId = s.typeId,
                    onBack = { screen = Screen.Settings },
                )
            }

            val due = state.dueBakes()
            if (due.isNotEmpty()) {
                RingBanner(
                    bake = due.first(),
                    now = now,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    onDone = {
                        Repo.I.markBakeDone(due.first().id)
                        ctx.stopService(Intent(ctx, RingService::class.java))
                    },
                    onSnooze = {
                        Repo.I.snoozeBake(due.first().id, 2)
                        ctx.stopService(Intent(ctx, RingService::class.java))
                    },
                    onOpen = { screen = Screen.Ovens },
                )
            }
        }
    }

    sampledTaskId?.let { taskId ->
        val task = state.taskOf(taskId)
        if (task == null) {
            sampledTaskId = null
        } else {
            SampledDialog(
                state = state,
                task = task,
                onDismiss = { sampledTaskId = null },
                onConfirm = { withBake, oven, temp, minutes ->
                    val id = Repo.I.confirmSampled(taskId, withBake, oven, temp, minutes)
                    sampledTaskId = null
                    if (id != null) screen = Screen.Detail(id)
                },
            )
        }
    }
}

@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit, onAdd: () -> Unit) {
    Surface(color = CardBg, shadowElevation = 10.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("今天", Icons.Filled.DateRange, current is Screen.Today) { onSelect(Screen.Today) }
            TabItem("烘箱", Icons.Filled.Notifications, current is Screen.Ovens) { onSelect(Screen.Ovens) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Brand)
                        .clickable { onAdd() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            TabItem("记录", Icons.Filled.List, current is Screen.Records) { onSelect(Screen.Records) }
            TabItem("设置", Icons.Filled.Settings, current is Screen.Settings) { onSelect(Screen.Settings) }
        }
    }
}

@Composable
private fun RowScope.TabItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Brand else Ink3,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 10.5.sp,
            color = if (active) Brand else Ink3,
        )
    }
}

@Composable
private fun RingBanner(
    bake: Bake,
    now: Long,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Danger,
        shape = androidx.compose.material3.MaterialTheme.shapes.large,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⏰", fontSize = 20.sp)
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "该出炉了 · ${bake.label}",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                    Text(
                        "${ovenName(bake.oven)} · ${bake.tempC}℃ · 已超 ${Fmt.hms(now - bake.endAt)}",
                        color = Color(0xFFFFE3DC),
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "打开",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { onOpen() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BannerButton("已取出", Color.White, Danger) { onDone() }
                BannerButton("延后 2 分钟", Color(0x33FFFFFF), Color.White) { onSnooze() }
            }
        }
    }
}

@Composable
private fun BannerButton(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = fg, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}
