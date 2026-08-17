package com.mochisofts.mata.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.intSetOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.mochisofts.mata.widget.TodayTodoWidgetReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetPreviewPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun publishIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        publishOnAndroid15()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun publishOnAndroid15() {
        val category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        val component = ComponentName(context, TodayTodoWidgetReceiver::class.java)
        val provider = context.getSystemService(AppWidgetManager::class.java)
            .installedProviders
            .firstOrNull { it.provider == component }
            ?: return
        if (provider.generatedPreviewCategories and category != 0) return
        GlanceAppWidgetManager(context).setWidgetPreviews(
            TodayTodoWidgetReceiver::class,
            intSetOf(category),
        )
    }
}
