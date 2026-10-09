package com.autoclick.macro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    firstRun: Boolean,
    onAccept: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "使用前必读" else "说明与风险提示") },
                navigationIcon = {
                    if (!firstRun) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (firstRun) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) { Text("我已阅读并理解风险，继续") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("这个应用做什么") {
                Text(
                    "屏幕点击宏可以让你在屏幕上标记多个固定坐标的点击点，把它们按顺序编排成“方案”，再通过悬浮控制条一键执行。" +
                        "典型场景：在固定界面里依次点击若干固定位置（例如先点“卖出”再点“买入”）。",
                )
            }
            Section("它是怎么工作的") {
                Bullets(
                    "通过 Android 无障碍服务的手势派发接口（dispatchGesture）模拟触摸点击，不需要 root。",
                    "通过悬浮窗显示控制条与取点覆盖层。",
                    "不读取屏幕内容，不注入、不修改任何应用的内存或文件，不检测、也不规避任何游戏的反作弊机制。",
                    "所有方案只保存在本机，应用不联网、不上传数据。",
                )
            }
            Section("需要的权限") {
                Bullets(
                    "悬浮窗权限（显示在其他应用上层）：用于显示悬浮控制条和取点覆盖层。",
                    "无障碍服务：仅用于派发点击手势。你可以随时在系统设置中关闭。",
                    "通知：悬浮条以前台服务运行，会显示一条常驻通知，可从通知中关闭悬浮条。",
                )
            }
            Section("风险提示", warning = true) {
                Text(
                    "在联网游戏或其他在线服务中使用自动化/连点工具，可能违反游戏用户协议，并可能导致账号警告、封禁等处罚。" +
                        "是否使用、在何处使用，由你自行判断并自行承担全部后果；本应用及其开发者不对由此产生的任何损失负责。",
                    fontWeight = FontWeight.Medium,
                )
                Text("请勿在你无权自动化的服务中使用，也不要用它做损害他人利益的事情。")
            }
            Section("使用提示") {
                Bullets(
                    "坐标是屏幕绝对像素，并记录创建时的屏幕尺寸。横竖屏或设备不同会导致位置偏移，应用会在尺寸不一致时提示。",
                    "悬浮按钮请拖到不会遮挡点击位置的地方；它是一个圆形的“开始/停止”键（▶ 开始，红色 ■ 停止），大小、透明度和颜色可在首页“悬浮按钮外观”调整。",
                    "手势执行期间如果你自己触摸屏幕，手势可能被取消并导致方案中止。",
                    "Android 13 及以上通过安装包（非应用商店）安装时，系统可能提示“受限制的设置”：请到系统“应用信息”页，" +
                        "点右上角菜单选择“允许受限制的设置”，再开启无障碍服务。",
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, warning: Boolean = false, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Bullets(vararg items: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { Text("•  $it") }
    }
}
