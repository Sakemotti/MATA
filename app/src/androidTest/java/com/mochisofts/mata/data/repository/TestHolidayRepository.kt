package com.mochisofts.mata.data.repository

import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.repository.HolidayRepository
import kotlinx.coroutines.flow.MutableStateFlow

class TestHolidayRepository(
    initialSnapshot: HolidaySnapshot = HolidaySnapshot(),
) : HolidayRepository {
    override val snapshot = MutableStateFlow(initialSnapshot)

    override suspend fun currentSnapshot(): HolidaySnapshot = snapshot.value

    override suspend fun needsRefresh(): Boolean = false

    override suspend fun refresh(): HolidayRefreshResult = HolidayRefreshResult(successful = true)

    override suspend fun pendingNotificationGeneration(): Long? = null

    override suspend fun markNotificationGenerationProcessed(generation: Long) = Unit
}
