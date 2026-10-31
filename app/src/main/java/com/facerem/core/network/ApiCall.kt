package com.facerem.core.network

import com.facerem.core.common.AppError
import com.facerem.core.common.AppResult
import com.facerem.core.network.dto.ApiEnvelope
import com.facerem.core.network.dto.ApiErrorBody
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Turns one backend call into an [AppResult].
 *
 * The server answers with a machine-readable code on failure, so the UI can say what went
 * wrong rather than showing a generic message. Anything unexpected still maps through
 * [ApiErrorMapper], which is what keeps a dropped connection from surfacing as a crash.
 */
@Singleton
class ApiCall @Inject constructor(
    private val json: Json,
    private val errorMapper: ApiErrorMapper,
) {
    suspend fun <T> invoke(block: suspend () -> ApiEnvelope<T>): AppResult<T> = try {
        val envelope = block()
        val data = envelope.data
        when {
            envelope.success && data != null -> AppResult.Success(data)
            envelope.error != null -> AppResult.Error(envelope.error.toAppError())
            else -> AppResult.Error(AppError.Unknown)
        }
    } catch (error: HttpException) {
        AppResult.Error(error.toAppError())
    } catch (error: Throwable) {
        AppResult.Error(errorMapper.map(error))
    }

    /** For endpoints whose body carries nothing useful. */
    suspend fun unit(block: suspend () -> ApiEnvelope<Unit>): AppResult<Unit> = try {
        val envelope = block()
        if (envelope.success) {
            AppResult.Success(Unit)
        } else {
            AppResult.Error(envelope.error?.toAppError() ?: AppError.Unknown)
        }
    } catch (error: HttpException) {
        AppResult.Error(error.toAppError())
    } catch (error: Throwable) {
        AppResult.Error(errorMapper.map(error))
    }

    /** A failed call still carries the envelope, so prefer the server's own wording. */
    private fun HttpException.toAppError(): AppError {
        val body = response()?.errorBody()?.string().orEmpty()
        val parsed = runCatching {
            json.decodeFromString<ApiEnvelope<Unit>>(body).error
        }.getOrNull()
        return parsed?.toAppError() ?: errorMapper.mapHttpStatus(code())
    }

    private fun ApiErrorBody.toAppError() = AppError.Api(code = code, userMessage = message)
}
