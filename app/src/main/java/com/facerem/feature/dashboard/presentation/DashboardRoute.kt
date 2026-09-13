package com.facerem.feature.dashboard.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.facerem.core.common.AppClock
import com.facerem.core.designsystem.component.AttendanceStatusPill
import com.facerem.core.designsystem.component.ErrorState
import com.facerem.core.designsystem.component.FullScreenLoader
import com.facerem.core.designsystem.component.GuestModeBanner
import com.facerem.core.designsystem.component.QuickActionCard
import com.facerem.core.designsystem.component.RateBar
import com.facerem.core.designsystem.component.SectionHeader
import com.facerem.core.designsystem.component.StatCard
import com.facerem.core.designsystem.component.UserAvatar
import com.facerem.core.designsystem.component.WeeklyRateChart
import com.facerem.core.model.AttendanceRecord
import com.facerem.core.model.DashboardSummary
import com.facerem.ui.theme.AppTheme
import com.facerem.ui.theme.Spacing

@Composable
fun DashboardRoute(
    onOpenScan: () -> Unit,
    onOpenEmployees: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            DashboardHeader(
                name = uiState.user?.fullName.orEmpty(),
                organization = uiState.user?.organizationName.orEmpty(),
                onOpenNotifications = onOpenNotifications,
                onOpenProfile = onOpenProfile,
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> FullScreenLoader(
                message = "Loading today's attendance…",
                modifier = Modifier.padding(padding),
            )

            uiState.summary == null -> ErrorState(
                title = "Could not load the dashboard",
                message = uiState.errorMessage ?: "Please try again.",
                onRetry = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            else -> DashboardContent(
                contentPadding = padding,
                summary = requireNotNull(uiState.summary),
                isGuest = uiState.isGuest,
                onOpenScan = onOpenScan,
                onOpenEmployees = onOpenEmployees,
                onOpenHistory = onOpenHistory,
                onOpenReports = onOpenReports,
            )
        }
    }
}

@Composable
private fun DashboardHeader(
    name: String,
    organization: String,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                greeting(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                name.ifBlank { "There" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (organization.isNotBlank()) {
                Text(
                    organization,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onOpenNotifications) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
        }
        Spacer(Modifier.width(Spacing.xs))
        IconButton(onClick = onOpenProfile) { UserAvatar(name = name, size = 40.dp) }
    }
}

@Composable
private fun DashboardContent(
    contentPadding: PaddingValues,
    summary: DashboardSummary,
    isGuest: Boolean,
    onOpenScan: () -> Unit,
    onOpenEmployees: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReports: () -> Unit,
) {
    val semantic = AppTheme.colors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(
            start = Spacing.screenHorizontal,
            end = Spacing.screenHorizontal,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (isGuest) {
            item { GuestModeBanner() }
        }

        item {
            Text(
                summary.dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatCard(
                    label = "Present today",
                    value = summary.presentCount.toString(),
                    icon = Icons.Default.HowToReg,
                    accent = semantic.success,
                    caption = "of ${summary.totalEmployees} employees",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Late arrivals",
                    value = summary.lateCount.toString(),
                    icon = Icons.Default.Schedule,
                    accent = semantic.warning,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatCard(
                    label = "Absent",
                    value = summary.absentCount.toString(),
                    icon = Icons.Default.EventBusy,
                    accent = MaterialTheme.colorScheme.error,
                    caption = "${summary.onLeaveCount} on approved leave",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Face enrollment",
                    value = summary.pendingEnrollments.toString(),
                    icon = Icons.Default.PersonAddAlt,
                    accent = MaterialTheme.colorScheme.tertiary,
                    caption = "pending or expired",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    RateBar(
                        label = "Today's attendance rate",
                        rate = summary.attendanceRate,
                        caption = "${summary.presentCount + summary.lateCount} checked in",
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    Text("Last 6 days", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(Spacing.md))
                    WeeklyRateChart(rates = summary.weeklyAttendanceRate)
                }
            }
        }

        item { SectionHeader(title = "Quick actions") }

        item {
            QuickActionCard(
                title = "Scan attendance",
                description = "Open the face scanner for check in or check out",
                icon = Icons.Default.CameraAlt,
                accent = MaterialTheme.colorScheme.primary,
                onClick = onOpenScan,
            )
        }
        item {
            QuickActionCard(
                title = "Manage people",
                description = "Add employees and enroll their face profiles",
                icon = Icons.Default.Groups,
                accent = semantic.info,
                onClick = onOpenEmployees,
            )
        }
        item {
            QuickActionCard(
                title = "View reports",
                description = "Attendance rate, overtime and department breakdown",
                icon = Icons.Default.InsertChart,
                accent = MaterialTheme.colorScheme.tertiary,
                onClick = onOpenReports,
            )
        }

        item {
            SectionHeader(
                title = "Recent activity",
                actionText = "See all",
                onAction = onOpenHistory,
            )
        }

        if (summary.recentActivity.isEmpty()) {
            item {
                Text(
                    "No check-ins recorded yet today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(summary.recentActivity, key = { it.id }) { record ->
                RecentActivityRow(record)
            }
        }
    }
}

@Composable
private fun RecentActivityRow(record: AttendanceRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(name = record.employeeName, size = 40.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(record.employeeName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(record.department)
                    record.checkInTime?.let { append(" • in $it") }
                    record.checkOutTime?.let { append(" • out $it") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AttendanceStatusPill(record.status)
    }
}

private fun greeting(): String = when (AppClock.currentHour()) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
