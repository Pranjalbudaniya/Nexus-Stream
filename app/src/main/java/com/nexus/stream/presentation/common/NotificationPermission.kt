package com.nexus.stream.presentation.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun rememberNotificationPermissionGate(): (proceed: () -> Unit) -> Unit {
    val context = LocalContext.current
    var askedInScreenInstance by rememberSaveable { mutableStateOf(false) }
    var pendingProceed by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { _ ->
            val proceed = pendingProceed
            pendingProceed = null
            proceed?.invoke()
        }

    return { proceed ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val isGranted =
            if (needsPermission) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        if (needsPermission && !isGranted && !askedInScreenInstance) {
            askedInScreenInstance = true
            pendingProceed = proceed
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            proceed()
        }
    }
}
