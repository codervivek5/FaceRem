package com.facerem.core.network.api

import com.facerem.core.network.AuthInterceptor
import com.facerem.core.network.dto.ApiEnvelope
import com.facerem.core.network.dto.LoginRequestDto
import com.facerem.core.network.dto.MeDto
import com.facerem.core.network.dto.RefreshRequestDto
import com.facerem.core.network.dto.TokenPairDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(
        @Body body: LoginRequestDto,
        @Header(AuthInterceptor.NO_AUTH) anonymous: String = "1",
    ): ApiEnvelope<TokenPairDto>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(
        @Body body: RefreshRequestDto,
        @Header(AuthInterceptor.NO_AUTH) anonymous: String = "1",
    ): ApiEnvelope<TokenPairDto>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: RefreshRequestDto): ApiEnvelope<Unit>

    @GET("api/v1/auth/me")
    suspend fun me(): ApiEnvelope<MeDto>
}
