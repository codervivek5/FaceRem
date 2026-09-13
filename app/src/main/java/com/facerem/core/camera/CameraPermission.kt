package com.facerem.core.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class CameraPermissionState(
    val isGranted: Boolean,
    val hasBeenRequested: Boolean,
    val request: () -> Unit,
)

/**
 * Minimal camera-permission holder. The user is always asked in context, immediately before a
 * camera screen needs the hardware.
 */
@Composable
fun rememberCameraPermissionState(): State<CameraPermissionState> {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var requested by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        requested = true
    }

    val state = remember(granted, requested) {
        mutableStateOf(
            CameraPermissionState(
                isGranted = granted,
                hasBeenRequested = requested,
                request = { launcher.launch(Manifest.permission.CAMERA) },
            ),
        )
    }
    return state
}
