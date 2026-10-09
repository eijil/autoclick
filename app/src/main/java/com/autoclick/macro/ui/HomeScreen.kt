package com.autoclick.macro.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.autoclick.macro.model.AppData
import com.autoclick.macro.model.Plan
import com.autoclick.macro.model.PlanLimits
import com.autoclick.macro.model.ScreenCheck
import com.autoclick.macro.model.checkScreen
import com.autoclick.macro.model.loopLabel
import com.autoclick.macro.overlay.currentScreenSize
import com.autoclick.macro.service.OverlayService

fun Context.openOverlaySettings() {
    startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun Context.openAccessibilitySettings() {
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.openAppDetailsSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    data: AppData,
    setup: SetupState,
    vm: MainViewModel,
    onOpenPlan: (String) -> Unit,
    onOpenGuide: () -> Unit,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val screen = remember(config) { currentScreenSize(context) }
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Plan?>(null) }
    var deleteTarget by remember { mutableStateOf<Plan?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏幕点击宏") },
                actions = {
                    IconButton(onClick = onOpenGuide) {
                        Icon(Icons.Filled.Info, contentDescription = "说明与风险提示")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新建方案") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SetupCard(setup, context) }
            item { BarCard(data, setup, vm, context) }
            item {
                Text("我的方案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (data.plans.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("还没有方案", fontWeight = FontWeight.Medium)
                            Text(
                                "点右下角“新建方案”，然后在方案里用“取点”依次标记要点击的位置。",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            items(data.plans, key = { it.id }) { plan ->
                PlanCard(
                    plan = plan,
                    selected = plan.id == data.selectedPlanId,
                    check = plan.checkScreen(screen),
                    onOpen = { onOpenPlan(plan.id) },
                    onSelect = { vm.selectPlan(plan.id) },
                    onRename = { renameTarget = plan },
                    onDuplicate = { vm.duplicatePlan(plan.id) },
                    onDelete = { deleteTarget = plan },
                )
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "新建方案",
            initial = data.plans.size.let { "方案 ${it + 1}" },
            confirmLabel = "创建",
            onConfirm = {
                showCreate = false
                onOpenPlan(vm.createPlan(it))
            },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { plan ->
        NameDialog(
            title = "重命名方案",
            initial = plan.name,
            confirmLabel = "保存",
            onConfirm = {
                vm.renamePlan(plan.id, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { plan ->
        ConfirmDialog(
            title = "删除方案",
            message = "确定删除「${plan.name}」及其 ${plan.steps.size} 个步骤吗？此操作不可撤销。",
            confirmLabel = "删除",
            onConfirm = {
                vm.deletePlan(plan.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun SetupCard(setup: SetupState, context: Context) {
    val ready = setup.overlayGranted && setup.accessibilityEnabled
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ready) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (ready) "授权已完成" else "还需要完成授权",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SetupRow(
                done = setup.overlayGranted,
                title = "悬浮窗权限",
                detail = "用于显示悬浮控制条和取点覆盖层",
                action = "去授权",
                onAction = { context.openOverlaySettings() },
            )
            SetupRow(
                done = setup.accessibilityEnabled,
                title = "无障碍服务",
                detail = "在“已下载的应用/已安装的服务”里找到“屏幕点击宏”并开启，仅用于派发点击",
                action = "去开启",
                onAction = { context.openAccessibilitySettings() },
            )
            if (!setup.accessibilityEnabled) {
                Text(
                    "若系统提示“受限制的设置”或开关为灰色：先到应用信息页，点右上角菜单选择“允许受限制的设置”，再回来开启。",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { context.openAppDetailsSettings() }) { Text("打开应用信息页") }
            }
        }
    }
}

@Composable
private fun SetupRow(done: Boolean, title: String, detail: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (done) "已完成" else "未完成",
            tint = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary,
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        if (!done) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun BarCard(data: AppData, setup: SetupState, vm: MainViewModel, context: Context) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("悬浮控制条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "悬浮按钮是一个圆形的“开始/停止”键，会执行下方“悬浮条当前方案”。按住拖动即可移动；要关闭请回到本应用。",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (setup.barRunning) {
                OutlinedButton(onClick = { OverlayService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("关闭悬浮条")
                }
            } else {
                Button(
                    onClick = { OverlayService.showBar(context) },
                    enabled = setup.overlayGranted,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (setup.overlayGranted) "显示悬浮条" else "请先授予悬浮窗权限") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("执行前倒计时", fontWeight = FontWeight.Medium)
                    Text("给你留出切换界面的时间", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = { vm.setCountdown(data.countdownSeconds - 1) },
                    enabled = data.countdownSeconds > 0,
                ) { Text("−") }
                Text(
                    "${data.countdownSeconds} 秒",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = { vm.setCountdown(data.countdownSeconds + 1) },
                    enabled = data.countdownSeconds < PlanLimits.MAX_COUNTDOWN_SECONDS,
                ) { Text("+") }
            }
            BarStyleSettings(data, vm)
        }
    }
}

private val BAR_COLOR_PRESETS = listOf(
    0x2E9E5B, 0x2E5BFF, 0x1F2430, 0x000000, 0xFFFFFF, 0x8E44AD, 0xF2994A, 0x00A6A6,
)

@Composable
private fun BarStyleSettings(data: AppData, vm: MainViewModel) {
    var scale by remember(data.barScalePercent) { mutableFloatStateOf(data.barScalePercent.toFloat()) }
    var opacity by remember(data.barOpacityPercent) { mutableFloatStateOf(data.barOpacityPercent.toFloat()) }
    var hex by remember(data.barColor) { mutableStateOf("%06X".format(data.barColor)) }

    HorizontalDivider()
    Text("悬浮按钮外观", fontWeight = FontWeight.Medium)

    // 预览：与悬浮按钮同样的大小/颜色/透明度（左：待机“开始”，右：执行中“停止”）。
    val startFill = Color(0xFF000000.toInt() or data.barColor)
    val stopFill = Color(0xFFD64545)
    Row(
        modifier = Modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewRunButton(running = false, fill = startFill, scale = scale / 100f, alpha = opacity / 100f)
        PreviewRunButton(running = true, fill = stopFill, scale = scale / 100f, alpha = opacity / 100f)
    }

    SliderRow(
        label = "大小",
        valueText = "${scale.roundToInt()}%",
        value = scale,
        range = PlanLimits.MIN_BAR_SCALE.toFloat()..PlanLimits.MAX_BAR_SCALE.toFloat(),
        onChange = { scale = it },
        onFinish = { vm.setBarScale(scale.roundToInt()) },
    )
    SliderRow(
        label = "透明度",
        valueText = "${opacity.roundToInt()}%",
        value = opacity,
        range = PlanLimits.MIN_BAR_OPACITY.toFloat()..PlanLimits.MAX_BAR_OPACITY.toFloat(),
        onChange = { opacity = it },
        onFinish = { vm.setBarOpacity(opacity.roundToInt()) },
    )

    Text("待机（开始）按钮颜色；执行中固定为红色", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        BAR_COLOR_PRESETS.forEach { rgb ->
            val selected = rgb == data.barColor
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF000000.toInt() or rgb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape,
                    )
                    .clickable { vm.setBarColor(rgb) },
            )
        }
    }
    OutlinedTextField(
        value = hex,
        onValueChange = { input ->
            val cleaned = input.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
            hex = cleaned
            if (cleaned.length == 6) vm.setBarColor(cleaned.toInt(16))
        },
        label = { Text("自定义颜色（十六进制 RRGGBB）") },
        prefix = { Text("#") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    onFinish: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(56.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinish,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
        Text(valueText, modifier = Modifier.width(48.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    selected: Boolean,
    check: ScreenCheck,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${plan.steps.size} 个步骤 · ${plan.loopLabel()}" +
                            (plan.screen?.let { " · 记录于 ${it.describe()}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("复制") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (check is ScreenCheck.Mismatch) {
                Text(
                    "屏幕尺寸与记录不一致（当前 ${check.current.describe()}）" + if (check.rotated) "，请旋转屏幕" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 12.dp, top = 2.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Text(
                        "悬浮条当前方案",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    TextButton(onClick = onSelect) { Text("设为悬浮条当前方案") }
                }
            }
        }
    }
}

/** 与悬浮按钮一致的预览：圆底 + 播放三角 / 停止方块。 */
@Composable
private fun PreviewRunButton(running: Boolean, fill: Color, scale: Float, alpha: Float) {
    val iconColor = if (fill.luminance() > 0.6f) Color(0xFF2A3340) else Color.White
    Canvas(Modifier.size((56 * scale).dp).alpha(alpha)) {
        val d = size.minDimension
        val c = center
        drawCircle(fill, radius = d / 2f, center = c)
        if (running) {
            val half = d * 0.17f
            drawRoundRect(
                iconColor,
                topLeft = Offset(c.x - half, c.y - half),
                size = Size(half * 2, half * 2),
                cornerRadius = CornerRadius(d * 0.04f),
            )
        } else {
            val half = d * 0.2f
            val left = c.x - half * 0.5f
            val path = Path().apply {
                moveTo(left, c.y - half)
                lineTo(left + half * 1.8f, c.y)
                lineTo(left, c.y + half)
                close()
            }
            drawPath(path, iconColor)
        }
    }
}
