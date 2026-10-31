package com.facerem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facerem.app.FaceAttendApp
import com.facerem.core.datastore.ThemeMode
import com.facerem.ui.theme.FaceAttendTheme
import com.facerem.ui.theme.GlassBackdrop
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash covers the cold start; the in-app splash takes over from there.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                // Glass is a light treatment; its own scheme overrides this anyway.
                ThemeMode.LIQUID_GLASS -> false
            }
            FaceAttendTheme(darkTheme = darkTheme, glass = themeMode.isGlass) {
                // Paints the colour wash the translucent panels sit on.
                GlassBackdrop {
                    FaceAttendApp()
                }
            }
        }
    }
}
