package com.mochisofts.mata.data.repository

import com.mochisofts.mata.domain.model.MonthlyNthWeekday
import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceRuleJsonTest {
    @Test
    fun allRuleParameters_roundTripWithStableTypeCodes() {
        val rules = listOf(
            RecurrenceRule.once(),
            RecurrenceRule.daily(),
            RecurrenceRule(RecurrenceType.WEEKDAYS),
            RecurrenceRule(
                RecurrenceType.SELECTED_WEEKDAYS,
                selectedWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            ),
            RecurrenceRule(RecurrenceType.MONTHLY_DAY, monthlyDay = 31),
            RecurrenceRule(
                RecurrenceType.MONTHLY_NTH_WEEKDAYS,
                monthlyNthWeekdays = setOf(
                    MonthlyNthWeekday(1, DayOfWeek.MONDAY),
                    MonthlyNthWeekday(3, DayOfWeek.FRIDAY),
                    MonthlyNthWeekday(5, DayOfWeek.MONDAY),
                ),
            ),
            RecurrenceRule(RecurrenceType.MONTH_END),
            RecurrenceRule(RecurrenceType.EVERY_N_DAYS, intervalDays = 999),
            RecurrenceRule(RecurrenceType.WEEKLY_COUNT, requiredCount = 7),
            RecurrenceRule(RecurrenceType.MONTHLY_COUNT, requiredCount = 31),
        )

        rules.forEach { rule ->
            val encoded = RecurrenceRuleJson.encode(rule)
            assertEquals(rule.type.code, encoded.typeCode)
            assertEquals(
                rule,
                RecurrenceRuleJson.decode(
                    encoded.typeCode,
                    encoded.paramsVersion,
                    encoded.paramsJson,
                ),
            )
        }
    }
}
