package com.facerem.core.network

import android.util.Log
import com.facerem.core.network.api.AuthApi
import com.facerem.core.network.dto.RefreshRequestDto
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

private const val TAG = "TokenAuthenticator"

/**
 * Refreshes the access token when the server rejects one, then replays the request.
 *
 * Two things stop this from going wrong:
 *
 * - **A mutex.** Several requests usually fail at once when a token expires. Without it each
 *   would refresh separately, and because the backend rotates refresh tokens the second
 *   call would present a burnt token — which the server treats as theft and responds to by
 *   ending every session.
 * - **A retry count.** If the refreshed token is also rejected, giving up is the only safe
 *   move; retrying forever is how a refresh loop locks an app into a spin.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    // The refresh client is built without this authenticator, so asking for it lazily is
    // what breaks the circular dependency.
    @param:RefreshClient private val refreshApi: Provider<AuthApi>,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponseCount() >= MAX_RETRIES) {
            Log.w(TAG, "Giving up after $MAX_RETRIES refresh attempts")
            return null
        }

        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        return runBlocking {
            mutex.withLock {
                val current = tokenStore.accessToken()
                // Another request already refreshed while this one waited for the lock.
                if (current != null && current != failedToken) {
                    return@withLock response.request.withToken(current)
                }

                val refresh = tokenStore.refreshToken() ?: return@withLock null
                val renewed = runCatching {
                    refreshApi.get().refresh(RefreshRequestDto(refresh))
                }.getOrNull()

                val tokens = renewed?.data
                if (tokens == null) {
                    // The refresh token is dead too, so the session is genuinely over.
                    tokenStore.clear()
                    return@withLock null
                }

                tokenStore.updateTokens(tokens.accessToken, tokens.refreshToken)
                response.request.withToken(tokens.accessToken)
            }
        }
    }

    private fun Request.withToken(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    private fun Response.priorResponseCount(): Int {
        var count = 0
        var prior = priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private companion object {
        const val MAX_RETRIES = 1
    }
}
