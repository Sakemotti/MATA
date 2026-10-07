package com.mochisofts.mata.data.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.mochisofts.mata.widget.TodayTodoWidgetReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the resource-based widget preview authoritative on every supported Android version. */
@Singleton
class WidgetStaticPreviewController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun clearGeneratedPreviewIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        clearOnAndroid15()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun clearOnAndroid15() {
        val category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        val component = ComponentName(context, TodayTodoWidgetReceiver::class.java)
        context.getSystemService(AppWidgetManager::class.java)
            .removeWidgetPreview(component, category)
    }
}
