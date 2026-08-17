package com.mochisofts.mata.data.repository

import androidx.room.withTransaction
import com.mochisofts.mata.data.holiday.HolidayDataException
import com.mochisofts.mata.data.holiday.HolidayHttpClient
import com.mochisofts.mata.data.holiday.HolidayHttpResponse
import com.mochisofts.mata.data.holiday.HolidayHttpValidators
import com.mochisofts.mata.data.holiday.HolidayJsonParser
import com.mochisofts.mata.data.holiday.ParsedHoliday
import com.mochisofts.mata.data.local.HolidayDao
import com.mochisofts.mata.data.local.HolidayEntity
import com.mochisofts.mata.data.local.HolidayFetchStateDao
import com.mochisofts.mata.data.local.HolidayFetchStateEntity
import com.mochisofts.mata.data.local.HolidayUpdateStateDao
import com.mochisofts.mata.data.local.HolidayUpdateStateEntity
import com.mochisofts.mata.data.local.MataDatabase
import com.mochisofts.mata.domain.model.HolidayRefreshResult
import com.mochisofts.mata.domain.model.HolidaySnapshot
import com.mochisofts.mata.domain.model.HolidayYearState
import com.mochisofts.mata.domain.model.HolidayYearStatus
import com.mochisofts.mata.domain.repository.HolidayRepository
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomHolidayRepository @Inject constructor(
    private val database: MataDatabase,
    private val holidayDao: HolidayDao,
    private val fetchStateDao: HolidayFetchStateDao,
    private val updateStateDao: HolidayUpdateStateDao,
    private val httpClient: HolidayHttpClient,
    private val clock: Clock,
) : HolidayRepository {
    private val refreshMutex = Mutex()
    private val refreshing = MutableStateFlow(false)

    override val snapshot: Flow<HolidaySnapshot> = combine(
        holidayDao.observeAll(),
        fetchStateDao.observeAll(),
        updateStateDao.observeCurrent(),
        refreshing,
    ) { holidays, states, update, isRefreshing ->
        val years = supportedYears()
        val now = clock.millis()
        val statesByYear = states.associateBy(HolidayFetchStateEntity::year)
        val visibleYears = statesByYear.keys + years
        HolidaySnapshot(
            namesByDate = holidays.associate { LocalDate.parse(it.date) to it.name },
            yearStates = visibleYears.associateWith { year ->
                statesByYear[year]?.toDomainStatus(
                    now = now,
                    isRefreshing = isRefreshing && year in years,
                    isSupported = year in years,
                ) ?: HolidayYearState(
                    year = year,
                    status = if (isRefreshing && year in years) {
                        HolidayYearStatus.FETCHING
                    } else if (year in years) {
                        HolidayYearStatus.UNAVAILABLE
                    } else {
                        HolidayYearStatus.OUT_OF_RANGE
                    },
                )
            },
            generation = update?.generation ?: 0,
            changedYears = update?.changedYears.toIntSet(),
            supportedYears = years,
        )
    }

    override suspend fun currentSnapshot(): HolidaySnapshot = snapshot.first()

    override suspend fun needsRefresh(): Boolean {
        if (updateStateDao.findCurrent()?.notificationProcessed == false) return true
        val years = supportedYears()
        val states = fetchStateDao.findForYears(years.toList()).associateBy { it.year }
        val now = clock.millis()
        if (years.any { year ->
                val state = states[year]
                state?.availability != AVAILABILITY_AVAILABLE || !isFresh(state.lastCheckedAt, now)
            }
        ) {
            return true
        }
        return !hasCompleteCache(years, states)
    }

    override suspend fun refresh(): HolidayRefreshResult = refreshMutex.withLock {
        val years = supportedYears()
        val beforeStates = fetchStateDao.findForYears(years.toList()).associateBy { it.year }
        val requestTime = clock.millis()
        if (years.all { year ->
                beforeStates[year]?.let { state ->
                    state.availability == AVAILABILITY_AVAILABLE &&
                        isFresh(state.lastCheckedAt, requestTime)
                } == true
            } && hasCompleteCache(years, beforeStates)
        ) {
            return@withLock HolidayRefreshResult(successful = true)
        }
        val lastFailedAttempt = beforeStates.values.mapNotNull { state ->
            state.lastAttemptedAt.takeIf { state.lastAttemptResult in FAILURE_RESULTS }
        }.maxOrNull()
        val sinceFailure = lastFailedAttempt?.let { clock.millis() - it }
        if (sinceFailure != null && sinceFailure >= 0 && sinceFailure < FAILURE_COOLDOWN_MILLIS) {
            return@withLock HolidayRefreshResult(
                successful = false,
                retryable = true,
                errorCode = ERROR_COOLDOWN,
            )
        }

        refreshing.value = true
        try {
            val attemptedAt = clock.millis()
            val validators = commonValidators(years, beforeStates)
            var response = httpClient.fetch(validators)
            if (response.statusCode == HTTP_NOT_MODIFIED && !hasCompleteCache(years, beforeStates)) {
                response = httpClient.fetch(null)
            }
            when (response.statusCode) {
                HTTP_OK -> applySuccessfulResponse(years, response, attemptedAt)
                HTTP_NOT_MODIFIED -> applyNotModified(years, response, attemptedAt, beforeStates)
                HTTP_REQUEST_TIMEOUT, HTTP_TOO_MANY_REQUESTS -> recordFailure(
                    years, beforeStates, attemptedAt, "http_${response.statusCode}", retryable = true,
                )
                in 500..599 -> recordFailure(
                    years, beforeStates, attemptedAt, "http_5xx", retryable = true,
                )
                else -> recordFailure(
                    years, beforeStates, attemptedAt, "http_${response.statusCode}", retryable = false,
                )
            }
        } catch (error: HolidayDataException) {
            recordFailure(
                years = years,
                previous = beforeStates,
                attemptedAt = clock.millis(),
                errorCode = error.errorCode,
                retryable = error.retryable,
            )
        } catch (_: Exception) {
            recordFailure(
                years = years,
                previous = beforeStates,
                attemptedAt = clock.millis(),
                errorCode = ERROR_STORAGE,
                retryable = true,
            )
        } finally {
            refreshing.value = false
        }
    }

    override suspend fun pendingNotificationGeneration(): Long? = updateStateDao.findCurrent()
        ?.takeUnless(HolidayUpdateStateEntity::notificationProcessed)
        ?.generation

    override suspend fun markNotificationGenerationProcessed(generation: Long) {
        database.withTransaction {
            val state = updateStateDao.findCurrent() ?: return@withTransaction
            if (state.generation == generation) {
                updateStateDao.upsert(state.copy(notificationProcessed = true))
            }
        }
    }

    private suspend fun applySuccessfulResponse(
        years: Set<Int>,
        response: HolidayHttpResponse,
        attemptedAt: Long,
    ): HolidayRefreshResult {
        val parsed = HolidayJsonParser.parse(
            body = response.body ?: throw HolidayDataException(ERROR_EMPTY_BODY),
            requiredYears = years,
        )
        val hashes = parsed.mapValues { (_, holidays) -> hash(holidays) }
        val changedYears = mutableSetOf<Int>()
        val changedDates = mutableSetOf<LocalDate>()
        val renamedDates = mutableSetOf<LocalDate>()
        var generation: Long? = null
        database.withTransaction {
            val previousStates = fetchStateDao.findForYears(years.toList()).associateBy { it.year }
            val previousHolidays = holidayDao.findForYears(years.toList()).groupBy(HolidayEntity::year)
            years.forEach { year ->
                val old = previousHolidays[year].orEmpty().associate { it.date to it.name }
                val new = parsed.getValue(year).associate { it.date.toString() to it.name }
                if (previousStates[year]?.dataHash != hashes.getValue(year) || old != new) {
                    changedYears += year
                    val allDates = old.keys + new.keys
                    allDates.filter { old[it] != new[it] }
                        .mapTo(changedDates, LocalDate::parse)
                    allDates.filter { old[it] != null && new[it] != null && old[it] != new[it] }
                        .mapTo(renamedDates, LocalDate::parse)
                    holidayDao.deleteYear(year)
                    holidayDao.insertAll(
                        parsed.getValue(year).map { holiday ->
                            HolidayEntity(
                                date = holiday.date.toString(),
                                year = year,
                                name = holiday.name,
                                sourceId = SOURCE_ID,
                                sourceDataHash = hashes.getValue(year),
                                fetchedAt = attemptedAt,
                            )
                        },
                    )
                }
            }
            fetchStateDao.upsertAll(
                years.map { year ->
                    val old = previousStates[year]
                    val changed = year in changedYears
                    HolidayFetchStateEntity(
                        year = year,
                        sourceId = SOURCE_ID,
                        availability = AVAILABILITY_AVAILABLE,
                        dataHash = hashes.getValue(year),
                        fetchedAt = if (changed) attemptedAt else old?.fetchedAt ?: attemptedAt,
                        lastCheckedAt = attemptedAt,
                        lastAttemptedAt = attemptedAt,
                        lastAttemptResult = if (changed) RESULT_SUCCESS else RESULT_UNCHANGED,
                        etag = response.etag,
                        lastModified = response.lastModified,
                        lastErrorCode = null,
                    )
                },
            )
            if (changedYears.isNotEmpty()) {
                val nextGeneration = (updateStateDao.findCurrent()?.generation ?: 0) + 1
                generation = nextGeneration
                updateStateDao.upsert(
                    HolidayUpdateStateEntity(
                        generation = nextGeneration,
                        changedYears = changedYears.sorted().joinToString(","),
                        changedDates = changedDates.sorted().joinToString(","),
                        renamedDates = renamedDates.sorted().joinToString(","),
                        domainProcessed = true,
                        notificationProcessed = false,
                        widgetProcessed = false,
                        createdAt = attemptedAt,
                    ),
                )
            }
        }
        return HolidayRefreshResult(
            successful = true,
            changedYears = changedYears,
            generation = generation,
        )
    }

    private suspend fun applyNotModified(
        years: Set<Int>,
        response: HolidayHttpResponse,
        attemptedAt: Long,
        previous: Map<Int, HolidayFetchStateEntity>,
    ): HolidayRefreshResult {
        if (!hasCompleteCache(years, previous)) {
            return recordFailure(years, previous, attemptedAt, ERROR_INCOMPLETE_304, retryable = false)
        }
        database.withTransaction {
            fetchStateDao.upsertAll(
                years.map { year ->
                    previous.getValue(year).copy(
                        lastCheckedAt = attemptedAt,
                        lastAttemptedAt = attemptedAt,
                        lastAttemptResult = RESULT_UNCHANGED,
                        etag = response.etag ?: previous.getValue(year).etag,
                        lastModified = response.lastModified ?: previous.getValue(year).lastModified,
                        lastErrorCode = null,
                    )
                },
            )
        }
        return HolidayRefreshResult(successful = true)
    }

    private suspend fun recordFailure(
        years: Set<Int>,
        previous: Map<Int, HolidayFetchStateEntity>,
        attemptedAt: Long,
        errorCode: String,
        retryable: Boolean,
    ): HolidayRefreshResult {
        runCatching {
            database.withTransaction {
                fetchStateDao.upsertAll(
                    years.map { year ->
                        val old = previous[year]
                        HolidayFetchStateEntity(
                            year = year,
                            sourceId = SOURCE_ID,
                            availability = old?.availability ?: AVAILABILITY_UNAVAILABLE,
                            dataHash = old?.dataHash,
                            fetchedAt = old?.fetchedAt,
                            lastCheckedAt = old?.lastCheckedAt,
                            lastAttemptedAt = attemptedAt,
                            lastAttemptResult = when {
                                errorCode.startsWith("http_") -> RESULT_HTTP_FAILURE
                                errorCode in VALIDATION_ERRORS -> RESULT_VALIDATION_FAILURE
                                else -> RESULT_NETWORK_FAILURE
                            },
                            etag = old?.etag,
                            lastModified = old?.lastModified,
                            lastErrorCode = errorCode,
                        )
                    },
                )
            }
        }
        return HolidayRefreshResult(
            successful = false,
            retryable = retryable,
            errorCode = errorCode,
        )
    }

    private suspend fun commonValidators(
        years: Set<Int>,
        states: Map<Int, HolidayFetchStateEntity>,
    ): HolidayHttpValidators? {
        if (!hasCompleteCache(years, states)) return null
        val values = years.map { states.getValue(it) }
        val etags = values.map { it.etag }.distinct()
        val lastModifiedValues = values.map { it.lastModified }.distinct()
        if (etags.size != 1 || lastModifiedValues.size != 1) return null
        val etag = etags.single()
        val lastModified = lastModifiedValues.single()
        if (etag == null && lastModified == null) return null
        return HolidayHttpValidators(etag, lastModified)
    }

    private suspend fun hasCompleteCache(
        years: Set<Int>,
        states: Map<Int, HolidayFetchStateEntity>,
    ): Boolean {
        if (years.any { states[it]?.availability != AVAILABILITY_AVAILABLE }) return false
        return holidayDao.findForYears(years.toList()).groupBy(HolidayEntity::year)
            .let { grouped -> years.all { grouped[it].orEmpty().isNotEmpty() } }
    }

    private fun supportedYears(): Set<Int> {
        val currentYear = clock.instant().atZone(TOKYO).year
        return setOf(currentYear - 1, currentYear, currentYear + 1)
    }

    private fun HolidayFetchStateEntity.toDomainStatus(
        now: Long,
        isRefreshing: Boolean,
        isSupported: Boolean,
    ): HolidayYearState {
        val available = availability == AVAILABILITY_AVAILABLE
        val failed = lastAttemptResult in FAILURE_RESULTS
        val stale = !isFresh(lastCheckedAt, now)
        val status = when {
            !isSupported -> HolidayYearStatus.OUT_OF_RANGE
            isRefreshing -> HolidayYearStatus.FETCHING
            failed && available -> HolidayYearStatus.FAILED_WITH_CACHE
            failed -> HolidayYearStatus.FAILED_WITHOUT_CACHE
            available && stale -> HolidayYearStatus.AVAILABLE_STALE
            available -> HolidayYearStatus.AVAILABLE_CURRENT
            else -> HolidayYearStatus.UNAVAILABLE
        }
        return HolidayYearState(
            year = year,
            status = status,
            available = available,
            lastCheckedAt = lastCheckedAt,
            lastAttemptedAt = lastAttemptedAt,
            lastErrorCode = lastErrorCode,
        )
    }

    private fun hash(holidays: List<ParsedHoliday>): String {
        val canonical = holidays.sortedBy(ParsedHoliday::date)
            .joinToString(separator = "\n", postfix = "\n") { "${it.date}\u0000${it.name}" }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun isFresh(lastCheckedAt: Long?, now: Long): Boolean =
        lastCheckedAt != null && now >= lastCheckedAt && now - lastCheckedAt < FRESH_MILLIS

    private fun String?.toIntSet(): Set<Int> = this
        ?.split(',')
        ?.mapNotNull(String::toIntOrNull)
        ?.toSet()
        .orEmpty()

    companion object {
        const val SOURCE_ID = "holidays_jp_v1"
        const val AVAILABILITY_AVAILABLE = "available"
        const val AVAILABILITY_UNAVAILABLE = "unavailable"
        const val RESULT_SUCCESS = "success"
        const val RESULT_UNCHANGED = "unchanged"
        const val RESULT_NETWORK_FAILURE = "network_failure"
        const val RESULT_HTTP_FAILURE = "http_failure"
        const val RESULT_VALIDATION_FAILURE = "validation_failure"
        const val ERROR_COOLDOWN = "retry_cooldown"
        const val ERROR_STORAGE = "room_save_failure"
        const val ERROR_EMPTY_BODY = "empty_body"
        const val ERROR_INCOMPLETE_304 = "incomplete_cache_304"
        private const val HTTP_OK = 200
        private const val HTTP_NOT_MODIFIED = 304
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val TOKYO = ZoneId.of("Asia/Tokyo")
        private val FRESH_MILLIS = Duration.ofDays(7).toMillis()
        private val FAILURE_COOLDOWN_MILLIS = Duration.ofMinutes(15).toMillis()
        private val FAILURE_RESULTS = setOf(
            RESULT_NETWORK_FAILURE,
            RESULT_HTTP_FAILURE,
            RESULT_VALIDATION_FAILURE,
        )
        private val VALIDATION_ERRORS = setOf(
            HolidayJsonParser.ERROR_BOM,
            HolidayJsonParser.ERROR_INVALID_UTF8,
            HolidayJsonParser.ERROR_INVALID_JSON,
            HolidayJsonParser.ERROR_INVALID_ROOT,
            HolidayJsonParser.ERROR_DUPLICATE_OR_INVALID_KEY,
            HolidayJsonParser.ERROR_INVALID_DATE,
            HolidayJsonParser.ERROR_INVALID_NAME,
            HolidayJsonParser.ERROR_REQUIRED_YEAR_MISSING,
            ERROR_EMPTY_BODY,
            ERROR_INCOMPLETE_304,
        )
    }
}
