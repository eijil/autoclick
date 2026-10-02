package com.autoclick.macro.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoclick.macro.service.ClickAccessibilityService
import com.autoclick.macro.service.OverlayService

data class SetupState(
    val overlayGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val barRunning: Boolean,
)

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ClickAccessibilityService::class.java)
    val enabledSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabledSetting)
    while (splitter.hasNext()) {
        if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
    }
    return false
}

/** 每次回到前台时重新读取权限状态（用户从系统设置页返回后刷新）。 */
@Composable
fun rememberSetupState(): SetupState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val connected by ClickAccessibilityService.connected.collectAsStateWithLifecycle()
    val barRunning by OverlayService.running.collectAsStateWithLifecycle()
    val overlayGranted = remember(refresh) { Settings.canDrawOverlays(context) }
    val accessibilityEnabled = remember(refresh, connected) { connected || isAccessibilityServiceEnabled(context) }
    return SetupState(overlayGranted, accessibilityEnabled, barRunning)
}
