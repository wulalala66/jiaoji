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
import androidx.compose.foundation.shape.CircleShape
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

/** 现在只有四个工具：比重 / 配料 / 条件表 / 设置 */
sealed interface Screen {
    data object Density : Screen
    data object Formulas : Screen
    data object Products : Screen
    data object Settings : Screen
    data object Diagnostics : Screen
    data class FormulaEditor(val formulaId: String) : Screen
    data class FormulaAi(val formulaId: String) : Screen
}

@Composable
fun Root() {
    val state by Repo.I.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Formulas) }
    val history = remember { mutableStateListOf<Screen>() }

    fun goTo(to: Screen) {
        if (screen == to) return
        history.add(screen)
        if (history.size > 16) history.removeAt(0)
        screen = to
    }

    fun goBack() {
        screen = if (history.isNotEmpty()) history.removeAt(history.size - 1) else Screen.Formulas
    }

    fun goTab(to: Screen) {
        history.clear()
        screen = to
    }

    BackHandler(enabled = history.isNotEmpty()) { goBack() }

    val showBar = screen !is Screen.FormulaAi

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (showBar) BottomBar(
                current = screen,
                onSelect = { goTab(it) },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (val s = screen) {
                is Screen.Density -> DensityScreen()
                is Screen.Formulas -> FormulasScreen(
                    state = state,
                    onOpen = { id -> goTo(Screen.FormulaEditor(id)) },
                )
                is Screen.Products -> ProductsScreen(state = state)
                is Screen.Settings -> SettingsScreen(
                    state = state,
                    onOpenDiagnostics = { goTo(Screen.Diagnostics) },
                )
                is Screen.Diagnostics -> DiagnosticsScreen(state, onBack = { goBack() })
                is Screen.FormulaEditor -> FormulaEditorScreen(
                    state = state,
                    formulaId = s.formulaId,
                    onBack = { goBack() },
                    onOpenAi = { goTo(Screen.FormulaAi(s.formulaId)) },
                )
                is Screen.FormulaAi -> FormulaAiScreen(
                    state = state,
                    formulaId = s.formulaId,
                    onBack = { goBack() },
                )
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
            TabItem(
                "🧪", "配料",
                current is Screen.Formulas || current is Screen.FormulaEditor || current is Screen.FormulaAi,
            ) { onSelect(Screen.Formulas) }
            TabItem("📋", "条件表", current is Screen.Products) { onSelect(Screen.Products) }
            TabItem("⚙️", "设置", current is Screen.Settings || current is Screen.Diagnostics) {
                onSelect(Screen.Settings)
            }
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
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
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
