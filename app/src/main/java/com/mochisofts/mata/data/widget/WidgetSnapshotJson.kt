package com.mochisofts.mata.data.widget

import com.mochisofts.mata.domain.model.WidgetDisplayModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object WidgetSnapshotJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(model: WidgetDisplayModel): String = json.encodeToString(model)

    fun decode(value: String): WidgetDisplayModel = json.decodeFromString(value)
}
