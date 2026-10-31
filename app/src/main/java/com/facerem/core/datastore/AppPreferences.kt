package com.facerem.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "faceattend_preferences")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,

    /** Translucent surfaces over a soft colour backdrop. Always light-on-light. */
    LIQUID_GLASS,
    ;

    val isGlass: Boolean get() = this == LIQUID_GLASS
}

/**
 * Stored session snapshot. Tokens live here only so the app can restore a session on cold start;
 * they are moved to a hardware-backed keystore in the security phase and are never logged.
 */
data class StoredSession(
    val accessToken: String?,
    val userId: String?,
    val fullName: String?,
    val email: String?,
    val role: String?,
    val organizationId: String?,
    val organizationName: String?,
    val isGuest: Boolean,
) {
    val isActive: Boolean get() = userId != null && (isGuest || accessToken != null)
}

class AppPreferences(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[Keys.themeMode] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.darkTheme] == true }

    val hasCompletedOnboarding: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.onboardingCompleted] == true }

    val session: Flow<StoredSession> = context.dataStore.data.map { it.toStoredSession() }

    /** Read separately from [session] because only the refresh coordinator needs it. */
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[Keys.refreshToken] }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.darkTheme] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    suspend fun saveSession(session: StoredSession, refreshToken: String? = null) {
        context.dataStore.edit { prefs ->
            prefs.putOrRemove(Keys.accessToken, session.accessToken)
            prefs.putOrRemove(Keys.refreshToken, refreshToken)
            prefs.putOrRemove(Keys.userId, session.userId)
            prefs.putOrRemove(Keys.fullName, session.fullName)
            prefs.putOrRemove(Keys.email, session.email)
            prefs.putOrRemove(Keys.role, session.role)
            prefs.putOrRemove(Keys.organizationId, session.organizationId)
            prefs.putOrRemove(Keys.organizationName, session.organizationName)
            prefs[Keys.guestMode] = session.isGuest
        }
    }

    /** Replaces just the token pair, leaving the signed-in identity untouched. */
    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit {
            it[Keys.accessToken] = accessToken
            it[Keys.refreshToken] = refreshToken
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            listOf(
                Keys.accessToken,
                Keys.refreshToken,
                Keys.userId,
                Keys.fullName,
                Keys.email,
                Keys.role,
                Keys.organizationId,
                Keys.organizationName,
            ).forEach(prefs::remove)
            prefs.remove(Keys.guestMode)
        }
    }

    private fun Preferences.toStoredSession() = StoredSession(
        accessToken = this[Keys.accessToken],
        userId = this[Keys.userId],
        fullName = this[Keys.fullName],
        email = this[Keys.email],
        role = this[Keys.role],
        organizationId = this[Keys.organizationId],
        organizationName = this[Keys.organizationName],
        isGuest = this[Keys.guestMode] == true,
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value == null) remove(key) else set(key, value)
    }

    private object Keys {
        val darkTheme = booleanPreferencesKey("dark_theme")
        val themeMode = stringPreferencesKey("theme_mode")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val guestMode = booleanPreferencesKey("guest_mode")
        val accessToken = stringPreferencesKey("access_token")
        val refreshToken = stringPreferencesKey("refresh_token")
        val userId = stringPreferencesKey("user_id")
        val fullName = stringPreferencesKey("user_full_name")
        val email = stringPreferencesKey("user_email")
        val role = stringPreferencesKey("user_role")
        val organizationId = stringPreferencesKey("organization_id")
        val organizationName = stringPreferencesKey("organization_name")
    }
}
