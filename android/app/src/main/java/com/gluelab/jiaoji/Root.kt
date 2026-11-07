package com.gluelab.jiaoji

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 三个工具：比重 / 条件表 / 设置 */
sealed interface Screen {
    data object Density : Screen
    data object Products : Screen
    data object Settings : Screen
}

@Composable
fun Root() {
    val state by Repo.I.state.collectAsState()
    // 打开 App 先看比重计算器：这是每天用得最多的一屏
    var screen by remember { mutableStateOf<Screen>(Screen.Density) }

    Scaffold(
        containerColor = Bg,
        bottomBar = { BottomBar(current = screen, onSelect = { screen = it }) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                is Screen.Density -> DensityScreen()
                is Screen.Products -> ProductsScreen(state = state)
                is Screen.Settings -> SettingsScreen(state = state)
            }
        }
    }
}

@Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    Surface(color = CardBg, shadowElevation = 10.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem("⚖️", "比重", current is Screen.Density) { onSelect(Screen.Density) }
            TabItem("📋", "条件表", current is Screen.Products) { onSelect(Screen.Products) }
            TabItem("⚙️", "设置", current is Screen.Settings) { onSelect(Screen.Settings) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    emoji: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        color = Color.Transparent,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(emoji, fontSize = 15.sp)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = if (active) Brand else Ink3,
            )
        }
    }
}
