package com.facerem.core.data

import com.facerem.feature.attendance.data.AttendanceRepositoryImpl
import com.facerem.feature.attendance.domain.AttendanceRepository
import com.facerem.feature.auth.data.AuthRepositoryImpl
import com.facerem.feature.auth.domain.AuthRepository
import com.facerem.feature.dashboard.data.DashboardRepositoryImpl
import com.facerem.feature.dashboard.domain.DashboardRepository
import com.facerem.feature.employees.data.EmployeeRepositoryImpl
import com.facerem.feature.employees.domain.EmployeeRepository
import com.facerem.feature.reports.data.ReportRepositoryImpl
import com.facerem.feature.reports.domain.ReportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Repository bindings. Swapping a demo implementation for its API-backed counterpart is a
 * one-line change here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindEmployeeRepository(impl: EmployeeRepositoryImpl): EmployeeRepository

    @Binds
    @Singleton
    abstract fun bindAttendanceRepository(impl: AttendanceRepositoryImpl): AttendanceRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository
}
