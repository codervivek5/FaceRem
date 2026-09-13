package com.facerem.core.camera

import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FaceDetectorModule {
    /**
     * Fast mode with classification so eye-open and smile probabilities are available for
     * quality checks and, later, challenge-based liveness. Contours are intentionally off:
     * they cost frame time and the backend does the alignment.
     */
    @Provides
    @Singleton
    fun provideFaceDetector(): FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(0.2f)
            .enableTracking()
            .build(),
    )
}
