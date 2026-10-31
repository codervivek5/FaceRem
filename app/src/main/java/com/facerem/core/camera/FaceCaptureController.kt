package com.facerem.core.camera

import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Hands a still frame to the caller so it can be sent for recognition or enrollment.
 *
 * The frame is kept in memory and never written to disk: a face image is biometric data,
 * and a file left in cache is a copy nobody is managing.
 */
class FaceCaptureController {

    internal var imageCapture: ImageCapture? = null
    internal var executor: Executor? = null

    val isReady: Boolean get() = imageCapture != null

    /** Returns JPEG bytes, or null when the camera is not bound or the shot failed. */
    suspend fun capture(): ByteArray? {
        val capture = imageCapture ?: return null
        val runOn = executor ?: return null

        return suspendCancellableCoroutine { continuation ->
            capture.takePicture(
                runOn,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = try {
                            image.toJpegBytes()
                        } finally {
                            image.close()
                        }
                        continuation.resume(bytes)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resume(null)
                    }
                },
            )
        }
    }
}

@Composable
fun rememberFaceCaptureController(): FaceCaptureController = remember { FaceCaptureController() }

/** `takePicture` with this callback already yields a JPEG, so the buffer copies straight out. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    buffer.rewind()
    return ByteArray(buffer.remaining()).also(buffer::get)
}
