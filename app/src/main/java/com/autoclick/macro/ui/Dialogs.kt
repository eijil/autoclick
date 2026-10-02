package com.autoclick.macro.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoclick.macro.model.PlanLimits
import com.autoclick.macro.model.ScreenSize
import com.autoclick.macro.model.Step

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(PlanLimits.MAX_NAME_LENGTH) },
                label = { Text("方案名称") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 编辑或手动新增步骤。[step] 为 null 表示新增。 */
@Composable
fun StepDialog(
    step: Step?,
    screen: ScreenSize,
    onConfirm: (x: Int, y: Int, delayMs: Long, holdMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var x by remember { mutableStateOf((step?.x ?: screen.width / 2).toString()) }
    var y by remember { mutableStateOf((step?.y ?: screen.height / 2).toString()) }
    var delay by remember { mutableStateOf((step?.delayMs ?: PlanLimits.DEFAULT_DELAY_MS).toString()) }
    var hold by remember { mutableStateOf((step?.holdMs ?: 0L).toString()) }

    val xv = x.toIntOrNull()?.takeIf { it >= 0 }
    val yv = y.toIntOrNull()?.takeIf { it >= 0 }
    val dv = delay.toLongOrNull()?.takeIf { it in 0..PlanLimits.MAX_DELAY_MS }
    val hv = hold.toLongOrNull()?.takeIf { it in 0..PlanLimits.MAX_HOLD_MS }
    val outOfScreen = (xv != null && xv >= screen.width) || (yv != null && yv >= screen.height)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step == null) "手动添加步骤" else "编辑步骤") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("X 坐标", x, xv == null, Modifier.weight(1f)) { x = it }
                    NumberField("Y 坐标", y, yv == null, Modifier.weight(1f)) { y = it }
                }
                NumberField("点击后延迟（毫秒）", delay, dv == null, Modifier.fillMaxWidth()) { delay = it }
                NumberField("长按时长（毫秒，0 为普通点击）", hold, hv == null, Modifier.fillMaxWidth()) { hold = it }
                Text(
                    "坐标为屏幕绝对像素（当前屏幕 ${screen.describe()}）。延迟范围 0–${PlanLimits.MAX_DELAY_MS}，长按范围 0–${PlanLimits.MAX_HOLD_MS}。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (outOfScreen) {
                    Text(
                        "坐标超出当前屏幕范围",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = xv != null && yv != null && dv != null && hv != null,
                onClick = { onConfirm(xv!!, yv!!, dv!!, hv!!) },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    isError: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(9)) },
        label = { Text(label, maxLines = 1) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
