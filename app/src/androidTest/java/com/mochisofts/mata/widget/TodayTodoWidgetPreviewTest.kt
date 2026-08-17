package com.mochisofts.mata.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import androidx.glance.appwidget.composeForPreview
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayTodoWidgetPreviewTest {
    @Test
    fun generatedPreviewComposesForHomeScreen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val remoteViews = TodayTodoWidget().composeForPreview(
            context = context,
            widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
        )

        assertNotNull(remoteViews)
    }
}
