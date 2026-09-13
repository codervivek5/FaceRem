package com.facerem.core.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.FaceDetector
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraPreview"

/**
 * Front-camera preview with a latest-frame analysis pipeline. Frames are analysed on a background
 * executor and dropped when analysis is busy, so the preview never stalls and no frame backlog
 * is retained.
 */
@Composable
fun FaceCameraPreview(
    detector: FaceDetector,
    pose: FaceQualityEvaluator.HeadPose,
    onQuality: (FaceQualityStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentPose = rememberUpdatedState(pose)
    val currentOnQuality = rememberUpdatedState(onQuality)
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null

        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(
                            executor,
                            FaceAnalyzer(
                                detector = detector,
                                poseProvider = { currentPose.value },
                                onQuality = { status -> currentOnQuality.value(status) },
                            ),
                        )
                    }

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis,
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to bind camera use cases", error)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraProvider?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
