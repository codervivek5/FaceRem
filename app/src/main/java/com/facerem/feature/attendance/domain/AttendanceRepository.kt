package com.facerem.feature.attendance.domain

import com.facerem.core.common.AppResult
import com.facerem.core.model.AttendanceRecord
import com.facerem.core.model.RecognitionOutcome

interface AttendanceRepository {
    /**
     * Maps to `POST /api/v1/attendance/recognize`. Identity is decided by the server against the
     * caller's organization only; the client never matches embeddings itself.
     */
    suspend fun recognize(): AppResult<RecognitionOutcome>

    suspend fun history(): AppResult<List<AttendanceRecord>>
}
