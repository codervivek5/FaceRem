package com.facerem.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.face.FaceDetector
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FaceDetectorEntryPoint {
    fun faceDetector(): FaceDetector
}

/**
 * The shared ML Kit detector, reached from composables. It is a singleton because creating a
 * detector per screen leaks native resources.
 */
@Composable
fun rememberFaceDetector(): FaceDetector {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            FaceDetectorEntryPoint::class.java,
        ).faceDetector()
    }
}
