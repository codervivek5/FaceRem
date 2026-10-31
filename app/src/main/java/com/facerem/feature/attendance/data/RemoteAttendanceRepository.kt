package com.facerem.feature.attendance.data

import com.facerem.core.common.AppError
import com.facerem.core.common.AppResult
import com.facerem.core.model.AttendanceRecord
import com.facerem.core.model.RecognitionOutcome
import com.facerem.core.network.ApiCall
import com.facerem.core.network.api.FaceAttendApi
import com.facerem.core.network.mapper.toOutcome
import com.facerem.core.network.mapper.toRecord
import com.facerem.feature.attendance.domain.AttendanceRepository
import com.facerem.feature.employees.data.TEXT
import com.facerem.feature.employees.data.asImagePart
import com.facerem.feature.employees.data.map
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class RemoteAttendanceRepository @Inject constructor(
    private val api: FaceAttendApi,
    private val apiCall: ApiCall,
) : AttendanceRepository {

    override suspend fun recognize(
        image: ByteArray,
        livenessScore: Float,
        deviceLabel: String,
    ): AppResult<RecognitionOutcome> {
        val result = apiCall.invoke {
            api.recognize(
                image = image.asImagePart(),
                livenessScore = livenessScore.toString().toRequestBody(TEXT),
                deviceLabel = deviceLabel.toRequestBody(TEXT),
            )
        }
        return when (result) {
            is AppResult.Success -> AppResult.Success(result.data.toOutcome())
            // An unknown face is a normal outcome at a gate, not a failure to report as one.
            is AppResult.Error -> when ((result.error as? AppError.Api)?.code) {
                "FACE_NOT_RECOGNIZED" ->
                    AppResult.Success(RecognitionOutcome.NotRecognized(result.error.userMessage))

                "LIVENESS_FAILED", "MULTIPLE_FACES", "FACE_NOT_FOUND", "FACE_QUALITY_LOW" ->
                    AppResult.Success(
                        RecognitionOutcome.Rejected(
                            code = result.error.code,
                            message = result.error.userMessage,
                        ),
                    )

                else -> result
            }

            AppResult.Loading -> AppResult.Loading
        }
    }

    override suspend fun history(): AppResult<List<AttendanceRecord>> =
        apiCall.invoke { api.attendance() }.map { rows -> rows.map { it.toRecord() } }
}
