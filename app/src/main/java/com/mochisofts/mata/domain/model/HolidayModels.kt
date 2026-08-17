package com.mochisofts.mata.domain.model

import java.time.LocalDate

enum class HolidayYearStatus {
    AVAILABLE_CURRENT,
    AVAILABLE_STALE,
    UNAVAILABLE,
    FETCHING,
    FAILED_WITH_CACHE,
    FAILED_WITHOUT_CACHE,
    OUT_OF_RANGE,
}

data class HolidayYearState(
    val year: Int,
    val status: HolidayYearStatus,
    val available: Boolean = false,
    val lastCheckedAt: Long? = null,
    val lastAttemptedAt: Long? = null,
    val lastErrorCode: String? = null,
) {
    val hasAvailableData: Boolean
        get() = available
}

data class HolidaySnapshot(
    val namesByDate: Map<LocalDate, String> = emptyMap(),
    val yearStates: Map<Int, HolidayYearState> = emptyMap(),
    val generation: Long = 0,
    val changedYears: Set<Int> = emptySet(),
    val supportedYears: Set<Int> = emptySet(),
) {
    val dates: Set<LocalDate>
        get() = namesByDate.keys

    fun holidayName(date: LocalDate): String? = namesByDate[date]

    fun isDefinitive(year: Int): Boolean = yearStates[year]?.hasAvailableData == true

    fun isProvisional(year: Int): Boolean = !isDefinitive(year)

    fun isStale(year: Int): Boolean = yearStates[year]?.status == HolidayYearStatus.AVAILABLE_STALE ||
        yearStates[year]?.status == HolidayYearStatus.FAILED_WITH_CACHE

    fun statusFor(year: Int): HolidayYearStatus = yearStates[year]?.status
        ?: if (year in supportedYears) HolidayYearStatus.UNAVAILABLE else HolidayYearStatus.OUT_OF_RANGE
}

data class HolidayRefreshResult(
    val successful: Boolean,
    val changedYears: Set<Int> = emptySet(),
    val generation: Long? = null,
    val retryable: Boolean = false,
    val errorCode: String? = null,
)
