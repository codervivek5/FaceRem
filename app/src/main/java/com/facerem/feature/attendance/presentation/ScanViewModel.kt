package com.facerem.feature.attendance.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facerem.core.camera.FaceQualityStatus
import com.facerem.core.common.AppResult
import com.facerem.core.model.RecognitionOutcome
import com.facerem.core.session.SessionManager
import com.facerem.feature.attendance.domain.AttendanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanPhase { SCANNING, VERIFYING, RESULT, COOLDOWN }

data class ScanUiState(
    val phase: ScanPhase = ScanPhase.SCANNING,
    val quality: FaceQualityStatus = FaceQualityStatus.NO_FACE,
    val stableFrames: Int = 0,
    val outcome: RecognitionOutcome? = null,
    val isGuest: Boolean = false,
    val errorMessage: String? = null,
) {
    val isCameraActive: Boolean get() = phase == ScanPhase.SCANNING
}

/**
 * Drives the scanner: hold a valid face for a few frames, send one image for server-side
 * recognition, show the result, then cool down before scanning again. The cooldown mirrors the
 * backend's duplicate-scan window so the same person cannot double-punch by accident.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(ScanUiState())
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.isGuest.collect { guest -> mutableUiState.update { it.copy(isGuest = guest) } }
        }
    }

    fun onQuality(status: FaceQualityStatus) {
        val current = mutableUiState.value
        if (current.phase != ScanPhase.SCANNING) return

        if (!status.isValid) {
            mutableUiState.value = current.copy(quality = status, stableFrames = 0)
            return
        }

        val frames = current.stableFrames + 1
        if (frames < REQUIRED_STABLE_FRAMES) {
            mutableUiState.value = current.copy(quality = status, stableFrames = frames)
            return
        }

        // Enough consecutive good frames: capture once and hand the decision to the server.
        mutableUiState.value = current.copy(quality = status, stableFrames = 0, phase = ScanPhase.VERIFYING)
        recognize()
    }

    fun scanAgain() {
        mutableUiState.update {
            it.copy(phase = ScanPhase.SCANNING, outcome = null, stableFrames = 0, errorMessage = null)
        }
    }

    private fun recognize() {
        viewModelScope.launch {
            when (val result = attendanceRepository.recognize()) {
                is AppResult.Success -> {
                    mutableUiState.update { it.copy(phase = ScanPhase.RESULT, outcome = result.data) }
                    delay(RESULT_COOLDOWN_MILLIS)
                    mutableUiState.update {
                        if (it.phase == ScanPhase.RESULT) it.copy(phase = ScanPhase.COOLDOWN) else it
                    }
                }

                is AppResult.Error -> mutableUiState.update {
                    it.copy(
                        phase = ScanPhase.COOLDOWN,
                        errorMessage = result.error.userMessage,
                    )
                }

                AppResult.Loading -> Unit
            }
        }
    }

    private companion object {
        const val REQUIRED_STABLE_FRAMES = 6
        const val RESULT_COOLDOWN_MILLIS = 3_500L
    }
}
