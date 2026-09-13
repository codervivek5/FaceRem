package com.facerem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facerem.core.datastore.AppPreferences
import com.facerem.core.datastore.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: AppPreferences,
) : ViewModel() {
    val themeMode = preferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThemeMode.SYSTEM,
    )
}
