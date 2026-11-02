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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
    data object Diagnostics : Screen
    data object Tools : Screen
    data object Density : Screen
    data object Products : Screen
    data object Formulas : Screen
    data class FormulaEditor(val formulaId: String) : Screen
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

    // 返回栈：返回键回到上一级，而不是一律回主页
    val history = remember { mutableStateListOf<Screen>() }

    fun goTo(to: Screen) {
        if (to == screen) return
        history.add(screen)
        if (history.size > 16) history.removeAt(0)
        screen = to
    }

    fun goBack() {
        screen = if (history.isNotEmpty()) history.removeAt(history.size - 1) else Screen.Today
    }

    // 切底部 tab = 重新起一个根页面，把返回栈清掉
    fun goTab(to: Screen) {
        history.clear()
        screen = to
    }

    // 从闹钟通知点进来时直接跳到烘箱页
    val pendingTab by MainActivity.pendingTab.collectAsState()
    LaunchedEffect(pendingTab) {
        when (pendingTab) {
            "ovens" -> { goTab(Screen.Ovens); MainActivity.pendingTab.value = null }
            "today" -> { goTab(Screen.Today); MainActivity.pendingTab.value = null }
            "records" -> { goTab(Screen.Records); MainActivity.pendingTab.value = null }
            "settings" -> { goTab(Screen.Settings); MainActivity.pendingTab.value = null }
        }
    }

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

    BackHandler(enabled = screen !is Screen.Today || history.isNotEmpty()) { goBack() }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            BottomBar(
                current = screen,
                onSelect = { goTab(it) },
                onAdd = { goTo(Screen.AddTask) },
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
                    onOpenOven = { goTo(Screen.Ovens) },
                    onSampled = { sampledTaskId = it },
                    onOpenSample = { goTo(Screen.Detail(it)) },
                    onAddTask = { goTo(Screen.AddTask) },
                )

                is Screen.Ovens -> OvensScreen(
                    state = state,
                    now = now,
                    onNewBake = { oven -> goTo(Screen.BakeForm(oven = oven)) },
                    onOpenSample = { goTo(Screen.Detail(it)) },
                )

                is Screen.Records -> RecordsScreen(
                    state = state,
                    now = now,
                    onOpenSample = { goTo(Screen.Detail(it)) },
                )

                is Screen.Settings -> SettingsScreen(
                    state = state,
                    onOpenType = { goTo(Screen.TypeEditor(it)) },
                    onOpenDiagnostics = { goTo(Screen.Diagnostics) },
                )

                is Screen.Tools -> ToolsScreen(
                    state = state,
                    onOpenRecords = { goTo(Screen.Records) },
                    onOpenDensity = { goTo(Screen.Density) },
                    onOpenProducts = { goTo(Screen.Products) },
                    onOpenFormulas = { goTo(Screen.Formulas) },
                    onOpenDiagnostics = { goTo(Screen.Diagnostics) },
                )

                is Screen.Density -> DensityScreen(onBack = { goBack() })

                is Screen.Products -> ProductsScreen(state = state, onBack = { goBack() })

                is Screen.Formulas -> FormulasScreen(
                    state = state,
                    onOpen = { goTo(Screen.FormulaEditor(it)) },
                    onBack = { goBack() },
                )

                is Screen.FormulaEditor -> FormulaEditorScreen(
                    state = state,
                    formulaId = s.formulaId,
                    onBack = { goBack() },
                )

                is Screen.Detail -> DetailScreen(
                    state = state,
                    now = now,
                    sampleId = s.sampleId,
                    onBack = { goBack() },
                    onBake = {
                        val free = state.freeOvens().firstOrNull()?.id
                            ?: state.ovens.firstOrNull()?.id ?: "door"
                        goTo(Screen.BakeForm(sampleId = s.sampleId, oven = free))
                    },
                )

                is Screen.AddTask -> AddTaskScreen(
                    state = state,
                    onBack = { goBack() },
                    onSaved = { goBack() },
                )

                is Screen.BakeForm -> BakeFormScreen(
                    state = state,
                    bakeId = s.bakeId,
                    sampleId = s.sampleId,
                    defaultOven = s.oven,
                    onBack = { goBack() },
                    onSaved = { goBack() },
                )

                is Screen.TypeEditor -> TypeEditorScreen(
                    state = state,
                    typeId = s.typeId,
                    onBack = { goBack() },
                )

                is Screen.Diagnostics -> DiagnosticsScreen(
                    state = state,
                    onBack = { goBack() },
                )
            }

            val due = state.dueBakes()
            if (due.isNotEmpty()) {
                RingBanner(
                    state = state,
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
                    onOpen = { goTo(Screen.Ovens) },
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
                    if (id != null) goTo(Screen.Detail(id))
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
                Surface(
                    onClick = onAdd,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    color = Brand,
                    shadowElevation = 3.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "新建",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            TabItem("工具", Icons.Filled.Build, current is Screen.Tools) { onSelect(Screen.Tools) }
            TabItem("设置", Icons.Filled.Settings, current is Screen.Settings) { onSelect(Screen.Settings) }
        }
    }
}

@Composable
private fun RowScope.TabItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
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
}

@Composable
private fun RingBanner(
    state: AppState,
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
                        "${state.ovenName(bake.oven)} · ${bake.tempC}℃ · 已超 ${Fmt.hms(now - bake.endAt)}",
                        color = Color(0xFFFFE3DC),
                        fontSize = 12.sp,
                    )
                }
                Surface(
                    onClick = onOpen,
                    shape = CircleShape,
                    color = Color(0x33FFFFFF),
                ) {
                    Text(
                        "打开",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
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
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
    ) {
        Text(
            text,
            color = fg,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
