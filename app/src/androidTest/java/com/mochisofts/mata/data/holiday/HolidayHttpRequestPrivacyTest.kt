package com.mochisofts.mata.data.holiday

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HolidayHttpRequestPrivacyTest {
    @Test
    fun dat003_holidayRequestContainsOnlyFixedProtocolMetadata() {
        val request = holidayHttpRequest(
            target = URL(UrlConnectionHolidayHttpClient.API_URL),
            validators = HolidayHttpValidators(
                etag = "server-etag",
                lastModified = "Wed, 02 Sep 2026 00:00:00 GMT",
            ),
        )

        assertEquals("https", request.target.protocol)
        assertEquals("holidays-jp.github.io", request.target.host)
        assertEquals("/api/v1/date.json", request.target.path)
        assertNull(request.target.query)
        assertEquals("GET", request.method)
        assertFalse(request.hasBody)
        assertEquals(
            mapOf(
                "Accept" to "application/json",
                "Accept-Encoding" to "gzip",
                "User-Agent" to "MATA",
                "If-None-Match" to "server-etag",
                "If-Modified-Since" to "Wed, 02 Sep 2026 00:00:00 GMT",
            ),
            request.headers,
        )

        val serializedRequest = buildString {
            append(request.target)
            request.headers.forEach { (name, value) -> append(name).append(value) }
        }
        listOf(
            "private todo title",
            "private category",
            "private history",
        ).forEach { privateValue -> assertFalse(serializedRequest.contains(privateValue)) }
    }
}
