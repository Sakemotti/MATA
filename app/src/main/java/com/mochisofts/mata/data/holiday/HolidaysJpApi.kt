package com.mochisofts.mata.data.holiday

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class HolidayHttpValidators(
    val etag: String? = null,
    val lastModified: String? = null,
)

data class HolidayHttpResponse(
    val statusCode: Int,
    val body: ByteArray? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val retryAfter: String? = null,
)

class HolidayDataException(
    val errorCode: String,
    val retryable: Boolean = false,
    cause: Throwable? = null,
) : Exception(errorCode, cause)

interface HolidayHttpClient {
    suspend fun fetch(validators: HolidayHttpValidators?): HolidayHttpResponse
}

@Singleton
class UrlConnectionHolidayHttpClient @Inject constructor() : HolidayHttpClient {
    override suspend fun fetch(validators: HolidayHttpValidators?): HolidayHttpResponse =
        withContext(Dispatchers.IO) {
            var target = URL(API_URL)
            var redirects = 0
            var result: HolidayHttpResponse? = null
            while (result == null) {
                if (target.protocol != "https") {
                    throw HolidayDataException(ERROR_INSECURE_REDIRECT)
                }
                val connection = (target.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MILLIS
                    readTimeout = READ_TIMEOUT_MILLIS
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Encoding", "gzip")
                    setRequestProperty("User-Agent", "MATA")
                    validators?.etag?.let { setRequestProperty("If-None-Match", it) }
                    validators?.lastModified?.let { setRequestProperty("If-Modified-Since", it) }
                }
                try {
                    val status = connection.responseCode
                    if (status in REDIRECT_CODES) {
                        if (redirects >= MAX_REDIRECTS) {
                            throw HolidayDataException(ERROR_TOO_MANY_REDIRECTS)
                        }
                        val location = connection.getHeaderField("Location")
                            ?: throw HolidayDataException(ERROR_INVALID_REDIRECT)
                        target = try {
                            URL(target, location)
                        } catch (error: Exception) {
                            throw HolidayDataException(ERROR_INVALID_REDIRECT, cause = error)
                        }
                        if (target.protocol != "https") {
                            throw HolidayDataException(ERROR_INSECURE_REDIRECT)
                        }
                        redirects++
                        continue
                    }
                    val body = if (status == HttpURLConnection.HTTP_OK) {
                        val rawStream = connection.inputStream
                        val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) {
                            GZIPInputStream(rawStream)
                        } else {
                            rawStream
                        }
                        stream.use(::readLimited)
                    } else {
                        null
                    }
                    result = HolidayHttpResponse(
                        statusCode = status,
                        body = body,
                        etag = safeHeader(connection.getHeaderField("ETag")),
                        lastModified = safeHeader(connection.getHeaderField("Last-Modified")),
                        retryAfter = safeHeader(connection.getHeaderField("Retry-After")),
                    )
                } catch (error: HolidayDataException) {
                    throw error
                } catch (error: SocketTimeoutException) {
                    throw HolidayDataException(ERROR_TIMEOUT, retryable = true, cause = error)
                } catch (error: SSLException) {
                    throw HolidayDataException(ERROR_TLS, retryable = true, cause = error)
                } catch (error: IOException) {
                    throw HolidayDataException(ERROR_CONNECTION, retryable = true, cause = error)
                } finally {
                    connection.disconnect()
                }
            }
            result
        }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_RESPONSE_BYTES) {
                throw HolidayDataException(ERROR_RESPONSE_TOO_LARGE)
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun safeHeader(value: String?): String? = value
        ?.takeIf { it.length <= MAX_HEADER_LENGTH && it.none(Char::isISOControl) }

    companion object {
        const val API_URL = "https://holidays-jp.github.io/api/v1/date.json"
        const val MAX_RESPONSE_BYTES = 1_048_576
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 30_000
        private const val MAX_REDIRECTS = 5
        private const val MAX_HEADER_LENGTH = 512
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        const val ERROR_TIMEOUT = "timeout"
        const val ERROR_TLS = "tls_failure"
        const val ERROR_CONNECTION = "connection_failure"
        const val ERROR_INSECURE_REDIRECT = "insecure_redirect"
        const val ERROR_INVALID_REDIRECT = "invalid_redirect"
        const val ERROR_TOO_MANY_REDIRECTS = "too_many_redirects"
        const val ERROR_RESPONSE_TOO_LARGE = "response_too_large"
    }
}

object HolidayJsonParser {
    private val dateFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        .withResolverStyle(ResolverStyle.STRICT)
    private val objectKeyPattern = Regex("""(?<!\\)"((?:\\.|[^"\\])*)"\s*:""")

    fun parse(body: ByteArray, requiredYears: Set<Int>): Map<Int, List<ParsedHoliday>> {
        if (body.size >= 3 && body[0] == 0xEF.toByte() && body[1] == 0xBB.toByte() && body[2] == 0xBF.toByte()) {
            throw HolidayDataException(ERROR_BOM)
        }
        val text = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body))
                .toString()
        } catch (error: Exception) {
            throw HolidayDataException(ERROR_INVALID_UTF8, cause = error)
        }
        val serializedKeyCount = objectKeyPattern.findAll(text).count()
        val element = try {
            Json.parseToJsonElement(text)
        } catch (error: Exception) {
            throw HolidayDataException(ERROR_INVALID_JSON, cause = error)
        }
        val root = element as? JsonObject ?: throw HolidayDataException(ERROR_INVALID_ROOT)
        if (serializedKeyCount != root.size) {
            throw HolidayDataException(ERROR_DUPLICATE_OR_INVALID_KEY)
        }

        val parsed = root.map { (key, value) ->
            val date = try {
                LocalDate.parse(key, dateFormatter).also { parsedDate ->
                    if (parsedDate.toString() != key) throw IllegalArgumentException()
                }
            } catch (error: Exception) {
                throw HolidayDataException(ERROR_INVALID_DATE, cause = error)
            }
            val primitive = value as? JsonPrimitive
                ?: throw HolidayDataException(ERROR_INVALID_NAME)
            if (!primitive.isString) throw HolidayDataException(ERROR_INVALID_NAME)
            val name = primitive.content
            if (name.isBlank() || name.length > MAX_NAME_LENGTH || name.any(Char::isISOControl)) {
                throw HolidayDataException(ERROR_INVALID_NAME)
            }
            ParsedHoliday(date, name)
        }
        val grouped = parsed.filter { it.date.year in requiredYears }
            .groupBy { it.date.year }
            .mapValues { (_, values) -> values.sortedBy(ParsedHoliday::date) }
        if (requiredYears.any { grouped[it].isNullOrEmpty() }) {
            throw HolidayDataException(ERROR_REQUIRED_YEAR_MISSING)
        }
        return grouped
    }

    const val ERROR_BOM = "bom_not_allowed"
    const val ERROR_INVALID_UTF8 = "invalid_utf8"
    const val ERROR_INVALID_JSON = "invalid_json"
    const val ERROR_INVALID_ROOT = "invalid_json_root"
    const val ERROR_DUPLICATE_OR_INVALID_KEY = "duplicate_or_invalid_key"
    const val ERROR_INVALID_DATE = "invalid_date"
    const val ERROR_INVALID_NAME = "invalid_name"
    const val ERROR_REQUIRED_YEAR_MISSING = "required_year_missing"
    private const val MAX_NAME_LENGTH = 100
}

data class ParsedHoliday(val date: LocalDate, val name: String)
