package com.facerem.core.network

import com.facerem.core.datastore.AppPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Synchronous-ish access to the stored tokens for OkHttp, which runs off the coroutine
 * world. Reads go through DataStore so there is still a single source of truth.
 */
@Singleton
class TokenStore @Inject constructor(private val preferences: AppPreferences) {
    suspend fun accessToken(): String? = preferences.session.first().accessToken

    suspend fun refreshToken(): String? = preferences.refreshToken.first()

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        preferences.updateTokens(accessToken, refreshToken)
    }

    suspend fun clear() = preferences.clearSession()
}
