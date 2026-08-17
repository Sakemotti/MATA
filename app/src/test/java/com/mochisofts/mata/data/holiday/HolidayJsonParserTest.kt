package com.mochisofts.mata.data.holiday

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class HolidayJsonParserTest {
    @Test
    fun parse_acceptsStrictUtf8ObjectForAllRequiredYears() {
        val parsed = HolidayJsonParser.parse(
            body = """
                {
                  "2025-01-01": "元日",
                  "2026-01-01": "元日",
                  "2027-01-01": "元日"
                }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8),
            requiredYears = setOf(2025, 2026, 2027),
        )

        assertEquals(setOf(2025, 2026, 2027), parsed.keys)
        assertEquals(
            ParsedHoliday(LocalDate.of(2026, 1, 1), "元日"),
            parsed.getValue(2026).single(),
        )
    }

    @Test
    fun parse_acceptsSymbolsThatLookLikeAJsonKeyInsideHolidayName() {
        val parsed = HolidayJsonParser.parse(
            "{\"2026-01-01\":\"記念 \\\"2026-01-01\\\": 日\"}"
                .toByteArray(StandardCharsets.UTF_8),
            setOf(2026),
        )

        assertEquals("記念 \"2026-01-01\": 日", parsed.getValue(2026).single().name)
    }

    @Test
    fun parse_rejectsDuplicateDateKeys() {
        assertError(HolidayJsonParser.ERROR_DUPLICATE_OR_INVALID_KEY) {
            HolidayJsonParser.parse(
                """{"2026-01-01":"元日","2026-01-01":"別名"}"""
                    .toByteArray(StandardCharsets.UTF_8),
                setOf(2026),
            )
        }
    }

    @Test
    fun parse_rejectsBomInvalidUtf8AndControlCharacters() {
        assertError(HolidayJsonParser.ERROR_BOM) {
            HolidayJsonParser.parse(
                byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                    "{\"2026-01-01\":\"元日\"}".toByteArray(StandardCharsets.UTF_8),
                setOf(2026),
            )
        }
        assertError(HolidayJsonParser.ERROR_INVALID_UTF8) {
            HolidayJsonParser.parse(byteArrayOf(0xC3.toByte(), 0x28), setOf(2026))
        }
        assertError(HolidayJsonParser.ERROR_INVALID_NAME) {
            HolidayJsonParser.parse(
                "{\"2026-01-01\":\"元日\\n改行\"}".toByteArray(StandardCharsets.UTF_8),
                setOf(2026),
            )
        }
    }

    @Test
    fun parse_rejectsMissingRequiredYear() {
        assertError(HolidayJsonParser.ERROR_REQUIRED_YEAR_MISSING) {
            HolidayJsonParser.parse(
                "{\"2026-01-01\":\"元日\"}".toByteArray(StandardCharsets.UTF_8),
                setOf(2025, 2026, 2027),
            )
        }
    }

    private fun assertError(expectedCode: String, block: () -> Unit) {
        try {
            block()
            fail("Expected HolidayDataException")
        } catch (error: HolidayDataException) {
            assertEquals(expectedCode, error.errorCode)
        }
    }
}
