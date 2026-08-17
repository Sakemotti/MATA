package com.mochisofts.mata.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochisofts.mata.data.holiday.HolidayHttpClient
import com.mochisofts.mata.data.holiday.HolidayHttpResponse
import com.mochisofts.mata.data.holiday.HolidayHttpValidators
import com.mochisofts.mata.data.local.MataDatabase
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomHolidayRepositoryTest {
    private lateinit var database: MataDatabase
    private lateinit var clock: MutableTestClock
    private lateinit var client: FakeHolidayHttpClient
    private lateinit var repository: RoomHolidayRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = MutableTestClock(
            Instant.parse("2026-08-17T00:00:00Z"),
            ZoneId.of("Asia/Tokyo"),
        )
        client = FakeHolidayHttpClient()
        repository = RoomHolidayRepository(
            database = database,
            holidayDao = database.holidayDao(),
            fetchStateDao = database.holidayFetchStateDao(),
            updateStateDao = database.holidayUpdateStateDao(),
            httpClient = client,
            clock = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refresh_replacesThreeYearsAndUsesValidatorsAfterCacheExpires() = runBlocking {
        client.enqueue(okResponse())

        val first = repository.refresh()

        assertTrue(first.successful)
        assertEquals(setOf(2025, 2026, 2027), first.changedYears)
        assertEquals(3, database.holidayDao().findAll().size)
        assertEquals(1L, repository.pendingNotificationGeneration())
        assertTrue(repository.needsRefresh())

        repository.markNotificationGenerationProcessed(1)
        assertNull(repository.pendingNotificationGeneration())
        assertFalse(repository.needsRefresh())

        clock.advance(Duration.ofDays(8))
        client.enqueue(HolidayHttpResponse(statusCode = 304, etag = "etag-1"))

        val second = repository.refresh()

        assertTrue(second.successful)
        assertEquals(HolidayHttpValidators("etag-1", "last-modified-1"), client.validators.last())
        assertEquals(3, database.holidayDao().findAll().size)
    }

    @Test
    fun invalidUpdate_preservesLastKnownGoodCache() = runBlocking {
        client.enqueue(okResponse())
        repository.refresh()
        val cached = database.holidayDao().findAll()
        clock.advance(Duration.ofDays(8))
        client.enqueue(
            HolidayHttpResponse(
                statusCode = 200,
                body = "{\"2026-01-01\":\"変更名\"}".toByteArray(StandardCharsets.UTF_8),
            ),
        )

        val result = repository.refresh()

        assertFalse(result.successful)
        assertEquals(cached, database.holidayDao().findAll())
        assertEquals("required_year_missing", result.errorCode)
    }

    @Test
    fun unexpectedNotModifiedWithoutCache_retriesOnceWithoutValidators() = runBlocking {
        client.enqueue(HolidayHttpResponse(statusCode = 304))
        client.enqueue(okResponse())

        val result = repository.refresh()

        assertTrue(result.successful)
        assertEquals(listOf(null, null), client.validators)
        assertEquals(3, database.holidayDao().findAll().size)
    }

    private fun okResponse() = HolidayHttpResponse(
        statusCode = 200,
        body = """
            {
              "2025-01-01": "元日",
              "2026-01-01": "元日",
              "2027-01-01": "元日"
            }
        """.trimIndent().toByteArray(StandardCharsets.UTF_8),
        etag = "etag-1",
        lastModified = "last-modified-1",
    )
}

private class FakeHolidayHttpClient : HolidayHttpClient {
    private val responses = ArrayDeque<HolidayHttpResponse>()
    val validators = mutableListOf<HolidayHttpValidators?>()

    fun enqueue(response: HolidayHttpResponse) {
        responses.addLast(response)
    }

    override suspend fun fetch(validators: HolidayHttpValidators?): HolidayHttpResponse {
        this.validators += validators
        return responses.removeFirst()
    }
}

private class MutableTestClock(
    private var current: Instant,
    private val currentZone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = MutableTestClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}
