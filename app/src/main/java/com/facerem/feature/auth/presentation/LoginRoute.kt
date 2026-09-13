package com.facerem.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facerem.core.designsystem.component.AppTextField
import com.facerem.core.designsystem.component.BrandMark
import com.facerem.core.designsystem.component.InlineMessage
import com.facerem.core.designsystem.component.PasswordField
import com.facerem.core.designsystem.component.PrimaryButton
import com.facerem.core.designsystem.component.SecondaryButton
import com.facerem.ui.theme.Spacing

@Composable
fun LoginRoute(
    onAuthenticated: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.authenticated) {
        if (uiState.authenticated) onAuthenticated()
    }

    LoginScreen(
        uiState = uiState,
        onOrganizationCodeChange = viewModel::onOrganizationCodeChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onRememberMeChange = viewModel::onRememberMeChange,
        onSignIn = viewModel::signIn,
        onGuestMode = viewModel::continueAsGuest,
        onUseDemoCredentials = viewModel::prefillDemoCredentials,
        onForgotPassword = onForgotPassword,
    )
}

@Composable
private fun LoginScreen(
    uiState: LoginUiState,
    onOrganizationCodeChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberMeChange: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onGuestMode: () -> Unit,
    onUseDemoCredentials: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenHorizontal),
        ) {
            Spacer(Modifier.height(Spacing.xxl))
            BrandMark(size = 72.dp)
            Spacer(Modifier.height(Spacing.xl))
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Sign in to your organization's attendance workspace.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.xl))

            AppTextField(
                value = uiState.organizationCode,
                onValueChange = onOrganizationCodeChange,
                label = "Organization code (optional)",
                supportingText = "Leave empty if you sign in with a single-tenant account",
                leadingIcon = Icons.Default.Apartment,
                enabled = !uiState.isSubmitting,
            )
            Spacer(Modifier.height(Spacing.md))
            AppTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = "Work email",
                errorText = uiState.emailError,
                leadingIcon = Icons.Default.MailOutline,
                keyboardType = KeyboardType.Email,
                enabled = !uiState.isSubmitting,
            )
            Spacer(Modifier.height(Spacing.md))
            PasswordField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                errorText = uiState.passwordError,
                enabled = !uiState.isSubmitting,
                imeAction = ImeAction.Done,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = uiState.rememberMe, onCheckedChange = onRememberMeChange)
                    Text("Keep me signed in", style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = onForgotPassword) { Text("Forgot?") }
            }

            InlineMessage(text = uiState.formError)

            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = "Sign in",
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canSubmit,
                loading = uiState.isSubmitting,
            )

            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "  or  ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.lg))

            SecondaryButton(
                text = if (uiState.isGuestSubmitting) "Preparing demo…" else "Continue as guest",
                onClick = onGuestMode,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSubmitting && !uiState.isGuestSubmitting,
                leadingIcon = Icons.Default.PersonOutline,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "Guest mode opens a sample organization on this device. Nothing is uploaded and no " +
                    "real attendance is recorded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = onUseDemoCredentials) { Text("Fill demo admin credentials") }
            }

            Spacer(Modifier.height(Spacing.xl))
            Text(
                "By continuing you agree to the organization's biometric data policy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xxl))
            Spacer(Modifier.width(Spacing.xs))
        }
    }
}
