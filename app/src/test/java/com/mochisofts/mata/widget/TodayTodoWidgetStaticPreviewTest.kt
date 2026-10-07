package com.mochisofts.mata.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTodoWidgetStaticPreviewTest {
    @Test
    fun widgetUsesStaticPreviewAndClearsPreviouslyGeneratedPreview() {
        val provider = source("src/main/res/xml/today_todo_widget_info.xml")
        val preview = source("src/main/res/layout/widget_preview.xml")
        val controller = source(
            "src/main/java/com/mochisofts/mata/data/widget/WidgetStaticPreviewController.kt",
        )
        val application = source("src/main/java/com/mochisofts/mata/app/MataApplication.kt")
        val widget = source(
            "src/main/java/com/mochisofts/mata/widget/TodayTodoWidget.kt",
        )

        assertTrue(provider.contains("android:previewLayout=\"@layout/widget_preview\""))
        assertTrue(preview.contains("@drawable/widget_card_background"))
        assertTrue(preview.contains("@string/widget_preview_todo_one"))
        assertTrue(preview.contains("@string/widget_preview_todo_two"))
        assertFalse(
            "RemoteViews cannot inflate a bare View in the static preview",
            preview.contains("<View"),
        )
        assertTrue(controller.contains("removeWidgetPreview(component, category)"))
        assertFalse(controller.contains("setWidgetPreviews"))
        assertTrue(application.contains("widgetStaticPreviewController.clearGeneratedPreviewIfNeeded()"))
        assertFalse(widget.contains("override suspend fun providePreview"))
    }

    private fun source(relativePath: String): String {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
        )
        val located = candidates.firstOrNull(File::isFile)
        assertNotNull("Could not locate $relativePath from $workingDirectory", located)
        return requireNotNull(located).readText()
    }
}
