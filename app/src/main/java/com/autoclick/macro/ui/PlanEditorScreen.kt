package com.autoclick.macro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoclick.macro.model.Plan
import com.autoclick.macro.model.PlanLimits
import com.autoclick.macro.model.ScreenCheck
import com.autoclick.macro.model.Step
import com.autoclick.macro.model.checkScreen
import com.autoclick.macro.model.estimatedDurationMs
import com.autoclick.macro.model.formatDuration
import com.autoclick.macro.overlay.currentScreenSize
import com.autoclick.macro.service.OverlayService

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(
    plan: Plan?,
    vm: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val screen = remember(config) { currentScreenSize(context) }

    var seen by remember { mutableStateOf(false) }
    if (plan != null) seen = true
    LaunchedEffect(plan == null, seen) { if (plan == null && seen) onBack() }
    if (plan == null) return

    var showRename by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Step?>(null) }
    var deleting by remember { mutableStateOf<Step?>(null) }
    var showRecalibrate by remember { mutableStateOf(false) }
    val check = plan.checkScreen(screen)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { TextButton(onClick = { showRename = true }) { Text("重命名") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SummaryCard(plan, screen, check, onRecalibrate = { showRecalibrate = true })
            }
            item { LoopCard(plan, vm) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { startPicking(context, plan.id) },
                        modifier = Modifier.weight(1f),
                    ) { Text("取点") }
                    OutlinedButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) { Text("手动添加") }
                }
            }
            item {
                Text(
                    "“取点”会收起应用并在屏幕上显示取点面板：先切到目标界面，再点“开始取点”，点哪里就记录哪里。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (plan.steps.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text("这个方案还没有步骤。", modifier = Modifier.padding(16.dp))
                    }
                }
            }
            itemsIndexed(plan.steps, key = { _, s -> s.id }) { index, step ->
                StepCard(
                    index = index,
                    step = step,
                    isFirst = index == 0,
                    isLast = index == plan.steps.lastIndex,
                    onUp = { vm.moveStep(plan.id, step.id, -1) },
                    onDown = { vm.moveStep(plan.id, step.id, 1) },
                    onEdit = { editing = step },
                    onDelete = { deleting = step },
                )
            }
        }
    }

    if (showRename) {
        NameDialog("重命名方案", plan.name, "保存", {
            vm.renamePlan(plan.id, it)
            showRename = false
        }, { showRename = false })
    }
    if (showAdd) {
        StepDialog(null, screen, onConfirm = { x, y, d, h ->
            vm.addManualStep(plan.id, Step(x = x, y = y, delayMs = d, holdMs = h), screen)
            showAdd = false
        }, onDismiss = { showAdd = false })
    }
    editing?.let { step ->
        StepDialog(step, plan.screen ?: screen, onConfirm = { x, y, d, h ->
            vm.updateStep(plan.id, step.id, x, y, d, h)
            editing = null
        }, onDismiss = { editing = null })
    }
    deleting?.let { step ->
        ConfirmDialog(
            "删除步骤",
            "确定删除步骤 ${plan.steps.indexOfFirst { it.id == step.id } + 1}（${step.x}, ${step.y}）吗？",
            "删除",
            { vm.removeStep(plan.id, step.id); deleting = null },
            { deleting = null },
        )
    }
    if (showRecalibrate) {
        ConfirmDialog(
            "按当前屏幕校准",
            "只会把方案记录的屏幕尺寸改为当前的 ${screen.describe()}，不会改变坐标本身。" +
                "仅当你确认这些坐标在当前屏幕上仍然正确时才使用。",
            "校准",
            { vm.recalibrate(plan.id, screen); showRecalibrate = false },
            { showRecalibrate = false },
        )
    }
}

private fun startPicking(context: Context, planId: String) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_LONG).show()
        context.openOverlaySettings()
        return
    }
    OverlayService.startPicking(context, planId)
    context.findActivity()?.moveTaskToBack(true)
}

@Composable
private fun SummaryCard(plan: Plan, screen: com.autoclick.macro.model.ScreenSize, check: ScreenCheck, onRecalibrate: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${plan.steps.size} 个步骤", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val duration = plan.estimatedDurationMs()
            Text(
                "预计耗时：" + (duration?.let { formatDuration(it) } ?: "持续运行，直到手动停止"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "记录屏幕尺寸：" + (plan.screen?.describe() ?: "尚未记录（首次取点时记录）") + "　当前：${screen.describe()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (check is ScreenCheck.Mismatch) {
                Text(check.message(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "在尺寸不一致时，悬浮条会拒绝执行此方案，取点也会被拒绝。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!check.rotated) {
                    OutlinedButton(onClick = onRecalibrate) { Text("按当前屏幕校准") }
                }
            }
        }
    }
}

@Composable
private fun LoopCard(plan: Plan, vm: MainViewModel) {
    var text by remember(plan.id) { mutableStateOf(if (plan.isInfinite) "1" else plan.loopCount.toString()) }
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("循环", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { raw ->
                        val digits = raw.filter(Char::isDigit).take(4)
                        text = digits
                        digits.toIntOrNull()?.takeIf { it >= 1 }?.let { vm.setLoopCount(plan.id, it) }
                    },
                    enabled = !plan.isInfinite,
                    label = { Text("循环次数（1 表示只执行一次）") },
                    isError = !plan.isInfinite && (text.toIntOrNull() ?: 0) < 1,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("无限", style = MaterialTheme.typography.labelMedium)
                    Switch(
                        checked = plan.isInfinite,
                        onCheckedChange = { infinite ->
                            if (infinite) {
                                vm.setLoopCount(plan.id, PlanLimits.LOOP_INFINITE)
                            } else {
                                vm.setLoopCount(plan.id, text.toIntOrNull()?.takeIf { it >= 1 } ?: 1)
                                if (text.toIntOrNull()?.let { it >= 1 } != true) text = "1"
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: Step,
    isFirst: Boolean,
    isLast: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f)) {
                Text("坐标 (${step.x}, ${step.y})", fontWeight = FontWeight.Medium)
                Text(
                    "点击后等待 ${step.delayMs} 毫秒" +
                        if (step.holdMs > 0) " · 长按 ${step.holdMs} 毫秒" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onUp, enabled = !isFirst) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(onClick = onDown, enabled = !isLast) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "编辑") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "删除") }
        }
    }
}
