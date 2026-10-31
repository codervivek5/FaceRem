package com.facerem.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches the access token. Endpoints that must stay anonymous opt out with [NO_AUTH]. */
@Singleton
class AuthInterceptor @Inject constructor(private val tokenStore: TokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(NO_AUTH) != null) {
            return chain.proceed(request.newBuilder().removeHeader(NO_AUTH).build())
        }

        // OkHttp interceptors are blocking; the token lives behind a Flow.
        val token = runBlocking { tokenStore.accessToken() } ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header("Authorization", "Bearer $token").build(),
        )
    }

    companion object {
        /** Marker header stripped before the request leaves. */
        const val NO_AUTH = "X-No-Auth"
    }
}
