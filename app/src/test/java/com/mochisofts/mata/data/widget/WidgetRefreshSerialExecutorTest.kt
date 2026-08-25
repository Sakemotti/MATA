package com.mochisofts.mata.data.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetRefreshSerialExecutorTest {
    @Test
    fun overlappingRefreshes_areSerialized() = runTest {
        val executor = WidgetRefreshSerialExecutor()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = async {
            executor.run {
                events += "first-start"
                firstStarted.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        firstStarted.await()

        val second = async {
            executor.run {
                events += "second"
            }
        }
        yield()

        assertEquals(listOf("first-start"), events)

        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "second"), events)
    }
}
