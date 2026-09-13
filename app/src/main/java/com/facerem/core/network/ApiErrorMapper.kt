package com.facerem.core.network

import com.facerem.core.common.AppError
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

class ApiErrorMapper {
    fun map(throwable: Throwable): AppError = when (throwable) {
        is HttpException -> mapHttpStatus(throwable.code())
        is UnknownHostException -> AppError.NoInternet
        is SocketTimeoutException -> AppError.Timeout
        is IOException -> AppError.NoInternet
        else -> AppError.Unknown
    }

    fun mapHttpStatus(statusCode: Int): AppError = when (statusCode) {
        400 -> AppError.Api("BAD_REQUEST", "The request could not be processed.", statusCode)
        401 -> AppError.Api("UNAUTHORIZED", "Your session has expired. Please sign in again.", statusCode)
        403 -> AppError.Api("FORBIDDEN", "You do not have permission to perform this action.", statusCode)
        404 -> AppError.Api("NOT_FOUND", "The requested item was not found.", statusCode)
        409 -> AppError.Api("CONFLICT", "This action conflicts with existing data.", statusCode)
        422 -> AppError.Api("VALIDATION_ERROR", "Please check the information and try again.", statusCode)
        429 -> AppError.Api("RATE_LIMITED", "Too many requests. Please try again shortly.", statusCode)
        in 500..599 -> AppError.Api("SERVER_ERROR", "Our server is unavailable. Please try again later.", statusCode)
        else -> AppError.Api("HTTP_ERROR", "The request could not be completed.", statusCode)
    }
}
