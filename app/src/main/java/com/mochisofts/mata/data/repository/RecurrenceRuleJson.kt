package com.mochisofts.mata.data.repository

import com.mochisofts.mata.domain.model.RecurrenceRule
import com.mochisofts.mata.domain.model.RecurrenceType
import java.time.DayOfWeek
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CURRENT_REPEAT_PARAMS_VERSION = 1

internal data class EncodedRecurrenceRule(
    val typeCode: String,
    val paramsVersion: Int,
    val paramsJson: String,
)

internal object RecurrenceRuleJson {
    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    fun encode(rule: RecurrenceRule): EncodedRecurrenceRule {
        val payload = RepeatParamsPayload(
            selectedWeekdays = rule.selectedWeekdays.map(DayOfWeek::getValue).sorted(),
            monthlyDay = rule.monthlyDay,
            intervalDays = rule.intervalDays,
            requiredCount = rule.requiredCount,
        )
        return EncodedRecurrenceRule(
            typeCode = rule.type.code,
            paramsVersion = CURRENT_REPEAT_PARAMS_VERSION,
            paramsJson = json.encodeToString(payload),
        )
    }

    fun decode(typeCode: String, paramsVersion: Int, paramsJson: String): RecurrenceRule {
        require(paramsVersion == CURRENT_REPEAT_PARAMS_VERSION)
        val payload = json.decodeFromString<RepeatParamsPayload>(paramsJson)
        return RecurrenceRule(
            type = RecurrenceType.fromStoredValue(typeCode),
            selectedWeekdays = payload.selectedWeekdays.map(DayOfWeek::of).toSet(),
            monthlyDay = payload.monthlyDay,
            intervalDays = payload.intervalDays,
            requiredCount = payload.requiredCount,
        )
    }
}

@Serializable
private data class RepeatParamsPayload(
    val selectedWeekdays: List<Int> = emptyList(),
    val monthlyDay: Int? = null,
    val intervalDays: Int? = null,
    val requiredCount: Int? = null,
)
