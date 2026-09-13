package com.facerem.feature.attendance.data

import com.facerem.core.common.AppResult
import com.facerem.core.data.demo.DemoDataStore
import com.facerem.core.model.AttendanceRecord
import com.facerem.core.model.RecognitionOutcome
import com.facerem.feature.attendance.domain.AttendanceRepository
import javax.inject.Inject
import kotlinx.coroutines.delay

class AttendanceRepositoryImpl @Inject constructor(
    private val demoDataStore: DemoDataStore,
) : AttendanceRepository {

    override suspend fun recognize(): AppResult<RecognitionOutcome> {
        delay(1_400)
        return AppResult.Success(demoDataStore.simulateRecognition())
    }

    override suspend fun history(): AppResult<List<AttendanceRecord>> {
        delay(400)
        return AppResult.Success(
            demoDataStore.attendance.value.sortedWith(
                compareByDescending<AttendanceRecord> { it.date }.thenBy { it.employeeName },
            ),
        )
    }
}
