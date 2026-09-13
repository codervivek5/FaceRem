package com.facerem.feature.faceenrollment.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facerem.app.navigation.AppRoute
import com.facerem.core.camera.FaceQualityEvaluator
import com.facerem.core.camera.FaceQualityStatus
import com.facerem.core.common.AppResult
import com.facerem.core.model.Employee
import com.facerem.feature.employees.domain.EmployeeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Enrollment captures three views so the backend can build a robust embedding set. */
val ENROLLMENT_POSES = listOf(
    FaceQualityEvaluator.HeadPose.FRONT,
    FaceQualityEvaluator.HeadPose.SLIGHT_LEFT,
    FaceQualityEvaluator.HeadPose.SLIGHT_RIGHT,
)

data class FaceEnrollmentUiState(
    val employee: Employee? = null,
    val consentGiven: Boolean = false,
    val capturedPoses: Int = 0,
    val quality: FaceQualityStatus = FaceQualityStatus.NO_FACE,
    val stableFrames: Int = 0,
    val isSubmitting: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
) {
    val currentPose: FaceQualityEvaluator.HeadPose
        get() = ENROLLMENT_POSES[capturedPoses.coerceAtMost(ENROLLMENT_POSES.lastIndex)]

    val allPosesCaptured: Boolean get() = capturedPoses >= ENROLLMENT_POSES.size

    val progress: Float get() = capturedPoses.toFloat() / ENROLLMENT_POSES.size
}

@HiltViewModel
class FaceEnrollmentViewModel @Inject constructor(
    private val employeeRepository: EmployeeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val employeeId: String = checkNotNull(savedStateHandle[AppRoute.EmployeeIdArg])

    private val mutableUiState = MutableStateFlow(FaceEnrollmentUiState())
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val result = employeeRepository.employee(employeeId)
            if (result is AppResult.Success) {
                mutableUiState.update { it.copy(employee = result.data) }
            }
        }
    }

    fun onConsentChange(granted: Boolean) {
        mutableUiState.update { it.copy(consentGiven = granted) }
    }

    /**
     * A pose is accepted only after the quality verdict stays valid across consecutive frames,
     * which filters out single lucky frames during motion.
     */
    fun onQuality(status: FaceQualityStatus) {
        mutableUiState.update { state ->
            if (state.allPosesCaptured || state.isSubmitting || !state.consentGiven) {
                return@update state.copy(quality = status)
            }
            if (!status.isValid) {
                return@update state.copy(quality = status, stableFrames = 0)
            }
            val frames = state.stableFrames + 1
            if (frames >= REQUIRED_STABLE_FRAMES) {
                state.copy(quality = status, stableFrames = 0, capturedPoses = state.capturedPoses + 1)
            } else {
                state.copy(quality = status, stableFrames = frames)
            }
        }
    }

    fun retake() {
        mutableUiState.update { it.copy(capturedPoses = 0, stableFrames = 0, errorMessage = null) }
    }

    fun submit() {
        val state = mutableUiState.value
        if (!state.allPosesCaptured) return
        mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val result = employeeRepository.submitFaceEnrollment(employeeId, ENROLLMENT_POSES.size)
            when (result) {
                is AppResult.Success -> mutableUiState.update {
                    it.copy(isSubmitting = false, isComplete = true)
                }

                is AppResult.Error -> mutableUiState.update {
                    it.copy(isSubmitting = false, errorMessage = result.error.userMessage, capturedPoses = 0)
                }

                AppResult.Loading -> Unit
            }
        }
    }

    private companion object {
        const val REQUIRED_STABLE_FRAMES = 8
    }
}
